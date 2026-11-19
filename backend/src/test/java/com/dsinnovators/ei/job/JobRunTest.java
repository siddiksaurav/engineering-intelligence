package com.dsinnovators.ei.job;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JobRunTest {

    private static final Instant NOW = Instant.parse("2026-11-19T08:00:00Z");

    private final JobRun run = new JobRun("import", Map.of(), "api", NOW);

    @Test
    void aRunEndsOnlyOnce() {
        run.finish(JobRunStatus.SUCCEEDED, Map.of(), NOW, null);

        assertThrows(IllegalStateException.class, () -> run.finish(JobRunStatus.FAILED, Map.of(), NOW, "late"));
    }

    @Test
    void theCountsOfAnEndedRunDoNotChange() {
        run.finish(JobRunStatus.SUCCEEDED, Map.of(), NOW, null);

        assertThrows(IllegalStateException.class, () -> run.updateCounts(Map.of("listed", 1L)));
    }

    @Test
    void aRunCannotFinishAsRunning() {
        assertThrows(IllegalArgumentException.class, () -> run.finish(JobRunStatus.RUNNING, Map.of(), NOW, null));
    }
}
