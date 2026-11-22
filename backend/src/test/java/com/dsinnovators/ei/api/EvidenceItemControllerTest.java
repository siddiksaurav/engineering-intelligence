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
    private static final String COMMENT_FIELDS = """
            {"pullRequest":{"externalId":"3","number":103,"title":"Add cache","url":"https://gh.test/pull/103","mergedAt":"2026-10-02T10:00:00Z"},
             "pullRequestAuthorPersonId":null,
             "comment":{"databaseId":77,"url":"https://gh.test/pull/103#discussion_r77","path":"a.java","createdAt":"2026-10-01T10:00:00Z","isReply":false}}
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
        return judgment(itemId, kind, tier, "{}", supersedes);
    }

    private long judgment(long itemId, String kind, String tier, String value, Long supersedes) {
        return jdbc.queryForObject("""
                insert into judgment (kind, evidence_item_id, value, tier, reason, facts, judged_by, model,
                                      prompt_version, input_hash, supersedes_id, judged_at)
                values (?, ?, ?::jsonb, ?, 'Because', '[{"fact":"Adds a cache","link":"https://gh.test/pull/101"}]'::jsonb,
                        'model', 'gpt-test', 'v1', 'hash' || gen_random_uuid(), ?, '2026-11-01T10:00:00Z')
                returning id
                """, Long.class, kind, itemId, value, tier, supersedes);
    }

    private JsonNode detail(long id) throws Exception {
        HttpResponse<String> response = client.get("/api/evidence-items/" + id);
        assertEquals(200, response.statusCode());
        return JobEndpointClient.json(response);
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

    @Test
    void returnsTheItemWithItsCurrentJudgment() throws Exception {
        long feature = item(ada, "feature_work:10:1", "feature_work", "2026-07-03", FEATURE_FIELDS, false, null);
        long first = judgment(feature, "feature_work_complexity", "T1", "{\"complexity\":\"Low\"}", null);
        judgment(feature, "feature_work_complexity", "T3", "{\"complexity\":\"Very high\"}", first);

        JsonNode body = detail(feature);

        assertEquals(feature, body.get("id").longValue());
        assertEquals("Single sign-on", body.get("title").stringValue());
        assertEquals("Ada", body.get("person").get("displayName").stringValue());
        assertEquals("T3", body.get("tier").stringValue());
        assertTrue(body.get("current").booleanValue());
        JsonNode judgment = body.get("judgment");
        assertEquals("judged", judgment.get("state").stringValue());
        assertEquals("feature_work_complexity", judgment.get("kind").stringValue());
        assertEquals("T3", judgment.get("tier").stringValue());
        assertEquals("Very high", judgment.get("value").get("complexity").stringValue());
        assertEquals("Because", judgment.get("reason").stringValue());
        assertEquals("Adds a cache", judgment.get("facts").get(0).get("fact").stringValue());
        assertEquals(
                "https://gh.test/pull/101",
                judgment.get("facts").get(0).get("link").stringValue());
        assertEquals("gpt-test", judgment.get("model").stringValue());
        assertEquals("v1", judgment.get("promptVersion").stringValue());
        assertTrue(judgment.get("judgedAt").stringValue().startsWith("2026-11-01T10:00:00"));
    }

    @Test
    void aReviewCommentAndATestChangeShowTheirOwnKindOfJudgment() throws Exception {
        long comment = item(
                ada, "review_comment_written:77", "review_comment_written", "2026-10-02", COMMENT_FIELDS, false, null);
        judgment(comment, "review_comment", "T2", "{\"commentType\":\"logic\",\"substantive\":true}", null);
        long testChange = item(ada, "test_change:104", "test_change", "2026-10-05", PULL_REQUEST_FIELDS, false, null);
        judgment(testChange, "item_tier", "T2", "{\"kind\":\"integration\"}", null);
        judgment(testChange, "feature_work_complexity", "T3", null);

        JsonNode commentJudgment = detail(comment).get("judgment");
        assertEquals("judged", commentJudgment.get("state").stringValue());
        assertEquals("review_comment", commentJudgment.get("kind").stringValue());
        assertEquals("logic", commentJudgment.get("value").get("commentType").stringValue());
        assertTrue(commentJudgment.get("value").get("substantive").booleanValue());

        JsonNode testJudgment = detail(testChange).get("judgment");
        assertEquals("item_tier", testJudgment.get("kind").stringValue());
        assertEquals("T2", testJudgment.get("tier").stringValue());
        assertEquals("integration", testJudgment.get("value").get("kind").stringValue());
    }

    @Test
    void anItemWithoutAJudgmentIsNotJudgedYet() throws Exception {
        long feature = item(ada, "feature_work:10:1", "feature_work", "2026-07-03", FEATURE_FIELDS, false, null);

        JsonNode judgment = detail(feature).get("judgment");

        assertEquals("not_judged_yet", judgment.get("state").stringValue());
        assertTrue(judgment.get("kind").isNull());
        assertTrue(judgment.get("tier").isNull());
        assertTrue(judgment.get("value").isNull());
        assertTrue(judgment.get("reason").isNull());
        assertTrue(judgment.get("facts").isNull());
        assertTrue(judgment.get("model").isNull());
        assertTrue(judgment.get("promptVersion").isNull());
        assertTrue(judgment.get("judgedAt").isNull());
    }

    @Test
    void aBugFixAndADocumentationItemAreNotTiered() throws Exception {
        long bugFix = item(ada, "bug_fix:20:1", "bug_fix", "2026-10-05", BUG_FIELDS, false, null);
        long documentation =
                item(ada, "documentation:104", "documentation", "2026-10-05", PULL_REQUEST_FIELDS, false, null);
        judgment(bugFix, "item_tier", "T1", null);

        for (long id : new long[] {bugFix, documentation}) {
            JsonNode judgment = detail(id).get("judgment");
            assertEquals("not_tiered", judgment.get("state").stringValue());
            assertTrue(judgment.get("tier").isNull());
        }
    }

    @Test
    void aSupersededItemIsShownAndPointsToTheCurrentRow() throws Exception {
        long old = item(ada, "feature_work:10:1", "feature_work", "2026-07-03", FEATURE_FIELDS, false, null);
        long replacement = item(ada, "feature_work:10:1", "feature_work", "2026-07-03", FEATURE_FIELDS, false, old);

        JsonNode body = detail(old);

        assertFalse(body.get("current").booleanValue());
        assertEquals(replacement, body.get("currentItemId").longValue());
    }

    @Test
    void anUnknownItemAnswers404NamingTheId() throws Exception {
        HttpResponse<String> response = client.get("/api/evidence-items/999999");

        assertEquals(404, response.statusCode());
        assertTrue(JobEndpointClient.json(response).get("detail").stringValue().contains("999999"));
    }
}
