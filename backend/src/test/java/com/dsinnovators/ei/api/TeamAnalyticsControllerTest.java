package com.dsinnovators.ei.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.JobEndpointClient;
import com.dsinnovators.ei.analytics.AnalyticsRows;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TeamAnalyticsControllerTest extends DatabaseTest {

    @Value("${local.server.port}")
    private int port;

    private JobEndpointClient client;
    private long ada;
    private long grace;

    @BeforeEach
    void twoDevelopersWithJudgedWork() {
        client = new JobEndpointClient(port);
        AnalyticsRows rows = new AnalyticsRows(jdbc);
        ada = rows.person(11, "ada");
        grace = rows.person(12, "grace");
        rows.pullRequest(1, 1, 11, "ada", "2026-07-02T10:00:00Z");
        rows.pullRequest(2, 1, 11, "ada", "2026-07-03T10:00:00Z");
        rows.pullRequest(3, 1, 12, "grace", "2026-07-04T10:00:00Z");
        rows.digest(1, "feature", null);
        rows.digest(3, "feature", null);
        rows.link(1, "resolved");
        rows.complexity(rows.item("feature_work", ada, "2026-07-02", false, null, 1), "low", "T1", null);
        rows.complexity(rows.item("feature_work", ada, "2026-07-03", false, null, 2), "high", "T3", null);
        rows.item("feature_work", ada, "2026-07-03", false, null, 2);
        rows.complexity(rows.item("feature_work", grace, "2026-07-04", false, null, 3), "low", "T1", null);
    }

    private JsonNode read(String path) throws Exception {
        return JobEndpointClient.json(client.get(path));
    }

    private static long countOf(JsonNode distribution, String name) {
        for (JsonNode slice : distribution.get("slices")) {
            if (slice.get("name").stringValue().equals(name)) {
                return slice.get("count").get("count").longValue();
            }
        }
        return 0;
    }

    @Test
    void teamNumbersEqualTheSumOfThePersons() throws Exception {
        JsonNode team = read("/api/team/analytics?quarter=2026-Q3");
        JsonNode adaNumbers = read("/api/developers/" + ada + "/analytics?quarter=2026-Q3");
        JsonNode graceNumbers = read("/api/developers/" + grace + "/analytics?quarter=2026-Q3");

        assertEquals("2026-Q3", team.get("quarter").stringValue());
        for (String bar : new String[] {"low", "medium", "high", "very_high", "not_judged_yet"}) {
            assertEquals(
                    countOf(adaNumbers.get("workComplexity").get("person"), bar)
                            + countOf(graceNumbers.get("workComplexity").get("person"), bar),
                    countOf(team.get("workComplexity").get("distribution"), bar),
                    bar);
        }
        for (String workType : new String[] {"feature", "not_judged_yet"}) {
            assertEquals(
                    countOf(adaNumbers.get("workClassification").get("distribution"), workType)
                            + countOf(graceNumbers.get("workClassification").get("distribution"), workType),
                    countOf(team.get("workClassification").get("distribution"), workType),
                    workType);
        }
        assertEquals(
                3, team.get("workClassification").get("coverage").get("total").longValue());
        assertEquals(
                2, team.get("workClassification").get("coverage").get("judged").longValue());
        assertEquals(4, team.get("workComplexity").get("coverage").get("total").longValue());
        assertEquals(3, team.get("workComplexity").get("coverage").get("judged").longValue());
        assertEquals(3, team.get("hygiene").get("total").longValue());
        assertEquals(1, team.get("hygiene").get("linked").get("count").longValue());
    }

    @Test
    void answers400ForAWrongQuarter() throws Exception {
        HttpResponse<String> response = client.get("/api/team/analytics?quarter=2026-Q5");

        assertEquals(400, response.statusCode());
        assertTrue(JobEndpointClient.json(response).get("detail").stringValue().contains("2026-Q5"));
    }

    @Test
    void answers400ForAMissingQuarter() throws Exception {
        HttpResponse<String> response = client.get("/api/team/analytics");

        assertEquals(400, response.statusCode());
        assertTrue(JobEndpointClient.json(response).get("detail").stringValue().contains("quarter"));
    }
}
