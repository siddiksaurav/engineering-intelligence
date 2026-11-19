package com.dsinnovators.ei.job;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The record of every run in {@code job_run}: written by the launcher, read by the endpoint. */
@Service
class JobRunLog {

    static final String INTERRUPTED_MESSAGE = "The application stopped while the run was going on";

    private final JobRunRepository runs;

    JobRunLog(JobRunRepository runs) {
        this.runs = runs;
    }

    /**
     * Records the start of a run.
     *
     * @throws JobAlreadyRunningException when a run of the job is going on; the database refuses a
     *     second one
     */
    // Not transactional: the refused insert must not leave a transaction that can only roll back,
    // because the running run is looked up after it.
    JobRunView recordStart(String jobName, Map<String, String> arguments, String startedBy, Instant startedAt) {
        try {
            return view(runs.saveAndFlush(new JobRun(jobName, arguments, startedBy, startedAt)));
        } catch (DataIntegrityViolationException refused) {
            JobRun running =
                    runs.findByJobNameAndStatus(jobName, JobRunStatus.RUNNING).orElseThrow(() -> refused);
            throw new JobAlreadyRunningException(jobName, running.getId());
        }
    }

    @Transactional
    void recordCounts(long runId, Map<String, Long> counts) {
        run(runId).updateCounts(counts);
    }

    @Transactional
    void recordSuccess(long runId, Map<String, Long> counts, Instant finishedAt) {
        run(runId).finish(JobRunStatus.SUCCEEDED, counts, finishedAt, null);
    }

    @Transactional
    void recordFailure(long runId, Map<String, Long> counts, Instant finishedAt, String errorMessage) {
        run(runId).finish(JobRunStatus.FAILED, counts, finishedAt, errorMessage);
    }

    /** Marks every run that is still recorded as running as interrupted. Returns how many. */
    @Transactional
    int markRunningAsInterrupted(Instant finishedAt) {
        return runs.markRunningAsInterrupted(finishedAt, INTERRUPTED_MESSAGE);
    }

    @Transactional(readOnly = true)
    Optional<JobRunView> find(long runId) {
        return runs.findById(runId).map(JobRunLog::view);
    }

    private JobRun run(long runId) {
        return runs.findById(runId).orElseThrow(() -> new IllegalStateException("No job run with id " + runId));
    }

    private static JobRunView view(JobRun run) {
        return new JobRunView(
                run.getId(),
                run.getJobName(),
                run.getArguments(),
                run.getStartedBy(),
                run.getStatus(),
                run.getStartedAt(),
                run.getFinishedAt().orElse(null),
                run.getCounts(),
                run.getErrorMessage().orElse(null));
    }
}
