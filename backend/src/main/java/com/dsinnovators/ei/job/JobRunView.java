package com.dsinnovators.ei.job;

import java.time.Instant;
import java.util.Map;

/**
 * One run of a job as it stands.
 *
 * @param finishedAt null while the run goes on
 * @param errorMessage null unless the run failed or was interrupted
 */
public record JobRunView(
        long id,
        String jobName,
        Map<String, String> arguments,
        String startedBy,
        JobRunStatus status,
        Instant startedAt,
        Instant finishedAt,
        Map<String, Long> counts,
        String errorMessage) {}
