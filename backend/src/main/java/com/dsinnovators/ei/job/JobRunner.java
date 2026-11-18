package com.dsinnovators.ei.job;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Runs the one job that the command line names and turns the outcome into an exit code. */
public class JobRunner {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);

    private final JobRegistry registry;

    JobRunner(JobRegistry registry) {
        this.registry = registry;
    }

    /** @return 0 when the job returns, 1 in every other case */
    public int run(String... args) {
        CommandLineJobArguments arguments;
        try {
            arguments = new CommandLineJobArguments(args);
        } catch (IllegalArgumentException e) {
            log.error("{}. {}", e.getMessage(), knownJobs());
            return 1;
        }
        List<String> names = arguments.jobNames();
        if (names.size() != 1 || names.get(0).isBlank()) {
            log.error("Exactly one --job=<name> is needed. {}", knownJobs());
            return 1;
        }
        String name = names.get(0);
        Optional<Job> job = registry.find(name);
        if (job.isEmpty()) {
            log.error("No job named '{}'. {}", name, knownJobs());
            return 1;
        }
        log.info("Job '{}' started", name);
        try {
            job.get().run(arguments);
        } catch (Exception e) {
            log.error("Job '{}' failed: {}", name, e.getMessage(), e);
            return 1;
        }
        log.info("Job '{}' finished", name);
        return 0;
    }

    private String knownJobs() {
        List<String> names = registry.names();
        return names.isEmpty() ? "No jobs are known." : "Known jobs: " + String.join(", ", names);
    }
}
