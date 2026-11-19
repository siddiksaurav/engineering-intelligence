package com.dsinnovators.ei.job;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts any job and records its run in {@code job_run}. The job runs in the background; the
 * caller gets the run as it starts and follows it by its id.
 */
public class JobLauncher {

    private static final Logger LOG = LoggerFactory.getLogger(JobLauncher.class);

    private final JobRegistry registry;
    private final JobRunLog log;
    private final Executor executor;
    private final Clock clock;
    private final JobsProperties properties;

    JobLauncher(JobRegistry registry, JobRunLog log, Executor executor, Clock clock, JobsProperties properties) {
        this.registry = registry;
        this.log = log;
        this.executor = executor;
        this.clock = clock;
        this.properties = properties;
    }

    /**
     * Checks the arguments, records the run and starts it.
     *
     * @param startedBy who or what started the run, for example {@code api}
     * @throws UnknownJobException when no job has that name
     * @throws IllegalArgumentException when the job refuses its arguments; nothing is recorded
     * @throws JobAlreadyRunningException when a run of the job is going on; nothing is recorded
     */
    public JobRunView start(String jobName, Map<String, String> arguments, String startedBy) {
        Job job = registry.find(jobName).orElseThrow(() -> new UnknownJobException(jobName, registry.names()));
        JobArguments jobArguments = JobArguments.of(arguments);
        job.checkArguments(jobArguments);
        JobRunView started = log.recordStart(jobName, jobArguments.asMap(), startedBy, clock.instant());
        LOG.info("Run {} of job '{}' started with {}", started.id(), jobName, jobArguments.asMap());
        executor.execute(() -> run(job, jobArguments, started.id()));
        return started;
    }

    private void run(Job job, JobArguments arguments, long runId) {
        RecordedJobCounts counts = new RecordedJobCounts(runId, log, clock, properties.countFlushInterval());
        try {
            job.run(arguments, counts);
            log.recordSuccess(runId, counts.counts().snapshot(), clock.instant());
            LOG.info(
                    "Run {} of job '{}' succeeded: {}",
                    runId,
                    job.name(),
                    counts.counts().snapshot());
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.error("Run {} of job '{}' failed", runId, job.name(), failure);
            log.recordFailure(runId, counts.counts().snapshot(), clock.instant(), message(failure));
        }
    }

    private static String message(Exception failure) {
        return failure.getMessage() == null ? failure.getClass().getName() : failure.getMessage();
    }
}
