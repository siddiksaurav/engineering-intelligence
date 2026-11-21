package com.dsinnovators.ei.source.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.source.RawRecord;
import com.dsinnovators.ei.source.RawRecordStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class StoredReviewCommentsTest extends DatabaseTest {

    @Autowired
    private RawRecordStore store;

    @Autowired
    private StoredReviewComments storedReviewComments;

    private void storeComment(long databaseId, String body, String replyTo) {
        store.store(
                PullRequestRecorder.DATA_SOURCE,
                GitHubRecordKind.REVIEW_COMMENT.recordKind(),
                String.valueOf(databaseId),
                "https://github.com/opf/openproject/pull/7#discussion_r" + databaseId,
                CanonicalJson.parse("{\"databaseId\":" + databaseId
                        + ",\"url\":\"https://github.com/opf/openproject/pull/7#discussion_r"
                        + databaseId
                        + "\",\"author\":{\"login\":\"reviewer\",\"__typename\":\"User\",\"databaseId\":55},"
                        + "\"body\":\"" + body
                        + "\",\"path\":\"app/model.rb\",\"diffHunk\":\"@@ -1 +1 @@\\n+x\",\"replyTo\":" + replyTo
                        + ",\"createdAt\":\"2026-07-03T09:30:00Z\","
                        + "\"pullRequest\":{\"number\":7,\"repository\":{\"nameWithOwner\":\"opf/openproject\"}}}"));
    }

    @Test
    void allGivesTheLatestVersionOfEachComment() {
        storeComment(101, "first version", "null");
        storeComment(101, "second version", "null");
        storeComment(102, "only version", "{\"databaseId\":101}");

        List<RawRecord> all = storedReviewComments.all();

        assertEquals(
                List.of("second version", "only version"),
                all.stream()
                        .map(record -> record.getPayload().get("body").asString())
                        .toList());
    }

    @Test
    void aCommentIsReadAsItsFacts() {
        storeComment(102, "a reply", "{\"databaseId\":101}");
        storeComment(103, "a first comment", "null");

        List<ReviewCommentFacts> facts =
                storedReviewComments.all().stream().map(ReviewCommentFacts::of).toList();

        ReviewCommentFacts reply = facts.get(0);
        assertEquals(102, reply.databaseId());
        assertEquals("https://github.com/opf/openproject/pull/7#discussion_r102", reply.url());
        assertEquals("app/model.rb", reply.path());
        assertEquals(Instant.parse("2026-07-03T09:30:00Z"), reply.createdAt());
        assertTrue(reply.isReply());
        assertEquals(7, reply.pullRequestNumber());
        assertEquals("opf/openproject#7", reply.pullRequestExternalId());
        assertEquals(Optional.of(new GitHubAuthor("reviewer", "User", 55)), reply.author());
        assertEquals(false, facts.get(1).isReply());
    }

    @Test
    void aCommentIsReadAsItsText() {
        storeComment(102, "a reply", "{\"databaseId\":101}");
        storeComment(103, "a first comment", "null");

        List<ReviewCommentText> texts = storedReviewComments.texts();

        assertEquals(
                List.of(
                        new ReviewCommentText(
                                texts.get(0).rawRecordId(),
                                102,
                                "https://github.com/opf/openproject/pull/7#discussion_r102",
                                "app/model.rb",
                                Instant.parse("2026-07-03T09:30:00Z"),
                                "a reply",
                                "@@ -1 +1 @@\n+x",
                                OptionalLong.of(101)),
                        new ReviewCommentText(
                                texts.get(1).rawRecordId(),
                                103,
                                "https://github.com/opf/openproject/pull/7#discussion_r103",
                                "app/model.rb",
                                Instant.parse("2026-07-03T09:30:00Z"),
                                "a first comment",
                                "@@ -1 +1 @@\n+x",
                                OptionalLong.empty())),
                texts);
    }

    @Test
    void aCommentWithoutBodyOrHunkIsReadWithEmptyTexts() {
        store.store(
                PullRequestRecorder.DATA_SOURCE,
                GitHubRecordKind.REVIEW_COMMENT.recordKind(),
                "104",
                "https://github.com/opf/openproject/pull/7#discussion_r104",
                CanonicalJson.parse("{\"databaseId\":104,\"url\":\"u\",\"path\":\"a.rb\","
                        + "\"createdAt\":\"2026-07-03T09:30:00Z\",\"replyTo\":null}"));

        ReviewCommentText text = storedReviewComments.texts().getFirst();

        assertEquals("", text.body());
        assertEquals("", text.diffHunk());
    }
}
