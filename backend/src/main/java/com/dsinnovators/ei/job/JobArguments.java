package com.dsinnovators.ei.job;

import java.util.Optional;

/** The {@code --key=value} arguments a job was started with. {@code --key} alone has the value "". */
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
