package com.dsinnovators.ei.job;

/** A job was started while a run of it was still going on. */
public class JobAlreadyRunningException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final long runningRunId;

    JobAlreadyRunningException(String jobName, long runningRunId) {
        super("The job '" + jobName + "' is already running as run " + runningRunId);
        this.runningRunId = runningRunId;
    }

    /** The id of the run that is going on. */
    public long runningRunId() {
        return runningRunId;
    }
}
