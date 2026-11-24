package com.dsinnovators.ei.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
class DeveloperAnalyticsControllerTest extends DatabaseTest {

    @Value("${local.server.port}")
    private int port;

    private JobEndpointClient client;
    private long ada;

    @BeforeEach
    void createDeveloperWithOneMergedPullRequest() {
        client = new JobEndpointClient(port);
        ada = jdbc.queryForObject("insert into person (display_name) values ('Ada') returning id", Long.class);
        jdbc.update("""
                insert into person_identity (person_id, data_source_id, external_id, username)
                values (?, (select id from data_source where key = 'github'), '11', 'ada')
                """, ada);
        jdbc.update("""
                insert into raw_record (data_source_id, record_kind, external_id, version, url, content_hash, payload, fetched_at)
                values ((select id from data_source where key = 'github'), 'pull_request', 'opf/openproject#7', 1,
                        'https://github.com/opf/openproject/pull/7', 'hash',
                        '{"number":7,"mergedAt":"2026-07-02T10:00:00Z","author":{"login":"ada","__typename":"User","databaseId":11}}'::jsonb,
                        now())
                """);
    }

    private HttpResponse<String> analytics(String query) throws Exception {
        return client.get("/api/developers/" + ada + "/analytics" + query);
    }

    @Test
    void returnsTheFiveAnalyticsAndTheHygieneNumberOfTheQuarter() throws Exception {
        HttpResponse<String> response = analytics("?quarter=2026-Q3");

        assertEquals(200, response.statusCode());
        JsonNode body = JobEndpointClient.json(response);
        assertEquals(ada, body.get("person").get("personId").longValue());
        assertEquals("Ada", body.get("person").get("displayName").stringValue());
        assertEquals("2026-Q3", body.get("quarter").stringValue());
        JsonNode notJudgedYet = body.get("workClassification").get("slices").get(0);
        assertEquals("not_judged_yet", notJudgedYet.get("name").stringValue());
        assertEquals(1.0, notJudgedYet.get("share").doubleValue());
        assertEquals(
                7, notJudgedYet.get("count").get("pullRequestNumbers").get(0).intValue());
        assertTrue(notJudgedYet.get("count").get("evidenceItemIds").isArray());
        assertTrue(body.get("workComplexity").has("person"));
        assertTrue(body.get("workComplexity").has("restOfRepository"));
        assertTrue(body.get("workLevel").get("person").has("shareAtT2OrAbove"));
        assertTrue(body.get("workLevel").has("line"));
        assertTrue(body.get("reviewParticipation").has("pullRequestsReviewed"));
        assertTrue(body.get("reviewFindingsReceived").has("pullRequests"));
        assertTrue(body.get("testingContribution").has("featureAndFixPullRequests"));
        assertEquals(1, body.get("hygiene").get("total").longValue());
    }

    @Test
    void answers404ForAnUnknownPerson() throws Exception {
        HttpResponse<String> response = client.get("/api/developers/999999/analytics?quarter=2026-Q3");

        assertEquals(404, response.statusCode());
        assertTrue(JobEndpointClient.json(response).get("detail").stringValue().contains("999999"));
    }

    @Test
    void answers400ForAWrongQuarter() throws Exception {
        HttpResponse<String> response = analytics("?quarter=2026-Q5");

        assertEquals(400, response.statusCode());
        assertTrue(JobEndpointClient.json(response).get("detail").stringValue().contains("2026-Q5"));
    }

    @Test
    void answers400ForAMissingQuarter() throws Exception {
        HttpResponse<String> response = analytics("");

        assertEquals(400, response.statusCode());
        assertTrue(JobEndpointClient.json(response).get("detail").stringValue().contains("quarter"));
    }
}
