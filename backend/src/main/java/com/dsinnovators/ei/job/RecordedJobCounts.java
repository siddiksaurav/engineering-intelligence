package com.dsinnovators.ei.job;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * The counts of one run, written to its {@code job_run} row at most once per interval while the
 * run goes on, so a long run shows its progress.
 */
final class RecordedJobCounts implements JobCounts {

    private final JobRunCounts counts = new JobRunCounts();
    private final long runId;
    private final JobRunLog log;
    private final Clock clock;
    private final Duration interval;
    private Instant lastRecorded;

    RecordedJobCounts(long runId, JobRunLog log, Clock clock, Duration interval) {
        this.runId = runId;
        this.log = log;
        this.clock = clock;
        this.interval = interval;
        this.lastRecorded = clock.instant();
    }

    @Override
    public void add(String name, long amount) {
        counts.add(name, amount);
        recordWhenDue();
    }

    JobRunCounts counts() {
        return counts;
    }

    private synchronized void recordWhenDue() {
        Instant now = clock.instant();
        if (Duration.between(lastRecorded, now).compareTo(interval) >= 0) {
            log.recordCounts(runId, counts.snapshot());
            lastRecorded = now;
        }
    }
}
