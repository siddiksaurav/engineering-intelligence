package com.dsinnovators.ei.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class JobRunnerTest {

    /** Keeps the arguments it was run with. */
    static class RecordingJob implements Job {
        private final String name;
        JobArguments received;

        RecordingJob(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void run(JobArguments arguments) {
            received = arguments;
        }
    }

    static class FailingJob implements Job {
        @Override
        public String name() {
            return "boom";
        }

        @Override
        public void run(JobArguments arguments) {
            throw new IllegalStateException("source unreachable");
        }
    }

    static class NeedsFromJob implements Job {
        @Override
        public String name() {
            return "needs-from";
        }

        @Override
        public void run(JobArguments arguments) {
            arguments.require("from");
        }
    }

    private RecordingJob echo;
    private JobRunner runner;

    @BeforeEach
    void setUp() {
        echo = new RecordingJob("echo");
        runner = new JobRunner(new JobRegistry(List.of(echo, new FailingJob(), new NeedsFromJob())));
    }

    @Test
    void runsTheNamedJobWithItsArguments() {
        assertEquals(0, runner.run("--job=echo", "--from=2026-07-01", "--dry-run"));

        assertEquals(Optional.of("2026-07-01"), echo.received.get("from"));
        assertEquals("2026-07-01", echo.received.require("from"));
        assertEquals(Optional.empty(), echo.received.get("to"));
        assertEquals(Optional.of(""), echo.received.get("dry-run"));
        assertEquals(Optional.empty(), echo.received.get("job"));
    }

    @Test
    void anUnknownNameListsTheKnownJobsAndFails(CapturedOutput output) {
        assertEquals(1, runner.run("--job=nope"));

        assertTrue(output.getAll().contains("nope"));
        assertTrue(output.getAll().contains("boom, echo, needs-from"));
        assertNull(echo.received);
    }

    @Test
    void anEmptyNameListsTheKnownJobsAndFails(CapturedOutput output) {
        assertEquals(1, runner.run("--job="));

        assertTrue(output.getAll().contains("boom, echo, needs-from"));
        assertNull(echo.received);
    }

    @Test
    void aNameGivenTwiceRunsNoJobAndFails(CapturedOutput output) {
        assertEquals(1, runner.run("--job=echo", "--job=boom"));

        assertTrue(output.getAll().contains("boom, echo, needs-from"));
        assertFalse(output.getAll().contains("source unreachable"));
        assertNull(echo.received);
    }

    @Test
    void aJobThatThrowsFailsWithItsMessage(CapturedOutput output) {
        assertEquals(1, runner.run("--job=boom"));

        assertTrue(output.getAll().contains("boom"));
        assertTrue(output.getAll().contains("source unreachable"));
    }

    @Test
    void aMissingRequiredArgumentFailsAndNamesTheArgument(CapturedOutput output) {
        assertEquals(1, runner.run("--job=needs-from"));

        assertTrue(output.getAll().contains("from"));
        assertTrue(output.getAll().contains("--from"));
    }

    @Test
    void anArgumentWithoutTwoDashesRunsNoJobAndFailsNamingIt(CapturedOutput output) {
        assertEquals(1, runner.run("--job=echo", "from=2026-07-01"));

        assertTrue(output.getAll().contains("from=2026-07-01"));
        assertNull(echo.received);
    }

    @Test
    void anArgumentGivenTwiceRunsNoJobAndFailsNamingIt(CapturedOutput output) {
        assertEquals(1, runner.run("--job=echo", "--from=2026-07-01", "--from=2026-08-01"));

        assertTrue(output.getAll().contains("--from is given more than once"));
        assertNull(echo.received);
    }

    @Test
    void requireThrowsForAMissingArgument() {
        runner.run("--job=echo");

        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> echo.received.require("from"));
        assertTrue(e.getMessage().contains("from"));
    }

    @Test
    void twoJobsWithOneNameAreRejected() {
        IllegalStateException e = assertThrows(
                IllegalStateException.class,
                () -> new JobRegistry(List.of(new RecordingJob("echo"), new RecordingJob("echo"))));
        assertTrue(e.getMessage().contains("echo"));
    }

    @Test
    void namesAreSorted() {
        assertEquals(
                List.of("boom", "echo"),
                new JobRegistry(List.of(new RecordingJob("echo"), new FailingJob())).names());
    }

    @Test
    void withNoJobsTheListSaysSo(CapturedOutput output) {
        assertEquals(1, new JobRunner(new JobRegistry(List.of())).run("--job=nope"));

        assertTrue(output.getAll().contains("No jobs are known"));
    }
}
