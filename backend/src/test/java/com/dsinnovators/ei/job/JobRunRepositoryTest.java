package com.dsinnovators.ei.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class JobRunRepositoryTest extends DatabaseTest {

    private static final Instant STARTED_AT = Instant.parse("2026-11-19T08:00:00Z");

    private static final String INSERT_RUN = "insert into job_run (job_name, arguments, started_by, status, started_at,"
            + " finished_at, counts) values ('github-import', '{}'::jsonb, 'api', ?, now(), ?, '{}'::jsonb)";

    @Autowired
    private JobRunRepository runs;

    private JobRun newRun(String jobName) {
        return new JobRun(jobName, Map.of("repository", "opf/openproject"), "api", STARTED_AT);
    }

    @Test
    void aNewRunIsStoredAsRunningWithItsArgumentsAndNoCounts() {
        long id = runs.saveAndFlush(newRun("github-import")).getId();

        JobRun stored = runs.findById(id).orElseThrow();
        assertEquals("github-import", stored.getJobName());
        assertEquals(Map.of("repository", "opf/openproject"), stored.getArguments());
        assertEquals("api", stored.getStartedBy());
        assertEquals(JobRunStatus.RUNNING, stored.getStatus());
        assertEquals(STARTED_AT, stored.getStartedAt().truncatedTo(ChronoUnit.SECONDS));
        assertTrue(stored.getFinishedAt().isEmpty());
        assertEquals(Map.of(), stored.getCounts());
        assertTrue(stored.getErrorMessage().isEmpty());
    }

    @Test
    void theStatusIsStoredInLowerCase() {
        long id = runs.saveAndFlush(newRun("github-import")).getId();

        assertEquals("running", jdbc.queryForObject("select status from job_run where id = ?", String.class, id));
    }

    @Test
    void aSecondRunningRunOfTheSameJobIsRefusedByTheDatabase() {
        runs.saveAndFlush(newRun("github-import"));

        assertThrows(DataIntegrityViolationException.class, () -> runs.saveAndFlush(newRun("github-import")));
    }

    @Test
    void runningRunsOfDifferentJobsAreAllowed() {
        runs.saveAndFlush(newRun("github-import"));
        runs.saveAndFlush(newRun("openproject-import"));

        assertEquals(2, runs.count());
    }

    @Test
    void aFinishedRunDoesNotBlockARunningOneOfTheSameJob() {
        jdbc.update(INSERT_RUN, "succeeded", Timestamp.from(STARTED_AT));
        jdbc.update(INSERT_RUN, "running", null);

        assertEquals(2, runs.count());
    }

    @Test
    void anUnknownStatusIsRefused() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update(INSERT_RUN, "paused", Timestamp.from(STARTED_AT)));
    }

    @Test
    void aRunningRunHasNoFinishTime() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update(INSERT_RUN, "running", Timestamp.from(STARTED_AT)));
    }

    @Test
    void aFinishedRunHasAFinishTime() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(INSERT_RUN, "failed", null));
    }
}
