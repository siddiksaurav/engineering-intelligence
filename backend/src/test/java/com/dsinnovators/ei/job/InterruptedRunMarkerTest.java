package com.dsinnovators.ei.job;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dsinnovators.ei.DatabaseTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InterruptedRunMarkerTest extends DatabaseTest {

    private static final Instant STARTED = Instant.parse("2026-11-19T07:00:00Z");
    private static final Instant RESTARTED = Instant.parse("2026-11-19T08:00:00Z");

    @Autowired
    private JobRunLog log;

    @Test
    void aRunLeftRunningByAStopIsMarkedInterruptedAtTheNextStart() {
        long id = log.recordStart("import", Map.of(), "api", STARTED).id();

        new InterruptedRunMarker(log, Clock.fixed(RESTARTED, ZoneOffset.UTC)).afterSingletonsInstantiated();

        JobRunView interrupted = log.find(id).orElseThrow();
        assertEquals(JobRunStatus.INTERRUPTED, interrupted.status());
        assertEquals(RESTARTED, interrupted.finishedAt());
        assertEquals("The application stopped while the run was going on", interrupted.errorMessage());
    }

    @Test
    void aFinishedRunIsLeftAsItIs() {
        long id = log.recordStart("import", Map.of(), "api", STARTED).id();
        log.recordSuccess(id, Map.of(), STARTED);

        new InterruptedRunMarker(log, Clock.fixed(RESTARTED, ZoneOffset.UTC)).afterSingletonsInstantiated();

        JobRunView finished = log.find(id).orElseThrow();
        assertEquals(JobRunStatus.SUCCEEDED, finished.status());
        assertEquals(STARTED, finished.finishedAt());
    }
}
