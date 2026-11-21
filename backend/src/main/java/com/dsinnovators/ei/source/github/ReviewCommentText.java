package com.dsinnovators.ei.source.github;

import com.dsinnovators.ei.source.RawRecord;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.OptionalLong;
import tools.jackson.databind.JsonNode;

/**
 * What a judgment reads of a stored {@code review_comment} record: the words and the code around them.
 *
 * @param rawRecordId the exact record version the text was read from
 * @param path the file the comment is on
 * @param body the Markdown text of the comment; empty when there is none
 * @param diffHunk the part of the diff the comment is on, its last line being the commented one; empty
 *     when there is none
 * @param repliedToDatabaseId the comment this one answers; empty for the first comment of a thread
 */
public record ReviewCommentText(
        long rawRecordId,
        long databaseId,
        String url,
        String path,
        Instant createdAt,
        String body,
        String diffHunk,
        OptionalLong repliedToDatabaseId) {

    /**
     * @throws IllegalStateException when the record has no readable {@code createdAt}; the message names
     *     the record by its id and external id
     */
    static ReviewCommentText of(RawRecord record) {
        JsonNode payload = record.getPayload();
        JsonNode repliedTo = payload.path("replyTo").path("databaseId");
        return new ReviewCommentText(
                record.getId(),
                payload.path("databaseId").asLong(),
                payload.path("url").asString(),
                payload.path("path").asString(),
                createdAtOf(record),
                payload.path("body").asString(""),
                payload.path("diffHunk").asString(""),
                repliedTo.isNumber() ? OptionalLong.of(repliedTo.asLong()) : OptionalLong.empty());
    }

    private static Instant createdAtOf(RawRecord record) {
        String createdAt = record.getPayload().path("createdAt").asString("");
        try {
            return Instant.parse(createdAt);
        } catch (DateTimeParseException unreadable) {
            throw new IllegalStateException(
                    "The review comment record " + record.getId() + " (external id " + record.getExternalId()
                            + ") has no readable createdAt, was '" + createdAt + "'",
                    unreadable);
        }
    }
}
