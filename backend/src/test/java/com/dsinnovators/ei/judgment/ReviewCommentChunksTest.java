package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.source.github.ReviewCommentText;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class ReviewCommentChunksTest {

    private static final String PROMPT_VERSION = "review-comments/v1";
    private static final ReviewCommentLimits ROOMY = new ReviewCommentLimits(100, 100, 100, 50, 2_000);
    private static final ReviewCommentChunks.PullRequestPart PULL_REQUEST =
            new ReviewCommentChunks.PullRequestPart("opf/openproject#7", "Add the series", "Adds a series.");

    private static ReviewCommentChunks.CommentPart comment(long databaseId, String createdAt, String body) {
        return comment(databaseId, createdAt, body, "@@ -1 +1 @@\n+code", OptionalLong.empty(), Optional.empty());
    }

    private static ReviewCommentChunks.CommentPart comment(
            long databaseId,
            String createdAt,
            String body,
            String diffHunk,
            OptionalLong repliedTo,
            Optional<String> repliedBody) {
        return new ReviewCommentChunks.CommentPart(
                new ReviewCommentToJudge(
                        databaseId + 1_000,
                        "review_comment_written:" + databaseId,
                        databaseId + 2_000,
                        3_000,
                        "opf/openproject#7"),
                new ReviewCommentText(
                        databaseId + 2_000,
                        databaseId,
                        "https://github.com/opf/openproject/pull/7#discussion_r" + databaseId,
                        "app/model.rb",
                        Instant.parse(createdAt),
                        body,
                        diffHunk,
                        repliedTo),
                repliedBody);
    }

    private static List<ReviewCommentChunks.Chunk> chunks(
            ReviewCommentLimits limits, ReviewCommentChunks.CommentPart... comments) {
        return ReviewCommentChunks.of(PULL_REQUEST, List.of(comments), limits, PROMPT_VERSION);
    }

    @Test
    void sendsTheTailOfALongDiffHunk() {
        String hunk = "first line\n" + "x".repeat(150) + "\nlast line";

        ReviewCommentChunks.Chunk chunk = chunks(
                        ROOMY, comment(1, "2026-07-03T09:00:00Z", "why?", hunk, OptionalLong.empty(), Optional.empty()))
                .getFirst();

        assertTrue(chunk.text().contains("last line"));
        assertFalse(chunk.text().contains("first line"));
        assertTrue(chunk.text().contains("[diff hunk cut: only the last 100 characters are shown]"));
    }

    @Test
    void tagsEachCommentWithItsDatabaseIdAndKeepsCreationOrder() {
        ReviewCommentChunks.Chunk chunk = chunks(
                        ROOMY,
                        comment(5, "2026-07-03T09:00:00Z", "later"),
                        comment(9, "2026-07-03T08:00:00Z", "earlier"),
                        comment(4, "2026-07-03T09:00:00Z", "same time, lower id"))
                .getFirst();

        assertEquals(List.of("C-9", "C-4", "C-5"), chunk.commentIds());
        assertTrue(chunk.text().indexOf("[C-9]") < chunk.text().indexOf("[C-4]"));
        assertTrue(chunk.text().indexOf("[C-4]") < chunk.text().indexOf("[C-5]"));
    }

    @Test
    void startsWithThePullRequestTitleAndItsCutBody() {
        ReviewCommentChunks.PullRequestPart longBody =
                new ReviewCommentChunks.PullRequestPart("opf/openproject#7", "Add the series", "b".repeat(150));

        ReviewCommentChunks.Chunk chunk = ReviewCommentChunks.of(
                        longBody, List.of(comment(1, "2026-07-03T09:00:00Z", "why?")), ROOMY, PROMPT_VERSION)
                .getFirst();

        assertTrue(chunk.text().startsWith("Pull request: Add the series\n"));
        assertTrue(chunk.text().contains("[description cut at 100 characters]"));
    }

    @Test
    void cutsALongCommentAndNamesTheCut() {
        ReviewCommentChunks.Chunk chunk = chunks(ROOMY, comment(1, "2026-07-03T09:00:00Z", "c".repeat(150)))
                .getFirst();

        assertTrue(chunk.text().contains("[comment cut at 100 characters]"));
    }

    @Test
    void aReplyCarriesTheCutTextOfTheCommentItAnswers() {
        ReviewCommentChunks.Chunk chunk = chunks(
                        ROOMY,
                        comment(
                                2,
                                "2026-07-03T09:00:00Z",
                                "Done",
                                "@@ -1 +1 @@",
                                OptionalLong.of(1),
                                Optional.of("r".repeat(80))))
                .getFirst();

        assertTrue(chunk.text().contains("This comment replies to:\n" + "r".repeat(50)));
        assertTrue(chunk.text().contains("[replied comment cut at 50 characters]"));
    }

    @Test
    void aReplyWhoseOriginalIsNotStoredHasNoRepliedText() {
        ReviewCommentChunks.Chunk chunk = chunks(
                        ROOMY,
                        comment(2, "2026-07-03T09:00:00Z", "Done", "@@ -1 +1 @@", OptionalLong.of(1), Optional.empty()))
                .getFirst();

        assertFalse(chunk.text().contains("replies to"));
    }

    @Test
    void splitsThePullRequestIntoChunksThatFitTheBudgetEachWithItsOwnComments() {
        ReviewCommentLimits tight = new ReviewCommentLimits(20, 20, 100, 20, 400);
        ReviewCommentChunks.PullRequestPart pullRequest =
                new ReviewCommentChunks.PullRequestPart("opf/openproject#7", "Title", "Body");

        List<ReviewCommentChunks.Chunk> chunks = ReviewCommentChunks.of(
                pullRequest,
                List.of(
                        comment(1, "2026-07-03T09:00:00Z", "a".repeat(100)),
                        comment(2, "2026-07-03T09:01:00Z", "b".repeat(100)),
                        comment(3, "2026-07-03T09:02:00Z", "c".repeat(100))),
                tight,
                PROMPT_VERSION);

        assertEquals(List.of(List.of("C-1"), List.of("C-2"), List.of("C-3")), idsOf(chunks));
        chunks.forEach(chunk -> assertTrue(chunk.text().length() <= 400));
        chunks.forEach(chunk -> assertTrue(chunk.text().startsWith("Pull request: Title\n")));
    }

    @Test
    void keepsSeveralCommentsInOneChunkWhileTheyFit() {
        List<ReviewCommentChunks.Chunk> chunks = chunks(
                ROOMY,
                comment(1, "2026-07-03T09:00:00Z", "one"),
                comment(2, "2026-07-03T09:01:00Z", "two"),
                comment(3, "2026-07-03T09:02:00Z", "three"));

        assertEquals(List.of(List.of("C-1", "C-2", "C-3")), idsOf(chunks));
    }

    @Test
    void everyCommentOfAChunkHasItsOwnHash() {
        ReviewCommentChunks.Chunk chunk = chunks(
                        ROOMY, comment(1, "2026-07-03T09:00:00Z", "one"), comment(2, "2026-07-03T09:01:00Z", "two"))
                .getFirst();

        assertNotEquals(chunk.comments().get(0).hash(), chunk.comments().get(1).hash());
        assertEquals(64, chunk.comments().get(0).hash().length());
    }

    @Test
    void theHashFollowsTheTextThePromptVersionAndTheSourceRecords() {
        ReviewCommentChunks.CommentPart original = comment(1, "2026-07-03T09:00:00Z", "one");
        String hash = hashOfTheOnlyComment(PROMPT_VERSION, original);

        assertEquals(hash, hashOfTheOnlyComment(PROMPT_VERSION, original));
        assertNotEquals(hash, hashOfTheOnlyComment("review-comments/v2", original));
        assertNotEquals(hash, hashOfTheOnlyComment(PROMPT_VERSION, comment(1, "2026-07-03T09:00:00Z", "one, edited")));
        ReviewCommentChunks.CommentPart newItemRow = new ReviewCommentChunks.CommentPart(
                new ReviewCommentToJudge(9_999, "review_comment_written:1", 2_001, 3_000, "opf/openproject#7"),
                original.text(),
                Optional.empty());
        assertNotEquals(hash, hashOfTheOnlyComment(PROMPT_VERSION, newItemRow));
    }

    @Test
    void aChunkNamesItsPullRequestAndItsFirstCommentAndKeepsTheFactSources() {
        ReviewCommentChunks.Chunk chunk =
                chunks(ROOMY, comment(7, "2026-07-03T09:00:00Z", "why?")).getFirst();

        assertEquals("opf/openproject#7 (review_comment_written:7)", chunk.describe());
        ReviewCommentChunks.ChunkComment only = chunk.comments().getFirst();
        assertEquals("C-7", only.commentId());
        assertEquals("https://github.com/opf/openproject/pull/7#discussion_r7", only.url());
        assertEquals("why?", only.body());
    }

    private static String hashOfTheOnlyComment(String promptVersion, ReviewCommentChunks.CommentPart comment) {
        return ReviewCommentChunks.of(PULL_REQUEST, List.of(comment), ROOMY, promptVersion)
                .getFirst()
                .comments()
                .getFirst()
                .hash();
    }

    private static List<List<String>> idsOf(List<ReviewCommentChunks.Chunk> chunks) {
        return chunks.stream().map(ReviewCommentChunks.Chunk::commentIds).toList();
    }
}
