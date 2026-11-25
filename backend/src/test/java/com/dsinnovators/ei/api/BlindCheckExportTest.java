package com.dsinnovators.ei.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.JobEndpointClient;
import com.dsinnovators.ei.review.BlindCheckFixture;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BlindCheckExportTest extends DatabaseTest {

    private static final String HEADER = "item_id,developer,title,work_item,pull_requests,complexity,note";

    @Value("${local.server.port}")
    private int port;

    private JobEndpointClient client;
    private BlindCheckFixture stored;

    @BeforeEach
    void setUp() {
        client = new JobEndpointClient(port);
        stored = new BlindCheckFixture(jdbc);
        jdbc.update("update setting set value = '1'::jsonb where key = 'blind_check.items_per_complexity'");
    }

    private List<String> exportedLines() throws Exception {
        assertEquals(200, client.post("/api/blind-check/sample").statusCode());
        HttpResponse<String> response = client.get("/api/blind-check/sample.csv");
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("text/csv"));
        return response.body().lines().toList();
    }

    @Test
    void theFileHoldsNoWordOfAnyJudgment() throws Exception {
        stored.judgedItem("low");
        stored.judgedItem("medium");
        stored.judgedItem("high");

        List<String> lines = exportedLines();

        assertEquals(HEADER, lines.getFirst());
        assertEquals(4, lines.size());
        String file = String.join("\n", lines).toLowerCase(Locale.ROOT);
        for (String wordOfAJudgment : List.of(
                "low",
                "medium",
                "high",
                "very_high",
                "answer_exists",
                "routine",
                "cache invalidation",
                "reason for",
                "gpt-test",
                "feature-work-complexity")) {
            assertFalse(file.contains(wordOfAJudgment), () -> "The file holds '" + wordOfAJudgment + "':\n" + file);
        }
        lines.stream().skip(1).forEach(line -> assertTrue(line.endsWith(",,"), line));
    }

    @Test
    void writesOneRowPerItemWithItsLinksAndQuotesWhatNeedsQuotes() throws Exception {
        long withWorkItem = stored.item("""
                {"feature":{"number":7,"title":"Say \\"hi\\", then wait","type":"Feature","url":"https://op.test/wp/7"},
                 "tasks":[],
                 "pullRequests":[{"externalId":"1","number":71,"title":"One","url":"https://gh.test/pull/71","mergedAt":"2026-07-01T10:00:00Z"},
                                 {"externalId":"2","number":72,"title":"Two","url":"https://gh.test/pull/72","mergedAt":"2026-07-02T10:00:00Z"}],
                 "doneAt":"2026-07-03T10:00:00Z"}
                """);
        long withoutWorkItem = stored.item("""
                {"noWorkItem":true,"tasks":[],
                 "pullRequests":[{"externalId":"3","number":73,"title":"Line one\\nline two","url":"https://gh.test/pull/73","mergedAt":"2026-07-01T10:00:00Z"}]}
                """);
        stored.judgment(withWorkItem, "low", null);
        stored.judgment(withoutWorkItem, "medium", null);
        stored.judgedItem("high");

        assertEquals(200, client.post("/api/blind-check/sample").statusCode());
        String file = client.get("/api/blind-check/sample.csv").body();

        String quotedRow =
                "%d,Ada,\"Say \"\"hi\"\", then wait\",https://op.test/wp/7,https://gh.test/pull/71 https://gh.test/pull/72,,\r\n"
                        .formatted(withWorkItem);
        String noWorkItemRow =
                "%d,Ada,\"Line one\nline two\",,https://gh.test/pull/73,,\r\n".formatted(withoutWorkItem);
        assertTrue(file.startsWith(HEADER + "\r\n"), file);
        assertTrue(file.contains(quotedRow), file);
        assertTrue(file.contains(noWorkItemRow), file);
        assertEquals(4, file.split("\r\n", -1).length - 1, file);
    }

    @Test
    void anEmptySampleExportsTheHeaderOnly() throws Exception {
        HttpResponse<String> response = client.get("/api/blind-check/sample.csv");

        assertEquals(200, response.statusCode());
        assertEquals(HEADER + "\r\n", response.body());
    }
}
