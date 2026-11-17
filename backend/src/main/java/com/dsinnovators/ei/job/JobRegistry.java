package com.dsinnovators.ei.job;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** The jobs of the application, by name. */
public class JobRegistry {

    private final Map<String, Job> jobs = new TreeMap<>();

    /** @throws IllegalStateException when two jobs share a name */
    public JobRegistry(List<Job> jobs) {
        for (Job job : jobs) {
            if (this.jobs.putIfAbsent(job.name(), job) != null) {
                throw new IllegalStateException("Two jobs are named '" + job.name() + "'");
            }
        }
    }

    public Optional<Job> find(String name) {
        return Optional.ofNullable(jobs.get(name));
    }

    /** The known job names, sorted. */
    public List<String> names() {
        return List.copyOf(jobs.keySet());
    }
}
