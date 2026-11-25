package com.dsinnovators.ei.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.JobEndpointClient;
import com.dsinnovators.ei.review.BlindCheckFixture;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReasonMarkControllerTest extends DatabaseTest {

    private static final String MARK =
            "{\"reasonRight\":true,\"problemMarks\":[{\"name\":\"Cache invalidation\",\"verdict\":\"right\"}],\"note\":\"fine\"}";

    @Value("${local.server.port}")
    private int port;

    private JobEndpointClient client;
    private long lowItem;
    private long outsideTheSample;

    @BeforeEach
    void pickASampleOfThree() throws Exception {
        client = new JobEndpointClient(port);
        BlindCheckFixture stored = new BlindCheckFixture(jdbc);
        jdbc.update("update setting set value = '1'::jsonb where key = 'blind_check.items_per_complexity'");
        lowItem = stored.judgedItem("low");
        stored.judgedItem("medium");
        stored.judgedItem("high");
        client.post("/api/blind-check/sample");
        outsideTheSample = stored.judgedItem("low");
    }

    private HttpResponse<String> mark(long itemId, String body) throws Exception {
        return client.put("/api/evidence-items/" + itemId + "/reason-mark", body);
    }

    @Test
    void storesTheMarkAndShowsItOnTheItem() throws Exception {
        client.postCsv("/api/blind-check/labels", "item_id,complexity,note\n" + lowItem + ",low,\n");

        HttpResponse<String> response = mark(lowItem, MARK);

        assertEquals(200, response.statusCode());
        JsonNode body = JobEndpointClient.json(response);
        assertTrue(body.get("reasonRight").booleanValue());
        assertEquals("right", body.get("problemMarks").get(0).get("verdict").stringValue());
        assertEquals("fine", body.get("note").stringValue());
        JsonNode shown = JobEndpointClient.json(client.get("/api/evidence-items/" + lowItem));
        assertEquals(body, shown.get("blindCheck").get("reasonMark"));
    }

    @Test
    void answers404OutsideTheSampleAnd409WithoutALabelAnd400ForAWrongList() throws Exception {
        assertEquals(404, mark(outsideTheSample, MARK).statusCode());
        assertEquals(409, mark(lowItem, MARK).statusCode());
        client.postCsv("/api/blind-check/labels", "item_id,complexity,note\n" + lowItem + ",low,\n");

        HttpResponse<String> wrongList = mark(lowItem, "{\"reasonRight\":true,\"problemMarks\":[]}");
        HttpResponse<String> missingField = mark(lowItem, "{\"problemMarks\":[]}");

        assertEquals(400, wrongList.statusCode());
        assertTrue(JobEndpointClient.json(wrongList).get("detail").stringValue().contains("1 problems"));
        assertEquals(400, missingField.statusCode());
        assertTrue(
                JobEndpointClient.json(missingField).get("detail").stringValue().contains("reasonRight"));
    }
}
