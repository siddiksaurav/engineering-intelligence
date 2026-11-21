package com.dsinnovators.ei.source.github;

import com.dsinnovators.ei.source.RawRecord;
import java.time.Instant;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * What the evidence builders read from a stored {@code review_comment} record, an inline comment
 * on the code.
 *
 * @param rawRecordId the exact record version the facts were read from
 * @param isReply whether the comment answers another comment of its thread
 * @param repository the repository of the pull request, such as {@code opf/openproject}
 */
public record ReviewCommentFacts(
        long rawRecordId,
        long databaseId,
        String url,
        String path,
        Instant createdAt,
        boolean isReply,
        int pullRequestNumber,
        String repository,
        Optional<GitHubAuthor> author) {

    public static ReviewCommentFacts of(RawRecord record) {
        JsonNode payload = record.getPayload();
        JsonNode pullRequest = payload.path("pullRequest");
        return new ReviewCommentFacts(
                record.getId(),
                payload.path("databaseId").asLong(),
                payload.path("url").asString(),
                payload.path("path").asString(),
                Instant.parse(payload.path("createdAt").asString()),
                payload.path("replyTo").isObject(),
                pullRequest.path("number").asInt(),
                pullRequest.path("repository").path("nameWithOwner").asString(),
                GitHubAuthor.of(payload));
    }

    /** The external id of the comment's pull request, such as {@code opf/openproject#24153}. */
    public String pullRequestExternalId() {
        return repository + "#" + pullRequestNumber;
    }
}
