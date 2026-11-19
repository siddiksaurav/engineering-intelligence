package com.dsinnovators.ei.source.github;

/** The kinds of GitHub record in {@code raw_record.record_kind} (F02 design 6.2). */
public enum GitHubRecordKind {
    PULL_REQUEST("pull_request"),
    PULL_REQUEST_DIFF("pull_request_diff"),
    REVIEW("review"),
    REVIEW_COMMENT("review_comment"),
    PULL_REQUEST_COMMENT("pull_request_comment");

    private final String recordKind;

    GitHubRecordKind(String recordKind) {
        this.recordKind = recordKind;
    }

    /** The value of {@code raw_record.record_kind}. */
    public String recordKind() {
        return recordKind;
    }
}
