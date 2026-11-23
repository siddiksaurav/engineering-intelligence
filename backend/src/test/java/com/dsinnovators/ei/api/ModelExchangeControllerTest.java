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
class ModelExchangeControllerTest extends DatabaseTest {

    private static final String REQUESTS = "/api/model-exchange/requests";
    private static final String ANSWER = """
            {"answer":{"workType":"fix"},"answeredBy":"claude-sonnet-5-5 via claude-code"}
            """;

    @Value("${local.server.port}")
    private int port;

    private JobEndpointClient client;

    @BeforeEach
    void storeWaitingRequests() {
        client = new JobEndpointClient(port);
        insertWaiting("hash-1", "pull-request-digest/v1", "2026-11-23T10:00:00Z");
        insertWaiting("hash-2", "review-comments/v1", "2026-11-23T10:01:00Z");
    }

    private void insertWaiting(String hash, String promptVersion, String askedAt) {
        jdbc.update("""
                insert into model_exchange
                    (request_hash, prompt_version, schema_name, instructions, input, json_schema, status, asked_at)
                values (?, ?, 'schema', 'Do this', 'On this', '{"type":"object"}'::jsonb, 'waiting', ?::timestamptz)
                """, hash, promptVersion, askedAt);
    }

    @Test
    void listsTheWaitingRequestsWithEverythingNeededToAnswerThem() throws Exception {
        HttpResponse<String> response = client.get(REQUESTS);

        assertEquals(200, response.statusCode());
        JsonNode requests = JobEndpointClient.json(response);
        assertEquals(2, requests.size());
        JsonNode first = requests.get(0);
        assertEquals("hash-1", first.get("requestHash").stringValue());
        assertEquals("pull-request-digest/v1", first.get("promptVersion").stringValue());
        assertEquals("Do this", first.get("instructions").stringValue());
        assertEquals("On this", first.get("input").stringValue());
        assertEquals("object", first.get("jsonSchema").get("type").stringValue());
    }

    @Test
    void filtersByPromptVersionAndLimits() throws Exception {
        JsonNode reviews = JobEndpointClient.json(client.get(REQUESTS + "?promptVersion=review-comments/v1"));
        JsonNode one = JobEndpointClient.json(client.get(REQUESTS + "?limit=1"));

        assertEquals(1, reviews.size());
        assertEquals("hash-2", reviews.get(0).get("requestHash").stringValue());
        assertEquals(1, one.size());
    }

    @Test
    void refusesALimitOutsideItsRange() throws Exception {
        assertEquals(400, client.get(REQUESTS + "?limit=0").statusCode());
        assertEquals(400, client.get(REQUESTS + "?limit=1000").statusCode());
    }

    @Test
    void takesAnAnswerAndTheRequestNoLongerWaits() throws Exception {
        HttpResponse<String> response = client.put(REQUESTS + "/hash-1/answer", ANSWER);

        assertEquals(204, response.statusCode());
        assertEquals(1, JobEndpointClient.json(client.get(REQUESTS)).size());
        assertEquals(
                "claude-sonnet-5-5 via claude-code",
                jdbc.queryForObject(
                        "select answered_by from model_exchange where request_hash = 'hash-1'", String.class));
    }

    @Test
    void refusesAnAnswerToAnUnknownRequest() throws Exception {
        HttpResponse<String> response = client.put(REQUESTS + "/unknown/answer", ANSWER);

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("unknown"), response.body());
    }

    @Test
    void refusesASecondAnswer() throws Exception {
        client.put(REQUESTS + "/hash-1/answer", ANSWER);

        assertEquals(409, client.put(REQUESTS + "/hash-1/answer", ANSWER).statusCode());
    }

    @Test
    void refusesAnAnswerThatIsNoObjectOrNamesNoModel() throws Exception {
        assertEquals(
                400,
                client.put(REQUESTS + "/hash-1/answer", "{\"answer\":[1],\"answeredBy\":\"x\"}")
                        .statusCode());
        assertEquals(
                400,
                client.put(REQUESTS + "/hash-1/answer", "{\"answer\":{\"a\":1}}")
                        .statusCode());
    }
}
