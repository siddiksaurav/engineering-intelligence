package com.dsinnovators.ei.source.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class PullRequestFetcherTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String FILES_URL = "https://api.github.com/repos/opf/openproject/pulls/%d/files?per_page=100";

    private final GitHubApiFixture github = new GitHubApiFixture();
    private final MockRestServiceServer server = github.server;
    private final PullRequestFetcher fetcher = new PullRequestFetcher(github.api);

    private static String recorded(String file) {
        try (InputStream in = PullRequestFetcherTest.class.getResourceAsStream("/github/pr-24204/" + file)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the recorded response " + file, e);
        }
    }

    private void expectGraphQl(int number, String after, String answer) {
        server.expect(requestTo(GitHubApiFixture.GRAPHQL))
                .andExpect(jsonPath("$.variables.number").value(number))
                .andExpect(
                        after == null
                                ? jsonPath("$.variables.after").doesNotExist()
                                : jsonPath("$.variables.after").value(after))
                .andRespond(withSuccess(answer, MediaType.APPLICATION_JSON));
    }

    private void expectFiles(int number, String answer) {
        server.expect(requestTo(FILES_URL.formatted(number)))
                .andRespond(withSuccess(answer, MediaType.APPLICATION_JSON));
    }

    private static FetchedPullRequest found(PullRequestFetch fetch) {
        return assertInstanceOf(PullRequestFetch.Found.class, fetch).pullRequest();
    }

    // A small pull request, built in the test, for the cases the recorded one does not have.

    private static ObjectNode connection(String hasNextPage, String endCursor, JsonNode... nodes) {
        ObjectNode connection = JSON.createObjectNode();
        connection
                .putObject("pageInfo")
                .put("hasNextPage", Boolean.parseBoolean(hasNextPage))
                .put("endCursor", endCursor);
        ArrayNode list = connection.putArray("nodes");
        for (JsonNode node : nodes) {
            list.add(node);
        }
        return connection;
    }

    private static ObjectNode node(String json) {
        return (ObjectNode) JSON.readTree(json);
    }

    private static ObjectNode smallPullRequest() {
        ObjectNode pullRequest = node(
                "{\"databaseId\":1,\"number\":7,\"url\":\"https://github.com/opf/openproject/pull/7\","
                        + "\"title\":\"T\",\"body\":\"B\",\"author\":{\"login\":\"dev\",\"__typename\":\"User\",\"databaseId\":5},"
                        + "\"baseRefName\":\"dev\",\"headRefName\":\"feature\",\"createdAt\":\"2026-07-01T08:00:00Z\","
                        + "\"mergedAt\":\"2026-07-02T08:00:00Z\",\"mergedBy\":{\"login\":\"dev\",\"__typename\":\"User\","
                        + "\"databaseId\":5},\"isDraft\":false,\"repository\":{\"nameWithOwner\":\"opf/openproject\"}}");
        pullRequest.set("labels", connection("false", null));
        pullRequest.set(
                "files",
                connection(
                        "false",
                        null,
                        node("{\"path\":\"a.rb\",\"additions\":1,\"deletions\":0,\"changeType\":\"ADDED\"}")));
        pullRequest.set("reviews", connection("false", null));
        pullRequest.set("reviewThreads", connection("false", null));
        pullRequest.set("comments", connection("false", null));
        return pullRequest;
    }

    private static String answer(JsonNode pullRequest) {
        ObjectNode answer = JSON.createObjectNode();
        answer.putObject("data").putObject("repository").set("pullRequest", pullRequest);
        return answer.toString();
    }

    private static String page(JsonNode connection) {
        ObjectNode pullRequest = JSON.createObjectNode();
        pullRequest.set("page", connection);
        return answer(pullRequest);
    }

    private static ObjectNode review(long id) {
        return node("{\"databaseId\":" + id + ",\"state\":\"APPROVED\"}");
    }

    private static ObjectNode comment(long id) {
        return node("{\"databaseId\":" + id + ",\"body\":\"c\"}");
    }

    private static Set<String> keys(JsonNode node) {
        Set<String> keys = new TreeSet<>();
        node.properties().forEach(property -> keys.add(property.getKey()));
        return keys;
    }

    @Test
    void aPullRequestIsFetchedWithAllItsParts() throws Exception {
        expectGraphQl(24204, null, recorded("graphql-pull-request.json"));
        expectGraphQl(24204, "MTAw", recorded("graphql-files-page-2.json"));
        HttpHeaders nextPage = new HttpHeaders();
        nextPage.add(
                "Link", "<https://api.github.com/repositories/1/pulls/24204/files?per_page=100&page=2>; rel=\"next\"");
        server.expect(requestTo(FILES_URL.formatted(24204)))
                .andRespond(withSuccess(recorded("rest-files-page-1.json"), MediaType.APPLICATION_JSON)
                        .headers(nextPage));
        server.expect(requestTo("https://api.github.com/repositories/1/pulls/24204/files?per_page=100&page=2"))
                .andRespond(withSuccess(recorded("rest-files-page-2.json"), MediaType.APPLICATION_JSON));

        FetchedPullRequest pullRequest = found(fetcher.fetch("opf/openproject", 24204));

        server.verify();
        assertEquals("opf/openproject", pullRequest.repository());
        assertEquals(24204, pullRequest.number());
        assertEquals("https://github.com/opf/openproject/pull/24204", pullRequest.url());
        JsonNode payload = pullRequest.payload();
        assertEquals(
                Set.of(
                        "author",
                        "baseRefName",
                        "body",
                        "createdAt",
                        "databaseId",
                        "files",
                        "headRefName",
                        "isDraft",
                        "labels",
                        "mergedAt",
                        "mergedBy",
                        "number",
                        "repository",
                        "title",
                        "url"),
                keys(payload));
        assertEquals(135, payload.get("files").size());
        assertEquals(
                Set.of("additions", "changeType", "deletions", "path"),
                keys(payload.get("files").get(0)));
        assertEquals("[{\"name\":\"documentation\"}]", payload.get("labels").toString());
        assertEquals(13, pullRequest.reviews().size());
        assertEquals(4683447746L, pullRequest.reviews().get(0).get("databaseId").longValue());
        assertEquals(17, pullRequest.reviewComments().size());
        assertTrue(pullRequest.reviewComments().get(0).has("diffHunk"));
        assertEquals(3, pullRequest.pullRequestComments().size());
        assertEquals(
                4956278455L,
                pullRequest.pullRequestComments().get(0).get("databaseId").longValue());
        assertEquals(135, pullRequest.diffFiles().size());
        assertTrue(pullRequest.diffFiles().get(0).has("patch"));
        assertFalse(pullRequest.diffTruncated());
    }

    @Test
    void everyPageOfReviewsAndConversationCommentsIsRead() throws Exception {
        ObjectNode pullRequest = smallPullRequest();
        pullRequest.set("reviews", connection("true", "r1", review(1)));
        pullRequest.set("comments", connection("true", "c1", comment(10)));
        expectGraphQl(7, null, answer(pullRequest));
        expectGraphQl(7, "r1", page(connection("false", null, review(2))));
        expectGraphQl(7, "c1", page(connection("false", null, comment(11))));
        expectFiles(7, "[{\"filename\":\"a.rb\",\"patch\":\"@@\"}]");

        FetchedPullRequest fetched = found(fetcher.fetch("opf/openproject", 7));

        assertEquals(
                List.of(1L, 2L),
                fetched.reviews().stream()
                        .map(r -> r.get("databaseId").longValue())
                        .toList());
        assertEquals(
                List.of(10L, 11L),
                fetched.pullRequestComments().stream()
                        .map(c -> c.get("databaseId").longValue())
                        .toList());
        server.verify();
    }

    @Test
    void everyCommentOfALongThreadIsRead() throws Exception {
        ObjectNode thread = node("{\"id\":\"thread-1\"}");
        thread.set("comments", connection("true", "t1", comment(20)));
        ObjectNode pullRequest = smallPullRequest();
        pullRequest.set("reviewThreads", connection("false", null, thread));
        expectGraphQl(7, null, answer(pullRequest));
        ObjectNode node = JSON.createObjectNode();
        node.putObject("data").putObject("node").set("page", connection("false", null, comment(21)));
        server.expect(requestTo(GitHubApiFixture.GRAPHQL))
                .andExpect(jsonPath("$.variables.id").value("thread-1"))
                .andExpect(jsonPath("$.variables.after").value("t1"))
                .andRespond(withSuccess(node.toString(), MediaType.APPLICATION_JSON));
        expectFiles(7, "[{\"filename\":\"a.rb\",\"patch\":\"@@\"}]");

        FetchedPullRequest fetched = found(fetcher.fetch("opf/openproject", 7));

        assertEquals(
                List.of(20L, 21L),
                fetched.reviewComments().stream()
                        .map(c -> c.get("databaseId").longValue())
                        .toList());
    }

    @Test
    void aPullRequestThatDoesNotExistIsNotFound() throws Exception {
        expectGraphQl(
                999999,
                null,
                "{\"data\":{\"repository\":{\"pullRequest\":null}},\"errors\":[{\"type\":\"NOT_FOUND\","
                        + "\"message\":\"Could not resolve to a PullRequest with the number of 999999.\"}]}");

        assertInstanceOf(PullRequestFetch.NotFound.class, fetcher.fetch("opf/openproject", 999999));
    }

    @Test
    void aPullRequestThatIsNotMergedIsReportedAsSuch() throws Exception {
        ObjectNode pullRequest = smallPullRequest();
        pullRequest.putNull("mergedAt");
        expectGraphQl(7, null, answer(pullRequest));

        assertInstanceOf(PullRequestFetch.NotMerged.class, fetcher.fetch("opf/openproject", 7));
        server.verify();
    }

    @Test
    void moreFilesThanTheRestListGivesMarkTheDiffTruncated() throws Exception {
        ObjectNode pullRequest = smallPullRequest();
        pullRequest.set(
                "files",
                connection(
                        "false",
                        null,
                        node("{\"path\":\"a.rb\",\"additions\":1,\"deletions\":0,\"changeType\":\"ADDED\"}"),
                        node("{\"path\":\"b.rb\",\"additions\":1,\"deletions\":0,\"changeType\":\"ADDED\"}")));
        expectGraphQl(7, null, answer(pullRequest));
        expectFiles(7, "[{\"filename\":\"a.rb\",\"patch\":\"@@\"}]");

        assertTrue(found(fetcher.fetch("opf/openproject", 7)).diffTruncated());
    }

    @Test
    void aFileWithoutPatchIsKeptAsGitHubGaveIt() throws Exception {
        expectGraphQl(7, null, answer(smallPullRequest()));
        expectFiles(7, "[{\"filename\":\"a.rb\",\"status\":\"added\"}]");

        FetchedPullRequest fetched = found(fetcher.fetch("opf/openproject", 7));

        assertEquals(
                "{\"filename\":\"a.rb\",\"status\":\"added\"}",
                fetched.diffFiles().get(0).toString());
    }

    @Test
    void anAuthorWhoseAccountWasDeletedIsNull() throws Exception {
        ObjectNode pullRequest = smallPullRequest();
        pullRequest.putNull("author");
        expectGraphQl(7, null, answer(pullRequest));
        expectFiles(7, "[{\"filename\":\"a.rb\",\"patch\":\"@@\"}]");

        assertTrue(found(fetcher.fetch("opf/openproject", 7))
                .payload()
                .get("author")
                .isNull());
    }

    @Test
    void aRepositoryWithoutOwnerIsRefused() {
        IllegalArgumentException refused =
                assertThrows(IllegalArgumentException.class, () -> fetcher.fetch("openproject", 7));

        assertEquals("A repository is written owner/name, was 'openproject'", refused.getMessage());
    }
}
