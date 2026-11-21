package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.job.JobCounts;
import com.dsinnovators.ei.source.github.ReviewCommentText;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

class ReviewCommentJudgeTest extends DatabaseTest {

    private static final Quarter QUARTER = Quarter.parse("2026-Q3");
    private static final ReviewCommentChunks.PullRequestPart PULL_REQUEST =
            new ReviewCommentChunks.PullRequestPart("opf/openproject#7", "Add the series", "Adds a series.");

    @Autowired
    private PromptLibrary promptLibrary;

    @Autowired
    private JudgmentStore judgmentStore;

    @Autowired
    private ReviewCommentTypes commentTypes;

    @Autowired
    private JudgmentProperties properties;

    private final Map<String, Long> counts = new HashMap<>();
    private final JobCounts jobCounts = (name, amount) -> counts.merge(name, amount, Long::sum);
    private final FakeJudgmentModel model = new FakeJudgmentModel();

    private ReviewCommentJudge judge() {
        return new ReviewCommentJudge(promptLibrary, model, judgmentStore, commentTypes);
    }

    private ReviewCommentChunks.CommentPart comment(long databaseId, String body) {
        long itemRowId = JudgmentSubjectRows.insertEvidenceItem(jdbc);
        return new ReviewCommentChunks.CommentPart(
                new ReviewCommentToJudge(
                        itemRowId,
                        "review_comment_written:" + databaseId,
                        databaseId + 2_000,
                        3_000,
                        "opf/openproject#7"),
                new ReviewCommentText(
                        databaseId + 2_000,
                        databaseId,
                        "https://github.com/opf/openproject/pull/7#discussion_r" + databaseId,
                        "app/model.rb",
                        Instant.parse("2026-07-03T09:00:00Z").plusSeconds(databaseId),
                        body,
                        "@@ -1 +1 @@\n+code",
                        OptionalLong.empty()),
                Optional.empty());
    }

    private ReviewCommentChunks.Chunk chunkOf(ReviewCommentChunks.CommentPart... comments) {
        return ReviewCommentChunks.of(
                        PULL_REQUEST,
                        List.of(comments),
                        properties.reviewCommentLimits(),
                        ReviewCommentJudge.PROMPT_VERSION)
                .getFirst();
    }

    /** An answer that gives every comment a type; each pair is a comment id and a type. */
    private static ObjectNode answerOf(String... idsAndTypes) {
        ArrayNode comments = JsonNodeFactory.instance.arrayNode();
        for (int index = 0; index < idsAndTypes.length; index += 2) {
            comments.addObject()
                    .put("commentId", idsAndTypes[index])
                    .put("commentType", idsAndTypes[index + 1])
                    .put("reason", "Reason for " + idsAndTypes[index]);
        }
        return JsonNodeFactory.instance.objectNode().set("comments", comments).deepCopy();
    }

    private Judgment currentJudgmentOf(ReviewCommentChunks.ChunkComment comment) {
        return judgmentStore
                .findCurrent(
                        JudgmentKind.REVIEW_COMMENT,
                        JudgmentSubject.evidenceItem(comment.item().itemRowId()))
                .orElseThrow();
    }

    @Test
    void aStyleCommentGetsNoTier() {
        ReviewCommentChunks.Chunk chunk = chunkOf(comment(1, "Please add a space here."));
        model.answeringByDefault(answerOf("C-1", "style"));

        judge().judge(chunk, QUARTER, jobCounts);

        Judgment judgment = currentJudgmentOf(chunk.comments().getFirst());
        assertNull(judgment.getTier());
        assertEquals("style", judgment.getValue().path("commentType").asString());
        assertEquals(false, judgment.getValue().path("substantive").asBoolean());
    }

    @ParameterizedTest
    @CsvSource({"question,T1", "correctness,T2", "security,T3", "architecture,T3"})
    void aSubstantiveCommentGetsTheTierOfItsType(String type, String tier) {
        ReviewCommentChunks.Chunk chunk = chunkOf(comment(1, "A real remark."));
        model.answeringByDefault(answerOf("C-1", type));

        judge().judge(chunk, QUARTER, jobCounts);

        Judgment judgment = currentJudgmentOf(chunk.comments().getFirst());
        assertEquals(tier, judgment.getTier());
        assertEquals(true, judgment.getValue().path("substantive").asBoolean());
    }

    @Test
    void storesTheValueReasonFactModelPromptVersionAndHashOfEveryComment() {
        ReviewCommentChunks.Chunk chunk = chunkOf(comment(1, "Why a second query?"), comment(2, "Rename this."));
        model.answeringByDefault(answerOf("C-1", "question", "C-2", "style"));

        judge().judge(chunk, QUARTER, jobCounts);

        Judgment first = currentJudgmentOf(chunk.comments().get(0));
        assertEquals("Reason for C-1", first.getReason());
        assertEquals(
                List.of(new JudgmentFact(
                        "Comment type question: \"Why a second query?\"",
                        "https://github.com/opf/openproject/pull/7#discussion_r1")),
                first.getFacts());
        assertEquals(FakeJudgmentModel.MODEL, first.getModel());
        assertEquals("review-comments/v1", first.getPromptVersion());
        assertEquals(JudgedBy.MODEL, first.getJudgedBy());
        assertEquals(chunk.comments().get(0).hash(), first.getInputHash());
        assertNull(first.getConfidence());
        assertEquals(
                chunk.comments().get(1).hash(),
                currentJudgmentOf(chunk.comments().get(1)).getInputHash());
    }

    @Test
    void quotesOnlyTheFirstTwoHundredCharactersOfTheComment() {
        ReviewCommentChunks.Chunk chunk = chunkOf(comment(1, "x".repeat(300)));
        model.answeringByDefault(answerOf("C-1", "design"));

        judge().judge(chunk, QUARTER, jobCounts);

        assertEquals(
                "Comment type design: \"" + "x".repeat(200) + "\"",
                currentJudgmentOf(chunk.comments().getFirst())
                        .getFacts()
                        .getFirst()
                        .fact());
    }

    @Test
    void sendsOneRequestForTheChunkWithItsPromptSchemaAndText() {
        ReviewCommentChunks.Chunk chunk = chunkOf(comment(1, "One."), comment(2, "Two."));
        model.answeringByDefault(answerOf("C-1", "style", "C-2", "style"));

        judge().judge(chunk, QUARTER, jobCounts);

        assertEquals(1, model.calls());
        ModelRequest request = model.requests().getFirst();
        assertEquals("review-comments/v1", request.promptVersion());
        assertEquals(promptLibrary.instructions("review-comments/v1"), request.instructions());
        assertEquals(promptLibrary.schema("review-comments/v1"), request.jsonSchema());
        assertEquals(chunk.text(), request.input());
    }

    @Test
    void countsTheJudgmentsWrittenAndTheModelUse() {
        ReviewCommentChunks.Chunk chunk = chunkOf(comment(1, "One."), comment(2, "Two."));
        model.answeringByDefault(answerOf("C-1", "style", "C-2", "style"));

        judge().judge(chunk, QUARTER, jobCounts);

        assertEquals(2L, counts.get("judgments_written"));
        assertEquals(1L, counts.get("model_calls"));
        assertEquals(0L, counts.get("skipped_same_input"));
    }

    @Test
    void makesNoCallWhenEveryCommentOfTheChunkWasJudged() {
        ReviewCommentChunks.Chunk chunk = chunkOf(comment(1, "One."), comment(2, "Two."));
        model.answeringByDefault(answerOf("C-1", "style", "C-2", "style"));
        judge().judge(chunk, QUARTER, jobCounts);

        counts.clear();
        judge().judge(chunk, QUARTER, jobCounts);

        assertEquals(1, model.calls());
        assertEquals(2L, counts.get("skipped_same_input"));
        assertNull(counts.get("model_calls"));
        assertNull(counts.get("judgments_written"));
    }

    @Test
    void writesOnlyTheCommentsNotJudgedYetWhenSomeOfTheChunkWereJudged() {
        ReviewCommentChunks.CommentPart first = comment(1, "One.");
        ReviewCommentChunks.CommentPart second = comment(2, "Two.");
        ReviewCommentChunks.Chunk chunk = chunkOf(first, second);
        model.answeringByDefault(answerOf("C-1", "style", "C-2", "style"));
        judge().judge(chunk, QUARTER, jobCounts);
        ReviewCommentChunks.CommentPart secondOnANewItemRow = new ReviewCommentChunks.CommentPart(
                new ReviewCommentToJudge(
                        JudgmentSubjectRows.insertEvidenceItem(jdbc),
                        second.item().key(),
                        second.item().commentRecordId(),
                        second.item().pullRequestRecordId(),
                        second.item().pullRequestExternalId()),
                second.text(),
                Optional.empty());
        ReviewCommentChunks.Chunk newChunk = ReviewCommentChunks.of(
                        PULL_REQUEST,
                        List.of(first, secondOnANewItemRow),
                        properties.reviewCommentLimits(),
                        ReviewCommentJudge.PROMPT_VERSION)
                .getFirst();
        counts.clear();

        judge().judge(newChunk, QUARTER, jobCounts);

        assertEquals(1L, counts.get("skipped_same_input"));
        assertEquals(1L, counts.get("judgments_written"));
        assertEquals(
                newChunk.comments().get(1).hash(),
                currentJudgmentOf(newChunk.comments().get(1)).getInputHash());
    }

    @Test
    void asksOnceMoreWhenTheAnswerLeavesOutACommentAndFailsAfterTheSecondRefusal() {
        ReviewCommentChunks.Chunk chunk = chunkOf(comment(1, "One."), comment(2, "Two."));
        model.thenAnswers(answerOf("C-1", "style")).thenAnswers(answerOf("C-1", "style", "C-2", "question"));

        judge().judge(chunk, QUARTER, jobCounts);

        assertEquals(2, model.calls());
        assertEquals(2L, counts.get("judgments_written"));

        ReviewCommentChunks.Chunk other = chunkOf(comment(3, "Three."));
        model.thenAnswers(answerOf("C-9", "style")).thenAnswers(answerOf("C-9", "style"));
        assertThrows(InvalidAnswerException.class, () -> judge().judge(other, QUARTER, jobCounts));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "select count(*) from judgment where input_hash = ?",
                        Integer.class,
                        other.comments().getFirst().hash()));
    }

    @Test
    void failsNamingTheTypeWhenTheSettingHasNoEntryForIt() {
        ReviewCommentChunks.Chunk chunk = chunkOf(comment(1, "One."));
        model.answeringByDefault(answerOf("C-1", "security"));
        jdbc.update(
                "update setting set value = '{\"style\": {\"substantive\": false, \"tier\": null}}'::jsonb where key = 'evidence.review_comment_types'");

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> judge().judge(chunk, QUARTER, jobCounts));

        assertTrue(failure.getMessage().contains("security"));
        assertEquals(0, jdbc.queryForObject("select count(*) from judgment", Integer.class));
    }

    @Test
    void writesNoJudgmentOfTheChunkWhenOneOfItsTypesCannotBeRead() {
        ReviewCommentChunks.Chunk chunk = chunkOf(comment(1, "One."), comment(2, "Two."));
        model.answeringByDefault(answerOf("C-1", "style", "C-2", "security"));
        jdbc.update(
                "update setting set value = '{\"style\": {\"substantive\": false, \"tier\": null}}'::jsonb where key = 'evidence.review_comment_types'");

        assertThrows(IllegalStateException.class, () -> judge().judge(chunk, QUARTER, jobCounts));

        assertEquals(0, jdbc.queryForObject("select count(*) from judgment", Integer.class));
    }
}
