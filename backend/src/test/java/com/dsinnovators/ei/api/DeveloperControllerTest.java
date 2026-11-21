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
class DeveloperControllerTest extends DatabaseTest {

    private static final String FEATURE_FIELDS = """
            {"feature":{"number":10,"title":"Single sign-on","type":"Feature","url":"https://op.test/wp/10"},
             "tasks":[],
             "pullRequests":[{"externalId":"1","number":101,"title":"Add SSO","url":"https://gh.test/pull/101","mergedAt":"2026-07-01T10:00:00Z"}],
             "doneAt":"2026-07-03T10:00:00Z"}
            """;
    private static final String BUG_FIELDS = """
            {"bug":{"number":20,"title":"Login fails","url":"https://op.test/wp/20","priority":"High"},
             "underFeature":null,
             "pullRequests":[{"externalId":"2","number":102,"title":"Fix login","url":"https://gh.test/pull/102","mergedAt":"2026-07-08T10:00:00Z"}],
             "doneAt":"2026-07-09T10:00:00Z"}
            """;
    private static final String COMMENT_FIELDS = """
            {"pullRequest":{"externalId":"3","number":103,"title":"Add cache","url":"https://gh.test/pull/103","mergedAt":"2026-10-02T10:00:00Z"},
             "pullRequestAuthorPersonId":null,
             "comment":{"databaseId":77,"url":"https://gh.test/pull/103#discussion_r77","path":"a.java","createdAt":"2026-10-01T10:00:00Z","isReply":false}}
            """;

    private static final String TEST_CHANGE_FIELDS = """
            {"pullRequest":{"externalId":"4","number":104,"title":"Add spec","url":"https://gh.test/pull/104","mergedAt":"2026-10-05T10:00:00Z"},
             "testKind":"integration"}
            """;

    private static final String NO_WORK_ITEM_FIELDS = """
            {"noWorkItem":true,
             "pullRequests":[{"externalId":"4","number":104,"title":"Add spec","url":"https://gh.test/pull/104","mergedAt":"2026-10-05T10:00:00Z"}]}
            """;

    @Value("${local.server.port}")
    private int port;

    private JobEndpointClient client;
    private long ada;

    @BeforeEach
    void createPerson() {
        client = new JobEndpointClient(port);
        ada = person("Ada");
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

    @Test
    void theItemsOfADeveloperAreTheCurrentOnesNewestFirst() throws Exception {
        long featureId = item(ada, "feature_work:10:1", "feature_work", "2026-07-03", FEATURE_FIELDS, false, null);
        long oldBug = item(ada, "bug_fix:20:1", "bug_fix", "2026-07-09", BUG_FIELDS, false, null);
        long currentBug = item(ada, "bug_fix:20:1", "bug_fix", "2026-07-09", BUG_FIELDS, false, oldBug);
        long commentId = item(
                ada, "review_comment_written:77", "review_comment_written", "2026-10-02", COMMENT_FIELDS, false, null);
        long withdrawn = item(
                ada, "review_comment_written:78", "review_comment_written", "2026-10-03", COMMENT_FIELDS, false, null);
        item(ada, "review_comment_written:78", "review_comment_written", "2026-10-03", COMMENT_FIELDS, true, withdrawn);

        HttpResponse<String> response = client.get("/api/developers/" + ada + "/evidence-items");

        assertEquals(200, response.statusCode());
        JsonNode body = JobEndpointClient.json(response);
        assertEquals("Ada", body.get("person").get("displayName").stringValue());
        assertEquals(ada, body.get("person").get("personId").longValue());
        JsonNode items = body.get("items");
        assertEquals(3, items.size());
        assertEquals(commentId, items.get(0).get("id").longValue());
        assertEquals(currentBug, items.get(1).get("id").longValue());
        assertEquals(featureId, items.get(2).get("id").longValue());
    }

    @Test
    void anItemHasItsTypeDateQuarterTitleAndLinks() throws Exception {
        item(ada, "feature_work:10:1", "feature_work", "2026-07-03", FEATURE_FIELDS, false, null);
        item(ada, "bug_fix:20:1", "bug_fix", "2026-07-09", BUG_FIELDS, false, null);
        item(ada, "review_comment_written:77", "review_comment_written", "2026-10-02", COMMENT_FIELDS, false, null);

        JsonNode items = JobEndpointClient.json(client.get("/api/developers/" + ada + "/evidence-items"))
                .get("items");

        JsonNode comment = items.get(0);
        assertEquals("review_comment_written", comment.get("type").stringValue());
        assertEquals("2026-10-02", comment.get("date").stringValue());
        assertEquals("2026-Q4", comment.get("quarter").stringValue());
        assertEquals("Add cache", comment.get("title").stringValue());
        assertEquals(
                "https://source.test/review_comment_written:77",
                comment.get("sourceUrl").stringValue());
        assertLink(comment.get("links").get(0), "Comment on GitHub", "https://gh.test/pull/103#discussion_r77");
        assertLink(comment.get("links").get(1), "Pull request #103 on GitHub", "https://gh.test/pull/103");
        assertEquals(77, comment.get("fields").get("comment").get("databaseId").longValue());

        JsonNode bug = items.get(1);
        assertEquals("Login fails", bug.get("title").stringValue());
        assertLink(bug.get("links").get(0), "Work item #20 on OpenProject", "https://op.test/wp/20");
        assertLink(bug.get("links").get(1), "Pull request #102 on GitHub", "https://gh.test/pull/102");

        JsonNode feature = items.get(2);
        assertEquals("Single sign-on", feature.get("title").stringValue());
        assertEquals("2026-Q3", feature.get("quarter").stringValue());
        assertLink(feature.get("links").get(0), "Work item #10 on OpenProject", "https://op.test/wp/10");
        assertLink(feature.get("links").get(1), "Pull request #101 on GitHub", "https://gh.test/pull/101");
    }

    private long tier(long itemId, String tier, String reason, Long supersedes) {
        return jdbc.queryForObject("""
                insert into judgment (kind, evidence_item_id, value, tier, reason, facts, judged_by, model,
                                      prompt_version, input_hash, supersedes_id, judged_at)
                values ('item_tier', ?, '{}'::jsonb, ?, ?, '[{"fact":"f","link":"https://gh.test/pull/104/files"}]'::jsonb,
                        'model', 'm', 'v1', 'hash' || gen_random_uuid(), ?, now())
                returning id
                """, Long.class, itemId, tier, reason, supersedes);
    }

    @Test
    void listsATestChangeWithItsTier() throws Exception {
        long testChange = item(ada, "test_change:104", "test_change", "2026-10-05", TEST_CHANGE_FIELDS, false, null);
        long untiered = item(ada, "security:104", "security", "2026-10-04", TEST_CHANGE_FIELDS, false, null);
        long first = tier(testChange, "T1", "Unit tests only", null);
        tier(testChange, "T2", "Integration tests", first);

        JsonNode body = JobEndpointClient.json(client.get("/api/developers/" + ada + "/evidence-items"));
        JsonNode items = body.get("items");

        JsonNode withTier = items.get(0);
        assertEquals(testChange, withTier.get("id").longValue());
        assertEquals("test_change", withTier.get("type").stringValue());
        assertEquals("Add spec", withTier.get("title").stringValue());
        assertLink(withTier.get("links").get(0), "Pull request #104 on GitHub", "https://gh.test/pull/104");
        assertEquals("T2", withTier.get("tier").get("tier").stringValue());
        assertEquals("Integration tests", withTier.get("tier").get("reason").stringValue());
        assertEquals(untiered, items.get(1).get("id").longValue());
        assertTrue(items.get(1).get("tier").isNull());
    }

    @Test
    void featureWorkAndBugFixWithoutAWorkItemShowTheirPullRequest() throws Exception {
        item(ada, "feature_work:pull_request:104", "feature_work", "2026-10-05", NO_WORK_ITEM_FIELDS, false, null);
        item(ada, "bug_fix:pull_request:104", "bug_fix", "2026-10-04", NO_WORK_ITEM_FIELDS, false, null);

        JsonNode items = JobEndpointClient.json(client.get("/api/developers/" + ada + "/evidence-items"))
                .get("items");

        for (JsonNode item : items) {
            assertEquals("Add spec", item.get("title").stringValue());
            assertEquals(1, item.get("links").size());
            assertLink(item.get("links").get(0), "Pull request #104 on GitHub", "https://gh.test/pull/104");
            assertTrue(item.get("tier").isNull());
        }
    }

    @Test
    void theListCountsTheNewTypes() throws Exception {
        item(ada, "test_change:104", "test_change", "2026-10-05", TEST_CHANGE_FIELDS, false, null);
        item(ada, "refactor_performance:104", "refactor_performance", "2026-10-05", TEST_CHANGE_FIELDS, false, null);
        item(ada, "security:104", "security", "2026-10-05", TEST_CHANGE_FIELDS, false, null);
        item(ada, "infra_release:104", "infra_release", "2026-10-05", TEST_CHANGE_FIELDS, false, null);
        item(ada, "documentation:104", "documentation", "2026-10-05", TEST_CHANGE_FIELDS, false, null);

        JsonNode counts =
                JobEndpointClient.json(client.get("/api/developers")).get(0).get("counts");

        assertEquals(1, counts.get("testChange").longValue());
        assertEquals(1, counts.get("refactorPerformance").longValue());
        assertEquals(1, counts.get("security").longValue());
        assertEquals(1, counts.get("infraRelease").longValue());
        assertEquals(1, counts.get("documentation").longValue());
    }

    private static void assertLink(JsonNode link, String label, String url) {
        assertEquals(label, link.get("label").stringValue());
        assertEquals(url, link.get("url").stringValue());
    }

    @Test
    void theListHasEveryPersonWithTheCountsOfCurrentItems() throws Exception {
        long grace = person("Grace");
        item(ada, "feature_work:10:1", "feature_work", "2026-07-03", FEATURE_FIELDS, false, null);
        long oldBug = item(ada, "bug_fix:20:1", "bug_fix", "2026-07-09", BUG_FIELDS, false, null);
        item(ada, "bug_fix:20:1", "bug_fix", "2026-07-09", BUG_FIELDS, false, oldBug);
        long gone = item(
                ada, "review_comment_written:77", "review_comment_written", "2026-10-02", COMMENT_FIELDS, false, null);
        item(ada, "review_comment_written:77", "review_comment_written", "2026-10-02", COMMENT_FIELDS, true, gone);

        JsonNode developers = JobEndpointClient.json(client.get("/api/developers"));

        assertEquals(2, developers.size());
        JsonNode first = developers.get(0);
        assertEquals(ada, first.get("personId").longValue());
        assertEquals("Ada", first.get("displayName").stringValue());
        assertEquals(1, first.get("counts").get("featureWork").longValue());
        assertEquals(1, first.get("counts").get("bugFix").longValue());
        assertEquals(0, first.get("counts").get("reviewCommentWritten").longValue());
        assertEquals(grace, developers.get(1).get("personId").longValue());
        assertEquals(0, developers.get(1).get("counts").get("featureWork").longValue());
    }

    @Test
    void anUnknownDeveloperAnswers404NamingTheId() throws Exception {
        HttpResponse<String> response = client.get("/api/developers/999/evidence-items");

        assertEquals(404, response.statusCode());
        assertTrue(JobEndpointClient.json(response).get("detail").stringValue().contains("999"));
    }
}
