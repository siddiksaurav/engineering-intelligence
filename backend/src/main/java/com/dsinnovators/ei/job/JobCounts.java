package com.dsinnovators.ei.job;

/** Named counts a job adds to while it runs. The launcher stores them on the run. Thread-safe. */
public interface JobCounts {

    /**
     * Adds to the count of that name, which starts at zero.
     *
     * @throws IllegalArgumentException for a blank name or a negative amount
     */
    void add(String name, long amount);
}
