package com.dsinnovators.ei.judgment;

import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.evidence.BuiltItem;
import com.dsinnovators.ei.evidence.EvidenceItemSource;
import com.dsinnovators.ei.evidence.EvidenceItemStore;
import com.dsinnovators.ei.evidence.EvidenceType;
import com.dsinnovators.ei.evidence.SourceRole;
import com.dsinnovators.ei.source.RawRecordStore;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Stores what a review comment judgment reads, in the layouts F02 and F05 store: pull requests, inline
 * review comments and the Review comment written item rows built from them.
 */
final class StoredReviewCommentFixture {

    static final String REPOSITORY = "opf/openproject";

    private final JdbcTemplate jdbc;
    private final RawRecordStore rawRecords;
    private final EvidenceItemStore items;
    private final long reviewer;

    StoredReviewCommentFixture(JdbcTemplate jdbc, RawRecordStore rawRecords, EvidenceItemStore items) {
        this.jdbc = jdbc;
        this.rawRecords = rawRecords;
        this.items = items;
        this.reviewer =
                jdbc.queryForObject("insert into person (display_name) values ('Grace') returning id", Long.class);
    }

    void pullRequest(int number, String title) {
        String url = "https://github.com/" + REPOSITORY + "/pull/" + number;
        rawRecords.store(
                "github",
                "pull_request",
                REPOSITORY + "#" + number,
                url,
                CanonicalJson.parse("{\"number\":" + number + ",\"title\":\"" + title + "\",\"body\":\"Body " + number
                        + "\",\"url\":\"" + url + "\",\"mergedAt\":\"2026-07-02T08:00:00Z\","
                        + "\"author\":{\"login\":\"ada\",\"__typename\":\"User\",\"databaseId\":1}}"));
    }

    /** Stores the comment record only: a comment of the pull request's author or a bot has no item. */
    void commentRecord(long databaseId, int pullRequestNumber, String createdAt, String body, Long repliedTo) {
        String url = commentUrl(pullRequestNumber, databaseId);
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("databaseId", databaseId);
        payload.put("url", url);
        payload.put("body", body);
        payload.put("path", "app/models/work_package.rb");
        payload.put("diffHunk", "@@ -1 +1 @@\n+code of " + databaseId);
        payload.put("createdAt", createdAt);
        if (repliedTo == null) {
            payload.putNull("replyTo");
        } else {
            payload.putObject("replyTo").put("databaseId", repliedTo);
        }
        payload.putObject("pullRequest")
                .put("number", pullRequestNumber)
                .putObject("repository")
                .put("nameWithOwner", REPOSITORY);
        rawRecords.store(
                "github",
                "review_comment",
                REPOSITORY + "#" + pullRequestNumber + "/review_comment/" + databaseId,
                url,
                payload);
    }

    /** Stores the comment and its Review comment written item row. */
    void comment(long databaseId, int pullRequestNumber, String createdAt, String body) {
        commentRecord(databaseId, pullRequestNumber, createdAt, body, null);
        item(databaseId, pullRequestNumber);
    }

    /** Stores the comment as a reply to another comment, and its item row. */
    void reply(long databaseId, int pullRequestNumber, String createdAt, String body, long repliedTo) {
        commentRecord(databaseId, pullRequestNumber, createdAt, body, repliedTo);
        item(databaseId, pullRequestNumber);
    }

    /**
     * Stores the comment on a stored pull request and an item row that names another, unstored pull
     * request, as a lost record would leave it.
     */
    void commentOfAnUnstoredPullRequest(
            long databaseId, int unstoredNumber, int storedNumber, String createdAt, String body) {
        commentRecord(databaseId, storedNumber, createdAt, body, null);
        item(databaseId, storedNumber, REPOSITORY + "#" + unstoredNumber);
    }

    private void item(long databaseId, int pullRequestNumber) {
        item(databaseId, pullRequestNumber, REPOSITORY + "#" + pullRequestNumber);
    }

    private void item(long databaseId, int pullRequestNumber, String namedPullRequestExternalId) {
        String pullRequestExternalId = REPOSITORY + "#" + pullRequestNumber;
        items.write(new BuiltItem(
                "review_comment_written:" + databaseId,
                EvidenceType.REVIEW_COMMENT_WRITTEN,
                reviewer,
                null,
                LocalDate.parse("2026-07-02"),
                commentUrl(pullRequestNumber, databaseId),
                CanonicalJson.parse("{\"pullRequest\":{\"externalId\":\"" + namedPullRequestExternalId
                        + "\"},\"comment\":{\"databaseId\":" + databaseId + "}}"),
                List.of(
                        new EvidenceItemSource(
                                rawRecordId("review_comment", pullRequestExternalId + "/review_comment/" + databaseId),
                                SourceRole.REVIEW_COMMENT),
                        new EvidenceItemSource(
                                rawRecordId("pull_request", pullRequestExternalId), SourceRole.PULL_REQUEST))));
    }

    private static String commentUrl(int pullRequestNumber, long databaseId) {
        return "https://github.com/" + REPOSITORY + "/pull/" + pullRequestNumber + "#discussion_r" + databaseId;
    }

    private long rawRecordId(String recordKind, String externalId) {
        return jdbc.queryForObject(
                "select max(id) from raw_record where record_kind = ? and external_id = ?",
                Long.class,
                recordKind,
                externalId);
    }
}
