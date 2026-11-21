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
        long commentId =
                item(ada, "review_comment:77", "review_comment_written", "2026-10-02", COMMENT_FIELDS, false, null);
        long withdrawn =
                item(ada, "review_comment:78", "review_comment_written", "2026-10-03", COMMENT_FIELDS, false, null);
        item(ada, "review_comment:78", "review_comment_written", "2026-10-03", COMMENT_FIELDS, true, withdrawn);

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
        item(ada, "review_comment:77", "review_comment_written", "2026-10-02", COMMENT_FIELDS, false, null);

        JsonNode items = JobEndpointClient.json(client.get("/api/developers/" + ada + "/evidence-items"))
                .get("items");

        JsonNode comment = items.get(0);
        assertEquals("review_comment_written", comment.get("type").stringValue());
        assertEquals("2026-10-02", comment.get("date").stringValue());
        assertEquals("2026-Q4", comment.get("quarter").stringValue());
        assertEquals("Add cache", comment.get("title").stringValue());
        assertEquals(
                "https://source.test/review_comment:77",
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
        long gone = item(ada, "review_comment:77", "review_comment_written", "2026-10-02", COMMENT_FIELDS, false, null);
        item(ada, "review_comment:77", "review_comment_written", "2026-10-02", COMMENT_FIELDS, true, gone);

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
