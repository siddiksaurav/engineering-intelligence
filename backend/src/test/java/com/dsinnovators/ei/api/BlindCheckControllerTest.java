package com.dsinnovators.ei.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.JobEndpointClient;
import com.dsinnovators.ei.review.BlindCheckFixture;
import java.net.http.HttpResponse;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BlindCheckControllerTest extends DatabaseTest {

    @Value("${local.server.port}")
    private int port;

    private JobEndpointClient client;
    private BlindCheckFixture stored;

    @BeforeEach
    void setUp() {
        client = new JobEndpointClient(port);
        stored = new BlindCheckFixture(jdbc);
        jdbc.update("update setting set value = '2'::jsonb where key = 'blind_check.items_per_complexity'");
    }

    private void judgedItems(String complexity, int count) {
        IntStream.range(0, count).forEach(ignored -> stored.judgedItem(complexity));
    }

    @Test
    void picksTheSampleOnceAndAnswersWithItsPositionsAndItemsOnly() throws Exception {
        judgedItems("low", 3);
        judgedItems("medium", 2);
        judgedItems("high", 2);

        HttpResponse<String> first = client.post("/api/blind-check/sample");
        HttpResponse<String> second = client.post("/api/blind-check/sample");

        assertEquals(200, first.statusCode());
        JsonNode body = JobEndpointClient.json(first);
        assertEquals(6, body.size());
        assertEquals(1, body.get(0).get("position").intValue());
        assertTrue(body.get(0).get("itemId").isNumber());
        assertFalse(first.body().contains("complexity"), first.body());
        assertEquals(first.body(), second.body());
    }

    @Test
    void refusesTheSampleNamingTheComplexityAndTheCount() throws Exception {
        judgedItems("low", 2);
        judgedItems("medium", 1);
        judgedItems("high", 2);

        HttpResponse<String> response = client.post("/api/blind-check/sample");

        assertEquals(409, response.statusCode());
        String detail = JobEndpointClient.json(response).get("detail").stringValue();
        assertTrue(detail.contains("medium") && detail.contains("1") && detail.contains("2"), detail);
    }
}
