package com.dsinnovators.ei.job;

/**
 * A named piece of batch work, such as an import from a source. A job is a Spring bean; a feature
 * adds one by declaring a {@code @Component} that implements this interface. Names are unique.
 * {@link JobLauncher} starts it and records the run.
 */
public interface Job {

    /** The name the job is started by. */
    String name();

    /**
     * Checks the arguments before a run is recorded, so a mistake is reported to the caller at once.
     *
     * @throws IllegalArgumentException with a message that names the argument and its value
     */
    void checkArguments(JobArguments arguments);

    /** Runs the job. Returning means success; any exception means failure. */
    void run(JobArguments arguments, JobCounts counts) throws Exception;
}
