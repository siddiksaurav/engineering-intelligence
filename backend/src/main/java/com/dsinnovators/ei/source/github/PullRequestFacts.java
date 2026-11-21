package com.dsinnovators.ei.source.github;

import com.dsinnovators.ei.source.RawRecord;
import java.time.Instant;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * What the evidence builders read from a stored {@code pull_request} record.
 *
 * @param rawRecordId the exact record version the facts were read from
 * @param externalId the record's external id, such as {@code opf/openproject#24153}
 */
public record PullRequestFacts(
        long rawRecordId,
        String externalId,
        int number,
        String title,
        String url,
        Instant mergedAt,
        Optional<GitHubAuthor> author) {

    public static PullRequestFacts of(RawRecord record) {
        JsonNode payload = record.getPayload();
        return new PullRequestFacts(
                record.getId(),
                record.getExternalId(),
                payload.path("number").asInt(),
                payload.path("title").asString(),
                payload.path("url").asString(),
                Instant.parse(payload.path("mergedAt").asString()),
                GitHubAuthor.of(payload));
    }
}
