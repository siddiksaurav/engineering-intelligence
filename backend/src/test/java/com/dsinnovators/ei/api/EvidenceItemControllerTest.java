package com.dsinnovators.ei.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.JobEndpointClient;
import java.net.http.HttpResponse;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EvidenceItemControllerTest extends DatabaseTest {

    private static final String FEATURE_FIELDS = """
            {"feature":{"number":10,"title":"Single sign-on","type":"Feature","url":"https://op.test/wp/10"},
             "tasks":[],
             "pullRequests":[{"externalId":"1","number":101,"title":"Add SSO","url":"https://gh.test/pull/101","mergedAt":"2026-07-01T10:00:00Z"}],
             "doneAt":"2026-07-03T10:00:00Z"}
            """;
    private static final String BUG_FIELDS = """
            {"bug":{"number":20,"title":"Login fails","url":"https://op.test/wp/20","priority":"High"},
             "underFeature":null,
             "pullRequests":[{"externalId":"2","number":102,"title":"Fix login","url":"https://gh.test/pull/102","mergedAt":"2026-10-05T10:00:00Z"}],
             "doneAt":"2026-10-05T10:00:00Z"}
            """;
    private static final String PULL_REQUEST_FIELDS = """
            {"pullRequest":{"externalId":"4","number":104,"title":"Add spec","url":"https://gh.test/pull/104","mergedAt":"2026-10-05T10:00:00Z"},
             "testKind":"integration"}
            """;

    @Value("${local.server.port}")
    private int port;

    private JobEndpointClient client;
    private long ada;
    private long grace;

    @BeforeEach
    void createPersons() {
        client = new JobEndpointClient(port);
        ada = person("Ada");
        grace = person("Grace");
    }

    private long person(String name) {
        return jdbc.queryForObject("insert into person (display_name) values (?) returning id", Long.class, name);
    }

    private long item(
            long personId, String key, String type, String date, String fields, boolean withdrawn, Long supersedes) {
        return jdbc.queryForObject("""
                insert into evidence_item (item_key, evidence_type, person_id, occurred_on, source_url, fields,
                                           content_hash, withdrawn, supersedes_id, built_at)
                values (?, ?, ?, ?::date, 'https://source.test/' || ?, ?::jsonb, 'hash', ?, ?, now())
                returning id
                """, Long.class, key, type, personId, date, key, fields, withdrawn, supersedes);
    }

    private long judgment(long itemId, String kind, String tier, Long supersedes) {
        return jdbc.queryForObject("""
                insert into judgment (kind, evidence_item_id, value, tier, reason, facts, judged_by, model,
                                      prompt_version, input_hash, supersedes_id, judged_at)
                values (?, ?, '{}'::jsonb, ?, 'Because', '[{"fact":"f","link":"https://gh.test/pull/101"}]'::jsonb,
                        'model', 'm', 'v1', 'hash' || gen_random_uuid(), ?, now())
                returning id
                """, Long.class, kind, itemId, tier, supersedes);
    }

    private HttpResponse<String> summaries(String ids) throws Exception {
        return client.get("/api/evidence-items?ids=" + ids);
    }

    @Test
    void returnsTheSummaryOfEachGivenIdWithItsPersonAndCurrentTier() throws Exception {
        long feature = item(ada, "feature_work:10:1", "feature_work", "2026-07-03", FEATURE_FIELDS, false, null);
        long testChange = item(grace, "test_change:104", "test_change", "2026-10-05", PULL_REQUEST_FIELDS, false, null);
        long bugFix = item(grace, "bug_fix:20:1", "bug_fix", "2026-10-05", BUG_FIELDS, false, null);
        long firstComplexity = judgment(feature, "feature_work_complexity", "T1", null);
        judgment(feature, "feature_work_complexity", "T3", firstComplexity);
        judgment(testChange, "item_tier", "T2", null);
        judgment(bugFix, "item_tier", "T1", null);

        HttpResponse<String> response = summaries(testChange + "," + feature + "," + bugFix);

        assertEquals(200, response.statusCode());
        JsonNode body = JobEndpointClient.json(response);
        assertEquals(3, body.size());
        JsonNode first = body.get(0);
        assertEquals(testChange, first.get("id").longValue());
        assertEquals("test_change", first.get("type").stringValue());
        assertEquals("2026-10-05", first.get("date").stringValue());
        assertEquals("2026-Q4", first.get("quarter").stringValue());
        assertEquals("Add spec", first.get("title").stringValue());
        assertEquals(
                "https://source.test/test_change:104", first.get("sourceUrl").stringValue());
        assertEquals(
                "Pull request #104 on GitHub",
                first.get("links").get(0).get("label").stringValue());
        assertEquals(grace, first.get("person").get("personId").longValue());
        assertEquals("Grace", first.get("person").get("displayName").stringValue());
        assertEquals("T2", first.get("tier").stringValue());
        assertTrue(first.get("current").booleanValue());
        assertTrue(first.get("currentItemId").isNull());
        JsonNode second = body.get(1);
        assertEquals(feature, second.get("id").longValue());
        assertEquals("Single sign-on", second.get("title").stringValue());
        assertEquals("T3", second.get("tier").stringValue());
        assertEquals("Ada", second.get("person").get("displayName").stringValue());
        JsonNode third = body.get(2);
        assertEquals(bugFix, third.get("id").longValue());
        assertTrue(third.get("tier").isNull());
    }

    @Test
    void aSupersededRowIsNotCurrentAndNamesTheCurrentRow() throws Exception {
        long old = item(ada, "feature_work:10:1", "feature_work", "2026-07-03", FEATURE_FIELDS, false, null);
        long replacement = item(ada, "feature_work:10:1", "feature_work", "2026-07-03", FEATURE_FIELDS, false, old);
        long withdrawn = item(ada, "feature_work:11:1", "feature_work", "2026-07-03", FEATURE_FIELDS, false, null);
        long withdrawal = item(ada, "feature_work:11:1", "feature_work", "2026-07-03", FEATURE_FIELDS, true, withdrawn);

        JsonNode body = JobEndpointClient.json(summaries(old + "," + replacement + "," + withdrawn + "," + withdrawal));

        assertFalse(body.get(0).get("current").booleanValue());
        assertEquals(replacement, body.get(0).get("currentItemId").longValue());
        assertTrue(body.get(1).get("current").booleanValue());
        assertTrue(body.get(1).get("currentItemId").isNull());
        assertFalse(body.get(2).get("current").booleanValue());
        assertTrue(body.get(2).get("currentItemId").isNull());
        assertFalse(body.get(3).get("current").booleanValue());
        assertTrue(body.get(3).get("currentItemId").isNull());
    }

    @Test
    void anUnknownIdIsLeftOut() throws Exception {
        long feature = item(ada, "feature_work:10:1", "feature_work", "2026-07-03", FEATURE_FIELDS, false, null);

        JsonNode body = JobEndpointClient.json(summaries("999999," + feature));

        assertEquals(1, body.size());
        assertEquals(feature, body.get(0).get("id").longValue());
    }

    @Test
    void idsThatAreNotPositiveWholeNumbersAreRefusedNamingTheValue() throws Exception {
        String[][] requestsAndRefusedValues = {{"abc", "abc"}, {"1,x2", "x2"}, {"0", "0"}, {"-3", "-3"}, {"1.5", "1.5"}
        };
        for (String[] requestAndValue : requestsAndRefusedValues) {
            HttpResponse<String> response = summaries(requestAndValue[0]);

            assertEquals(400, response.statusCode(), requestAndValue[0]);
            assertTrue(
                    JobEndpointClient.json(response).get("detail").stringValue().contains(requestAndValue[1]));
        }
    }

    @Test
    void noIdsAreRefused() throws Exception {
        assertEquals(400, summaries("").statusCode());
        assertEquals(400, client.get("/api/evidence-items").statusCode());
    }

    @Test
    void moreIdsThanTheLimitAreRefused() throws Exception {
        String ids = LongStream.rangeClosed(1, 1001).mapToObj(Long::toString).collect(Collectors.joining(","));

        HttpResponse<String> response = summaries(ids);

        assertEquals(400, response.statusCode());
        assertTrue(JobEndpointClient.json(response).get("detail").stringValue().contains("1000"));
    }
}
