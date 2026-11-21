package com.dsinnovators.ei.judgment;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.job.JobCounts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Stage 2 for one chunk of the review comments of a pull request: asks the model unless every comment
 * of the chunk was judged on this input, checks that the answer names every comment once, reads
 * whether a comment is substantive and its tier from the comment type table, and stores the judgments
 * of the chunk in one transaction (F07 design, "Main flows" 2).
 */
@Component
class ReviewCommentJudge {

    static final String PROMPT_VERSION = "review-comments/v1";

    private static final String SCHEMA_NAME = "review_comments";

    private final PromptLibrary promptLibrary;
    private final JudgmentModel model;
    private final JudgmentStore judgmentStore;
    private final ReviewCommentTypes commentTypes;

    ReviewCommentJudge(
            PromptLibrary promptLibrary,
            JudgmentModel model,
            JudgmentStore judgmentStore,
            ReviewCommentTypes commentTypes) {
        this.promptLibrary = promptLibrary;
        this.model = model;
        this.judgmentStore = judgmentStore;
        this.commentTypes = commentTypes;
    }

    /**
     * Writes the judgment of each comment of the chunk that was not judged on this input before. A
     * comment judged before is counted in {@code skipped_same_input}; when all of them were, there is no call.
     *
     * @param quarter the quarter whose settings apply
     * @throws IllegalStateException when the setting {@code evidence.review_comment_types} has no entry
     *     or no tier for a type the model gave
     * @throws InvalidAnswerException when the answer breaks the schema or leaves out a comment twice
     * @throws UnreadableAnswerException when the answer cannot be read twice
     * @throws ModelLimitReachedException when every provider is at its limit
     */
    void judge(ReviewCommentChunks.Chunk chunk, Quarter quarter, JobCounts counts) {
        List<ReviewCommentChunks.ChunkComment> toJudge = chunk.comments().stream()
                .filter(comment -> !judgmentStore.hasModelJudgment(JudgmentKind.REVIEW_COMMENT, comment.hash()))
                .toList();
        counts.add("skipped_same_input", chunk.comments().size() - toJudge.size());
        if (toJudge.isEmpty()) {
            return;
        }
        CheckedAnswers.Answered<ReviewCommentAnswer> answered = CheckedAnswers.ask(
                model,
                new ModelRequest(
                        PROMPT_VERSION,
                        promptLibrary.instructions(PROMPT_VERSION),
                        chunk.text(),
                        SCHEMA_NAME,
                        promptLibrary.schema(PROMPT_VERSION)),
                answer -> ReviewCommentAnswer.fromAnswer(answer, chunk.commentIds()),
                counts);
        Map<String, ReviewCommentAnswer.Comment> answers = answered.value().comments().stream()
                .collect(Collectors.toMap(ReviewCommentAnswer.Comment::commentId, Function.identity()));
        List<NewJudgment> judgments = new ArrayList<>();
        for (ReviewCommentChunks.ChunkComment comment : toJudge) {
            judgments.add(judgmentOf(comment, answers.get(comment.commentId()), answered.model(), quarter));
        }
        judgmentStore.writeAll(judgments);
        counts.add("judgments_written", judgments.size());
    }

    private NewJudgment judgmentOf(
            ReviewCommentChunks.ChunkComment comment,
            ReviewCommentAnswer.Comment answer,
            String modelId,
            Quarter quarter) {
        ReviewCommentTypes.Classification classification = commentTypes.classify(answer.commentType(), quarter);
        ObjectNode value = JsonNodeFactory.instance.objectNode();
        value.put("commentType", answer.commentType().wireName());
        value.put("substantive", classification.substantive());
        value.put("reason", answer.reason());
        return new NewJudgment(
                JudgmentKind.REVIEW_COMMENT,
                JudgmentSubject.evidenceItem(comment.item().itemRowId()),
                value,
                classification.tier().orElse(null),
                answer.reason(),
                List.of(new JudgmentFact(factOf(answer.commentType(), comment.body()), comment.url())),
                null,
                JudgedBy.MODEL,
                modelId,
                PROMPT_VERSION,
                comment.hash(),
                null,
                null);
    }

    // A quote the reader can take in at a glance; the whole comment is one click away at the link.
    private static String factOf(ReviewCommentAnswer.CommentType type, String body) {
        int quoteCharacters = 200;
        int end = body.offsetByCodePoints(0, Math.min(quoteCharacters, body.codePointCount(0, body.length())));
        return "Comment type " + type.wireName() + ": \"" + body.substring(0, end) + "\"";
    }
}
