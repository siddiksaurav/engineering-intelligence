package com.dsinnovators.ei.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.JobEndpointClient;
import com.dsinnovators.ei.analytics.AnalyticsRows;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DimensionScoringJobTest extends DatabaseTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String QUARTER_ARGUMENTS = "{\"quarter\":\"2026-Q3\"}";
    private static final String CODING = "coding_and_code_quality";
    private static final String STANDARDS = "engineering_standards_and_system_health";

    @Value("${local.server.port}")
    private int port;

    private JobEndpointClient jobs;
    private AnalyticsRows rows;
    private long ada;
    private long grace;

    /**
     * Ada has three Feature work items (Low, Low, High) and two feature pull requests without a test
     * change; Grace has two feature pull requests, both tested. One developer is enough for a gate group.
     */
    @BeforeEach
    void storeTwoDevelopersWithFeatureWork() {
        jobs = new JobEndpointClient(port);
        rows = new AnalyticsRows(jdbc);
        ada = rows.person(11, "ada");
        grace = rows.person(12, "grace");
        for (int number = 1; number <= 3; number++) {
            rows.pullRequest(number, 1, 11, "ada", "2026-07-0" + number + "T10:00:00Z");
            long item = rows.item("feature_work", ada, "2026-07-0" + number, false, null, number);
            rows.complexity(item, number == 3 ? "high" : "low", number == 3 ? "T3" : "T1", null);
        }
        rows.digest(1, "feature", null);
        rows.digest(2, "feature", null);
        rows.pullRequest(4, 1, 12, "grace", "2026-07-04T10:00:00Z");
        rows.pullRequest(5, 1, 12, "grace", "2026-07-05T10:00:00Z");
        rows.digest(4, "feature", null);
        rows.digest(5, "feature", null);
        rows.testChange(grace, 4, "2026-07-04");
        rows.testChange(grace, 5, "2026-07-05");
        insertSetting("scoring.gate_minimum_developers", "1");
    }

    private void insertSetting(String key, String value) {
        jdbc.update(
                "insert into setting (key, valid_from_quarter, value, description) values (?, '2026-Q3', ?::jsonb, 'test')",
                key,
                value);
    }

    private JsonNode run(String arguments) throws Exception {
        return jobs.awaitEnd(jobs.startAccepted("dimension-scoring", arguments));
    }

    private Map<String, Object> scoreRow(long runId, long personId, String dimensionKey, String level) {
        return jdbc.queryForMap("""
                select score.working_tier, score.score, score.gates::text as gates, score.trail::text as trail
                from dimension_score score join dimension on dimension.id = score.dimension_id
                where score.job_run_id = ? and score.person_id = ? and dimension.key = ? and score.selected_level = ?
                """, runId, personId, dimensionKey, level);
    }

    private long count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Long.class, arguments);
    }

    @Test
    void scoresEveryPersonInBothDimensionsAtThreeLevels() throws Exception {
        JsonNode run = run(QUARTER_ARGUMENTS);

        assertEquals("succeeded", run.get("status").stringValue());
        long runId = run.get("id").longValue();
        assertEquals(2, run.get("counts").get("persons").longValue());
        assertEquals(12, run.get("counts").get("scores_written").longValue());
        assertEquals(12, count("select count(*) from dimension_score where job_run_id = ?", runId));
        assertEquals(
                12,
                count("select count(*) from dimension_score where quarter = '2026-Q3' and calculated_at is not null"));
        Map<String, Object> adaCoding = scoreRow(runId, ada, CODING, "L1");
        assertEquals("T3", adaCoding.get("working_tier"));
        assertEquals(2, adaCoding.get("score"));
        assertTrue(adaCoding.get("trail").toString().contains("\"" + CODING + "\""));
        Map<String, Object> adaStandards = scoreRow(runId, ada, STANDARDS, "L2");
        assertNull(adaStandards.get("working_tier"));
        assertEquals(0, adaStandards.get("score"));
        assertTrue(adaStandards.get("gates").toString().contains("\"G4\""));
        assertEquals(
                1,
                count(
                        "select count(*) from dimension_score where person_id = ? and dimension_id = "
                                + "(select id from dimension where key = ?) and selected_level = 'L3' and job_run_id = ?",
                        grace,
                        CODING,
                        runId));
    }

    @Test
    void aNewPresenceThresholdChangesTheWorkingTier() throws Exception {
        long firstRun = run(QUARTER_ARGUMENTS).get("id").longValue();
        assertEquals("T3", scoreRow(firstRun, ada, CODING, "L1").get("working_tier"));

        insertSetting("scoring.presence_threshold", "{\"numerator\":3,\"denominator\":4}");
        long secondRun = run(QUARTER_ARGUMENTS).get("id").longValue();

        assertEquals("T1", scoreRow(secondRun, ada, CODING, "L1").get("working_tier"));
    }

    @Test
    void aNewGateLimitChangesTheGateResult() throws Exception {
        long firstRun = run(QUARTER_ARGUMENTS).get("id").longValue();
        assertEquals("FAR_MISS", gateOutcome(firstRun, "G1"));

        insertSetting(
                "scoring.gate_limits",
                "{\"G1\":{\"passWithinPoints\":10,\"farMissBeyondPoints\":60},"
                        + "\"G2\":{\"passWithinPoints\":10,\"farMissBeyondPoints\":25}}");
        long secondRun = run(QUARTER_ARGUMENTS).get("id").longValue();

        assertEquals("CLOSE_MISS", gateOutcome(secondRun, "G1"));
    }

    @Test
    void switchingG2OnInSettingsLowersTheScore() throws Exception {
        rows.itemTier(rows.testChange(ada, 3, "2026-07-03"), "T3");
        for (int number = 6; number <= 9; number++) {
            long author = number <= 7 ? ada : grace;
            rows.pullRequest(number, 1, number <= 7 ? 11 : 12, number <= 7 ? "ada" : "grace", "2026-07-1" + number);
            rows.item("bug_fix", author, "2026-07-1" + number, false, null, number);
        }
        rows.testChange(grace, 8, "2026-07-18");
        rows.testChange(grace, 9, "2026-07-19");
        insertSetting(
                "scoring.gate_limits",
                "{\"G1\":{\"passWithinPoints\":100,\"farMissBeyondPoints\":100},"
                        + "\"G2\":{\"passWithinPoints\":10,\"farMissBeyondPoints\":25}}");
        long unscoredRun = run(QUARTER_ARGUMENTS).get("id").longValue();

        assertEquals("FAR_MISS", gateOutcome(unscoredRun, "G2"));
        assertFalse(gateScored(unscoredRun, "G2"));
        assertEquals(2, scoreRow(unscoredRun, ada, STANDARDS, "L1").get("score"));
        assertTrue(scoreRow(unscoredRun, ada, STANDARDS, "L1")
                .get("trail")
                .toString()
                .contains("G2 not scored yet"));

        insertSetting("scoring.gate_scored", "{\"G1\":true,\"G2\":true}");
        long scoredRun = run(QUARTER_ARGUMENTS).get("id").longValue();

        assertEquals("FAR_MISS", gateOutcome(scoredRun, "G2"));
        assertTrue(gateScored(scoredRun, "G2"));
        assertEquals(0, scoreRow(scoredRun, ada, STANDARDS, "L1").get("score"));
        assertTrue(scoreRow(scoredRun, ada, STANDARDS, "L1")
                .get("trail")
                .toString()
                .contains("check: G2 far"));
    }

    private boolean gateScored(long runId, String gate) throws Exception {
        String gates = scoreRow(runId, ada, STANDARDS, "L1").get("gates").toString();
        for (JsonNode result : JSON.readTree(gates)) {
            if (gate.equals(result.get("gate").stringValue())) {
                return result.get("scored").booleanValue();
            }
        }
        throw new AssertionError("No result of gate " + gate + " in " + gates);
    }

    private String gateOutcome(long runId, String gate) throws Exception {
        String gates = scoreRow(runId, ada, STANDARDS, "L1").get("gates").toString();
        for (JsonNode result : JSON.readTree(gates)) {
            if (gate.equals(result.get("gate").stringValue())) {
                return result.get("outcome").stringValue();
            }
        }
        throw new AssertionError("No result of gate " + gate + " in " + gates);
    }

    @Test
    void aSecondRunAddsRowsAndLeavesTheFirstRunUnchanged() throws Exception {
        long firstRun = run(QUARTER_ARGUMENTS).get("id").longValue();
        List<Map<String, Object>> before =
                jdbc.queryForList("select * from dimension_score where job_run_id = ? order by id", firstRun);

        long secondRun = run(QUARTER_ARGUMENTS).get("id").longValue();

        assertEquals(12, count("select count(*) from dimension_score where job_run_id = ?", secondRun));
        assertEquals(24, count("select count(*) from dimension_score"));
        assertEquals(
                before, jdbc.queryForList("select * from dimension_score where job_run_id = ? order by id", firstRun));
    }

    @Test
    void aFailingRunWritesNoRows() throws Exception {
        JsonNode run = run("{\"quarter\":\"2025-Q1\"}");

        assertEquals("failed", run.get("status").stringValue());
        assertTrue(run.get("errorMessage").stringValue().contains("scoring.presence_threshold"));
        assertEquals(0, count("select count(*) from dimension_score"));
    }

    @Test
    void aRunThatFailsAfterSomeInsertsWritesNoRowsAndNoCount() throws Exception {
        jdbc.execute("""
                create function fail_after_first_score() returns trigger as $$
                begin
                    if exists (select 1 from dimension_score) then
                        raise exception 'second score refused';
                    end if;
                    return new;
                end $$ language plpgsql
                """);
        jdbc.execute("""
                create trigger fail_after_first_score before insert on dimension_score
                for each row execute function fail_after_first_score()
                """);
        try {
            JsonNode run = run(QUARTER_ARGUMENTS);

            assertEquals("failed", run.get("status").stringValue());
            assertTrue(run.get("errorMessage").stringValue().contains("second score refused"));
            assertEquals(0, count("select count(*) from dimension_score"));
            assertNull(run.get("counts").get("scores_written"));
        } finally {
            jdbc.execute("drop trigger fail_after_first_score on dimension_score");
            jdbc.execute("drop function fail_after_first_score()");
        }
    }

    @Test
    void aMissingOrWrongQuarterIsRefusedAndNothingIsRecorded() throws Exception {
        HttpResponse<String> missing = jobs.start("dimension-scoring", "{}");
        HttpResponse<String> wrong = jobs.start("dimension-scoring", "{\"quarter\":\"2026-Q5\"}");
        HttpResponse<String> unknownArgument =
                jobs.start("dimension-scoring", "{\"quarter\":\"2026-Q3\",\"level\":\"L1\"}");

        assertEquals(400, missing.statusCode());
        assertTrue(missing.body().contains("quarter"));
        assertEquals(400, wrong.statusCode());
        assertTrue(wrong.body().contains("2026-Q5"));
        assertEquals(400, unknownArgument.statusCode());
        assertEquals(0, count("select count(*) from job_run"));
    }
}
