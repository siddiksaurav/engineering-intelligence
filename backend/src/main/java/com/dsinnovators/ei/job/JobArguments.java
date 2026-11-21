package com.dsinnovators.ei.job;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The named arguments a job was started with. Every value is text. */
public interface JobArguments {

    /**
     * The arguments as given, copied.
     *
     * @throws IllegalArgumentException for a blank name or a missing value, naming it
     */
    static JobArguments of(Map<String, String> values) {
        return new MapJobArguments(values);
    }

    /** The value of the argument, or empty when it was not given. */
    Optional<String> get(String name);

    /**
     * The value of an argument the job cannot run without.
     *
     * @throws IllegalArgumentException that names the argument when it was not given
     */
    String require(String name);

    /**
     * Checks that the job was given no argument at all.
     *
     * @param jobName the job, named in the message
     * @throws IllegalArgumentException that names the job and the arguments given
     */
    default void requireNone(String jobName) {
        if (!names().isEmpty()) {
            throw new IllegalArgumentException("The job " + jobName + " takes no arguments, was given " + names());
        }
    }

    /** The names of the given arguments. */
    Set<String> names();

    /** The arguments as a map, sorted by name. */
    Map<String, String> asMap();
}
