package com.dsinnovators.ei.job;

/** Where a run of a job stands. Stored in lower case in {@code job_run.status}. */
public enum JobRunStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    /** The application stopped while the run was going on. */
    INTERRUPTED
}
