package com.dsinnovators.ei.judgment;

import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.source.github.ReviewCommentText;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * What the model reads of the review comments of one pull request (F07 design, "Main flows" 2 and
 * decision 169): the pull request, then the comments in the order they were written, each tagged
 * {@code C-<databaseId>} with its file, the tail of its diff hunk, its text and, for a reply, the text
 * of the comment it answers. A pull request with more comments than one call holds is split into chunks.
 * Every comment has its own hash.
 */
final class ReviewCommentChunks {

    private static final String COMMENT_ID_PREFIX = "C-";

    private ReviewCommentChunks() {}

    /** The pull request of the comments. */
    record PullRequestPart(String externalId, String title, String body) {}

    /**
     * One comment to judge with the text it was written with.
     *
     * @param repliedCommentBody the text of the comment this one answers, whoever wrote it; empty for a
     *     first comment or when the answered comment is not stored
     */
    record CommentPart(ReviewCommentToJudge item, ReviewCommentText text, Optional<String> repliedCommentBody) {}

    /**
     * A comment of a chunk.
     *
     * @param commentId the key the model cites, such as {@code C-1234}
     * @param url the comment on GitHub, the link of the fact of its judgment
     * @param body the full text of the comment
     * @param hash SHA-256 over the kind, the prompt version, the text of the chunk and the item row and
     *     source records of this comment; the same hash is never judged twice (decision 157)
     */
    record ChunkComment(ReviewCommentToJudge item, String commentId, String url, String body, String hash) {}

    /** The input of one call: the text and its comments in the order of the text. */
    record Chunk(String pullRequestExternalId, String text, List<ChunkComment> comments) {

        Chunk {
            comments = List.copyOf(comments);
        }

        List<String> commentIds() {
            return comments.stream().map(ChunkComment::commentId).toList();
        }

        /** Names the chunk in a failure: its pull request and its first comment. */
        String describe() {
            return pullRequestExternalId + " (" + comments.getFirst().item().key() + ")";
        }
    }

    /**
     * Builds the chunks of one pull request.
     *
     * @param comments the comments of the pull request, in any order
     * @return at least one chunk; a comment that alone does not fit the budget gets a chunk of its own
     */
    static List<Chunk> of(
            PullRequestPart pullRequest, List<CommentPart> comments, ReviewCommentLimits limits, String promptVersion) {
        String header = header(pullRequest, limits);
        List<CommentPart> ordered = comments.stream()
                .sorted(Comparator.comparing(
                                (CommentPart comment) -> comment.text().createdAt())
                        .thenComparingLong(comment -> comment.text().databaseId()))
                .toList();
        List<Chunk> chunks = new ArrayList<>();
        List<CommentPart> current = new ArrayList<>();
        StringBuilder text = new StringBuilder(header);
        for (CommentPart comment : ordered) {
            String part = part(comment, limits);
            if (!current.isEmpty() && text.length() + part.length() > limits.maxReviewCharacters()) {
                chunks.add(chunk(pullRequest, current, text.toString(), promptVersion));
                current = new ArrayList<>();
                text = new StringBuilder(header);
            }
            current.add(comment);
            text.append(part);
        }
        chunks.add(chunk(pullRequest, current, text.toString(), promptVersion));
        return chunks;
    }

    private static String header(PullRequestPart pullRequest, ReviewCommentLimits limits) {
        return "Pull request: " + pullRequest.title() + "\nDescription:\n"
                + TextCuts.cut(pullRequest.body(), limits.maxDescriptionCharacters()) + "\n";
    }

    private static String part(CommentPart comment, ReviewCommentLimits limits) {
        ReviewCommentText text = comment.text();
        StringBuilder part = new StringBuilder("\n[")
                .append(commentId(text))
                .append("]\nFile: ")
                .append(text.path())
                .append('\n');
        if (!text.diffHunk().isBlank()) {
            part.append("Diff hunk (the commented line is the last):\n")
                    .append(TextCuts.tail(text.diffHunk(), limits.maxHunkCharacters(), "diff hunk"))
                    .append('\n');
        }
        part.append("Comment:\n")
                .append(TextCuts.cut(text.body(), limits.maxCommentCharacters(), "comment"))
                .append('\n');
        comment.repliedCommentBody()
                .ifPresent(replied -> part.append("This comment replies to:\n")
                        .append(TextCuts.cut(replied, limits.maxRepliedCommentCharacters(), "replied comment"))
                        .append('\n'));
        return part.toString();
    }

    private static Chunk chunk(
            PullRequestPart pullRequest, List<CommentPart> comments, String text, String promptVersion) {
        List<ChunkComment> chunkComments = comments.stream()
                .map(comment -> new ChunkComment(
                        comment.item(),
                        commentId(comment.text()),
                        comment.text().url(),
                        comment.text().body(),
                        hashOf(comment, text, promptVersion)))
                .toList();
        return new Chunk(pullRequest.externalId(), text, chunkComments);
    }

    private static String commentId(ReviewCommentText text) {
        return COMMENT_ID_PREFIX + text.databaseId();
    }

    private static String hashOf(CommentPart comment, String text, String promptVersion) {
        ObjectNode hashed = JsonNodeFactory.instance.objectNode();
        hashed.put("kind", JudgmentKind.REVIEW_COMMENT.databaseName());
        hashed.put("promptVersion", promptVersion);
        hashed.put("input", text);
        hashed.put("itemRowId", comment.item().itemRowId());
        hashed.put("commentRecordId", comment.item().commentRecordId());
        hashed.put("pullRequestRecordId", comment.item().pullRequestRecordId());
        return CanonicalJson.sha256Hex(hashed);
    }
}
