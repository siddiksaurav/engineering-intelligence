package com.dsinnovators.ei.job;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/** A run as the endpoint shows it. The status is in lower case, as in {@code job_run}. */
record JobRunResponse(
        long id,
        String jobName,
        Map<String, String> arguments,
        String startedBy,
        String status,
        Instant startedAt,
        Instant finishedAt,
        Map<String, Long> counts,
        String errorMessage) {

    static JobRunResponse of(JobRunView run) {
        return new JobRunResponse(
                run.id(),
                run.jobName(),
                run.arguments(),
                run.startedBy(),
                run.status().name().toLowerCase(Locale.ROOT),
                run.startedAt(),
                run.finishedAt(),
                run.counts(),
                run.errorMessage());
    }
}
