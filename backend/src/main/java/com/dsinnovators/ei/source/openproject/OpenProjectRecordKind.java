package com.dsinnovators.ei.source.openproject;

/** The kinds of OpenProject record in {@code raw_record.record_kind} (F03 design, "What is stored"). */
enum OpenProjectRecordKind {
    WORK_PACKAGE("work_package"),
    WORK_PACKAGE_ACTIVITY("work_package_activity"),
    WORK_PACKAGE_PULL_REQUESTS("work_package_pull_requests"),
    PROJECT("project");

    private final String recordKind;

    OpenProjectRecordKind(String recordKind) {
        this.recordKind = recordKind;
    }

    /** The value of {@code raw_record.record_kind}. */
    String recordKind() {
        return recordKind;
    }
}
