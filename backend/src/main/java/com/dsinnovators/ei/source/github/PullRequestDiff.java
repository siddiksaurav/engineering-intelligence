package com.dsinnovators.ei.source.github;

import com.dsinnovators.ei.source.RawRecord;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * The files of a pull request as a stored {@code pull_request_diff} record holds them. Line counts
 * are not read: they are never given to the model (decision 132).
 *
 * @param recordId the exact record version the files were read from
 */
public record PullRequestDiff(long recordId, List<File> files) {

    public PullRequestDiff {
        files = List.copyOf(files);
    }

    /**
     * One changed file.
     *
     * @param status GitHub's status, such as {@code added} or {@code modified}
     * @param patch the unified diff; empty for a binary or very large file, for which GitHub gives none
     */
    public record File(String path, String status, Optional<String> patch) {}

    static PullRequestDiff of(RawRecord record) {
        List<File> files = record.getPayload()
                .path("files")
                .valueStream()
                .map(PullRequestDiff::fileOf)
                .toList();
        return new PullRequestDiff(record.getId(), files);
    }

    private static File fileOf(JsonNode file) {
        JsonNode patch = file.path("patch");
        return new File(
                file.path("filename").asString(),
                file.path("status").asString(),
                patch.isString() ? Optional.of(patch.asString()) : Optional.empty());
    }
}
