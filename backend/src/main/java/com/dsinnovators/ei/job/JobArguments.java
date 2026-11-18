package com.dsinnovators.ei.job;

import java.util.Optional;

/**
 * The named arguments a job was started with.
 *
 * <p>There is no implementation yet. F02 adds it with the launcher (F01 design, section 13).
 */
public interface JobArguments {

    /** The value of the argument, or empty when it was not given. */
    Optional<String> get(String name);

    /**
     * The value of an argument the job cannot run without.
     *
     * @throws IllegalArgumentException that names the argument when it was not given
     */
    String require(String name);
}
