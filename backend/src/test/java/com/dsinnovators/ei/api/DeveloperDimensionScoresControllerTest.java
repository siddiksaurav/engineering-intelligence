package com.dsinnovators.ei.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.JobEndpointClient;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeveloperDimensionScoresControllerTest extends DatabaseTest {

    private static final String CODING = "coding_and_code_quality";
    private static final String STANDARDS = "engineering_standards_and_system_health";

    @Value("${local.server.port}")
    private int port;

    private JobEndpointClient client;
    private long ada;
    private long grace;

    @BeforeEach
    void storeTwoDevelopers() {
        client = new JobEndpointClient(port);
        ada = jdbc.queryForObject("insert into person (display_name) values ('Ada') returning id", Long.class);
        grace = jdbc.queryForObject("insert into person (display_name) values ('Grace') returning id", Long.class);
    }

    private long run(String status) {
        return jdbc.queryForObject("""
                insert into job_run (job_name, arguments, started_by, status, started_at, finished_at, counts)
                values ('dimension-scoring', '{}'::jsonb, 'test', ?, now(), now(), '{}'::jsonb) returning id
                """, Long.class, status);
    }

    private void score(
            long runId, long personId, String dimensionKey, String quarter, String level, String tier, int score) {
        jdbc.update("""
                insert into dimension_score (job_run_id, person_id, dimension_id, quarter, selected_level, working_tier,
                                             score, gates, trail, calculated_at)
                values (?, ?, (select id from dimension where key = ?), ?, ?, ?, ?,
                        '[{"gate":"G1","outcome":"PASS","scored":true}]'::jsonb,
                        ('{"dimensionKey":"' || ? || '","notes":["held: G1"],"finalScore":' || ? || '}')::jsonb,
                        now())
                """, runId, personId, dimensionKey, quarter, level, tier, score, dimensionKey, score);
    }

    private HttpResponse<String> scores(long personId, String query) throws Exception {
        return client.get("/api/developers/" + personId + "/dimension-scores" + query);
    }

    @Test
    void returnsBothDimensionsOfTheNewestRunAtTheSelectedLevel() throws Exception {
        long olderRun = run("succeeded");
        score(olderRun, ada, CODING, "2026-Q3", "L2", "T1", 0);
        long newestRun = run("succeeded");
        score(newestRun, ada, CODING, "2026-Q3", "L1", "T2", 2);
        score(newestRun, ada, CODING, "2026-Q3", "L2", "T2", 1);
        score(newestRun, ada, STANDARDS, "2026-Q3", "L2", null, 0);
        long failedRun = run("failed");
        score(failedRun, ada, CODING, "2026-Q3", "L2", "T3", 2);
        long otherQuarterRun = run("succeeded");
        score(otherQuarterRun, ada, CODING, "2026-Q4", "L2", "T3", 2);

        HttpResponse<String> response = scores(ada, "?quarter=2026-Q3&level=L2");

        assertEquals(200, response.statusCode());
        JsonNode body = JobEndpointClient.json(response);
        assertEquals(ada, body.get("person").get("personId").longValue());
        assertEquals("Ada", body.get("person").get("displayName").stringValue());
        assertEquals("2026-Q3", body.get("quarter").stringValue());
        assertEquals("L2", body.get("level").stringValue());
        assertTrue(body.get("scored").booleanValue());
        assertEquals(newestRun, body.get("jobRunId").longValue());
        assertFalse(body.get("calculatedAt").stringValue().isBlank());
        JsonNode dimensions = body.get("dimensions");
        assertEquals(2, dimensions.size());
        JsonNode coding = dimensions.get(0);
        assertEquals(CODING, coding.get("key").stringValue());
        assertEquals("Coding and code quality", coding.get("name").stringValue());
        assertEquals("T2", coding.get("workingTier").stringValue());
        assertEquals(1, coding.get("score").intValue());
        assertEquals("PASS", coding.get("gates").get(0).get("outcome").stringValue());
        assertTrue(coding.get("gates").get(0).get("scored").booleanValue());
        assertEquals("held: G1", coding.get("notes").get(0).stringValue());
        assertEquals(1, coding.get("trail").get("finalScore").intValue());
        JsonNode standards = dimensions.get(1);
        assertEquals(STANDARDS, standards.get("key").stringValue());
        assertTrue(standards.get("workingTier").isNull());
    }

    @Test
    void aPersonWithoutRowsInTheQuarterIsNotScored() throws Exception {
        score(run("succeeded"), ada, CODING, "2026-Q3", "L1", "T1", 1);

        JsonNode body = JobEndpointClient.json(scores(grace, "?quarter=2026-Q3&level=L1"));

        assertFalse(body.get("scored").booleanValue());
        assertTrue(body.get("jobRunId").isNull());
        assertEquals(0, body.get("dimensions").size());
    }

    @Test
    void aQuarterWithoutASucceededRunIsNotScored() throws Exception {
        score(run("failed"), ada, CODING, "2026-Q3", "L1", "T1", 1);

        JsonNode body = JobEndpointClient.json(scores(ada, "?quarter=2026-Q3&level=L1"));

        assertFalse(body.get("scored").booleanValue());
        assertEquals(0, body.get("dimensions").size());
    }

    @Test
    void answers404ForAnUnknownPerson() throws Exception {
        HttpResponse<String> response = scores(999999, "?quarter=2026-Q3&level=L1");

        assertEquals(404, response.statusCode());
        assertTrue(JobEndpointClient.json(response).get("detail").stringValue().contains("999999"));
    }

    @Test
    void answers400ForAMissingOrWrongQuarterOrLevel() throws Exception {
        assertRefused(scores(ada, "?level=L1"), "quarter");
        assertRefused(scores(ada, "?quarter=2026-Q5&level=L1"), "2026-Q5");
        assertRefused(scores(ada, "?quarter=2026-Q3"), "level");
        assertRefused(scores(ada, "?quarter=2026-Q3&level=L4"), "L4");
    }

    private static void assertRefused(HttpResponse<String> response, String namedInDetail) {
        assertEquals(400, response.statusCode());
        assertTrue(JobEndpointClient.json(response).get("detail").stringValue().contains(namedInDetail));
    }
}
