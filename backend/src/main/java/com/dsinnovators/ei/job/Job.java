package com.dsinnovators.ei.job;

/**
 * A named piece of batch work, such as an import from a source. A job is a Spring bean; a feature
 * adds one by declaring a {@code @Component} that implements this interface. Names are unique.
 *
 * <p>Nothing starts a job yet. F02 adds the launcher that runs a job and records the run, and the
 * endpoint that starts it (F01 design, section 13).
 */
public interface Job {

    /** The name the job is started by. */
    String name();

    /** Runs the job. Returning means success; any exception means failure. */
    void run(JobArguments arguments) throws Exception;
}
