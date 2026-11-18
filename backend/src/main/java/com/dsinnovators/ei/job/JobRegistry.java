package com.dsinnovators.ei.job;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The jobs of the application, by name.
 *
 * <p>Nothing reads the registry yet. F02 adds the launcher that does (F01 design, section 13).
 */
class JobRegistry {

    private final Map<String, Job> jobs = new TreeMap<>();

    /**
     * Registers the jobs by name.
     *
     * @throws IllegalStateException when two jobs share a name
     */
    JobRegistry(List<Job> jobs) {
        for (Job job : jobs) {
            if (this.jobs.putIfAbsent(job.name(), job) != null) {
                throw new IllegalStateException("Two jobs are named '" + job.name() + "'");
            }
        }
    }

    Optional<Job> find(String name) {
        return Optional.ofNullable(jobs.get(name));
    }

    /** The known job names, sorted. */
    List<String> names() {
        return List.copyOf(jobs.keySet());
    }
}
