package com.dsinnovators.ei.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The launcher with jobs made for the test. It runs each job on the calling thread, so a test sees
 * the finished run when {@code start} returns; the background thread is covered by the endpoint
 * tests.
 */
class JobLauncherTest extends DatabaseTest {

    private static final Instant NOW = Instant.parse("2026-11-19T08:00:00Z");
    private static final Map<String, String> ARGUMENTS = Map.of("repository", "opf/openproject");

    @Autowired
    private JobRunLog log;

    private JobLauncher launcher(Duration countFlushInterval, Job... jobs) {
        return new JobLauncher(
                new JobRegistry(List.of(jobs)),
                log,
                Runnable::run,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new JobsProperties(countFlushInterval));
    }

    private JobLauncher launcher(Job... jobs) {
        return launcher(Duration.ofSeconds(30), jobs);
    }

    private interface Body {
        void run(JobArguments arguments, JobCounts counts) throws Exception;
    }

    private record TestJob(String name, Body body) implements Job {
        @Override
        public void checkArguments(JobArguments arguments) {
            arguments.require("repository");
        }

        @Override
        public void run(JobArguments arguments, JobCounts counts) throws Exception {
            body.run(arguments, counts);
        }
    }

    private String statusInDatabase(String jobName) {
        return jdbc.queryForObject("select status from job_run where job_name = ?", String.class, jobName);
    }

    @Test
    void aRunIsRecordedAsRunningThenSucceededWithItsCounts() {
        StringBuilder statusWhileRunning = new StringBuilder();
        JobLauncher launcher = launcher(new TestJob("import", (arguments, counts) -> {
            statusWhileRunning.append(statusInDatabase("import"));
            counts.add("pull_request.created", 2);
        }));

        JobRunView started = launcher.start("import", ARGUMENTS, "api");

        assertEquals("running", statusWhileRunning.toString());
        JobRunView finished = log.find(started.id()).orElseThrow();
        assertEquals("import", finished.jobName());
        assertEquals(ARGUMENTS, finished.arguments());
        assertEquals("api", finished.startedBy());
        assertEquals(JobRunStatus.SUCCEEDED, finished.status());
        assertEquals(NOW, finished.startedAt());
        assertEquals(NOW, finished.finishedAt());
        assertEquals(Map.of("pull_request.created", 2L), finished.counts());
        assertEquals(null, finished.errorMessage());
    }

    @Test
    void theRunIsReturnedAsRunningWhenItStarts() {
        JobRunView started =
                launcher(new TestJob("import", (arguments, counts) -> {})).start("import", ARGUMENTS, "api");

        assertEquals(JobRunStatus.RUNNING, started.status());
        assertEquals(null, started.finishedAt());
    }

    @Test
    void aFailingJobIsRecordedAsFailedWithItsMessageAndTheCountsSoFar() {
        JobLauncher launcher = launcher(new TestJob("import", (arguments, counts) -> {
            counts.add("pull_request.created", 1);
            throw new IllegalStateException("GitHub refused the token");
        }));

        long id = launcher.start("import", ARGUMENTS, "api").id();

        JobRunView failed = log.find(id).orElseThrow();
        assertEquals(JobRunStatus.FAILED, failed.status());
        assertEquals("GitHub refused the token", failed.errorMessage());
        assertEquals(Map.of("pull_request.created", 1L), failed.counts());
        assertEquals(NOW, failed.finishedAt());
    }

    @Test
    void anExceptionWithoutAMessageIsRecordedByItsType() {
        JobLauncher launcher = launcher(new TestJob("import", (arguments, counts) -> {
            throw new IllegalStateException();
        }));

        long id = launcher.start("import", ARGUMENTS, "api").id();

        assertEquals(
                "java.lang.IllegalStateException", log.find(id).orElseThrow().errorMessage());
    }

    @Test
    void anUnknownJobIsRefusedWithTheKnownNamesAndNothingIsRecorded() {
        JobLauncher launcher = launcher(new TestJob("import", (arguments, counts) -> {}));

        UnknownJobException refused =
                assertThrows(UnknownJobException.class, () -> launcher.start("nope", ARGUMENTS, "api"));

        assertEquals("No job named 'nope'. Known jobs: [import]", refused.getMessage());
        assertEquals(0, jdbc.queryForObject("select count(*) from job_run", Integer.class));
    }

    @Test
    void badArgumentsAreRefusedAndNothingIsRecorded() {
        JobLauncher launcher = launcher(new TestJob("import", (arguments, counts) -> {}));

        IllegalArgumentException refused =
                assertThrows(IllegalArgumentException.class, () -> launcher.start("import", Map.of(), "api"));

        assertEquals("The argument 'repository' is required", refused.getMessage());
        assertEquals(0, jdbc.queryForObject("select count(*) from job_run", Integer.class));
    }

    @Test
    void aSecondStartWhileTheJobRunsIsRefusedWithTheRunningRunsId() {
        JobLauncher launcher = launcher(new TestJob("import", (arguments, counts) -> {}));
        long runningId = log.recordStart("import", ARGUMENTS, "api", NOW).id();

        JobAlreadyRunningException refused =
                assertThrows(JobAlreadyRunningException.class, () -> launcher.start("import", ARGUMENTS, "api"));

        assertEquals(runningId, refused.runningRunId());
        assertEquals("The job 'import' is already running as run " + runningId, refused.getMessage());
        assertEquals(1, jdbc.queryForObject("select count(*) from job_run", Integer.class));
    }

    @Test
    void countsAreWrittenWhileTheRunGoesOn() {
        StringBuilder countsWhileRunning = new StringBuilder();
        JobLauncher launcher = launcher(Duration.ZERO, new TestJob("import", (arguments, counts) -> {
            counts.add("pull_request.created", 4);
            countsWhileRunning.append(
                    jdbc.queryForObject("select counts::text from job_run where job_name = 'import'", String.class));
        }));

        launcher.start("import", ARGUMENTS, "api");

        assertEquals("{\"pull_request.created\": 4}", countsWhileRunning.toString());
    }

    @Test
    void aJobMayRunAgainOnceItsRunHasFinished() {
        JobLauncher launcher = launcher(new TestJob("import", (arguments, counts) -> {}));

        launcher.start("import", ARGUMENTS, "api");
        launcher.start("import", ARGUMENTS, "api");

        assertEquals(2, jdbc.queryForObject("select count(*) from job_run where status = 'succeeded'", Integer.class));
    }

    @Test
    void anUnknownRunIsNotFound() {
        assertTrue(log.find(999_999L).isEmpty());
    }
}
