package com.dsinnovators.ei.source.github;

import com.dsinnovators.ei.source.RawRecordStore;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Stores a fetched pull request as raw records, one per part, in one transaction (F02 design 6.2):
 * a pull request is stored whole or not at all.
 */
@Component
class PullRequestRecorder {

    static final String DATA_SOURCE = "github";

    private final RawRecordStore store;

    PullRequestRecorder(RawRecordStore store) {
        this.store = store;
    }

    /**
     * Stores every part, in the order pull request, diff, reviews, review comments, conversation
     * comments, and says what each store did.
     *
     * @throws GitHubApiException when a review or comment has no id or URL; nothing is stored
     */
    @Transactional
    List<StoredRecord> store(FetchedPullRequest pullRequest) {
        List<PendingRecord> records = records(pullRequest);
        List<StoredRecord> stored = new ArrayList<>(records.size());
        for (PendingRecord record : records) {
            stored.add(new StoredRecord(
                    record.kind(),
                    store.store(
                                    DATA_SOURCE,
                                    record.kind().recordKind(),
                                    record.externalId(),
                                    record.url(),
                                    record.payload())
                            .outcome()));
        }
        return stored;
    }

    private static List<PendingRecord> records(FetchedPullRequest pullRequest) {
        String key = pullRequest.repository() + "#" + pullRequest.number();
        List<PendingRecord> records = new ArrayList<>();
        records.add(new PendingRecord(GitHubRecordKind.PULL_REQUEST, key, pullRequest.url(), pullRequest.payload()));
        records.add(new PendingRecord(
                GitHubRecordKind.PULL_REQUEST_DIFF, key, pullRequest.url() + "/files", diffPayload(pullRequest)));
        addParts(records, GitHubRecordKind.REVIEW, pullRequest.reviews(), key);
        addParts(records, GitHubRecordKind.REVIEW_COMMENT, pullRequest.reviewComments(), key);
        addParts(records, GitHubRecordKind.PULL_REQUEST_COMMENT, pullRequest.pullRequestComments(), key);
        return records;
    }

    private static JsonNode diffPayload(FetchedPullRequest pullRequest) {
        ObjectNode diff = JsonNodeFactory.instance.objectNode();
        diff.putArray("files").addAll(pullRequest.diffFiles());
        diff.put("truncated", pullRequest.diffTruncated());
        return diff;
    }

    private static void addParts(List<PendingRecord> records, GitHubRecordKind kind, List<JsonNode> parts, String key) {
        for (JsonNode part : parts) {
            records.add(new PendingRecord(
                    kind, required(part, "databaseId", kind, key), required(part, "url", kind, key), part));
        }
    }

    private static String required(JsonNode part, String field, GitHubRecordKind kind, String key) {
        JsonNode value = part.path(field);
        if (!value.isValueNode() || value.isNull() || value.asString().isBlank()) {
            throw new GitHubApiException(
                    "A " + kind.recordKind().replace('_', ' ') + " of " + key + " has no " + field + ": " + part);
        }
        return value.asString();
    }

    private record PendingRecord(GitHubRecordKind kind, String externalId, String url, JsonNode payload) {}
}
