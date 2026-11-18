package com.dsinnovators.ei.job;

/**
 * Work that is started by name from the command line: {@code --job=<name> --key=value ...}. A job
 * is a Spring bean; a feature adds one by declaring a {@code @Component} that implements this
 * interface. Names are unique.
 */
public interface Job {

    /** The name the job is started by. */
    String name();

    /** Runs the job. Returning means success; any exception means failure. */
    void run(JobArguments arguments) throws Exception;
}
