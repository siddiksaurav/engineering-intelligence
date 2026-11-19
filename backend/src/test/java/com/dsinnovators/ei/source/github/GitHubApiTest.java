package com.dsinnovators.ei.source.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import tools.jackson.databind.JsonNode;

class GitHubApiTest {

    private static final String TOKEN = GitHubApiFixture.TOKEN;
    private static final Instant NOW = GitHubApiFixture.NOW;
    private static final String GRAPHQL = GitHubApiFixture.GRAPHQL;

    private List<Duration> waits;
    private MockRestServiceServer server;

    private GitHubApi api(String token) {
        GitHubApiFixture fixture = new GitHubApiFixture(token);
        waits = fixture.waits;
        server = fixture.server;
        return fixture.api;
    }

    private GitHubApi api() {
        return api(TOKEN);
    }

    private static HttpHeaders budget(int remaining, Instant reset) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-ratelimit-remaining", Integer.toString(remaining));
        headers.add("x-ratelimit-reset", Long.toString(reset.getEpochSecond()));
        return headers;
    }

    @Test
    void aGraphQlCallSendsTheTokenAndTheQueryAndReturnsTheData() throws Exception {
        GitHubApi api = api();
        server.expect(requestTo(GRAPHQL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andExpect(jsonPath("$.query").value("query { viewer { login } }"))
                .andExpect(jsonPath("$.variables.number").value(24204))
                .andRespond(withSuccess("{\"data\":{\"amount\":1.10}}", MediaType.APPLICATION_JSON));

        JsonNode data = api.graphQl("query { viewer { login } }", Map.of("number", 24204));

        assertEquals("{\"amount\":1.10}", data.toString());
        server.verify();
    }

    @Test
    void aGraphQlErrorFailsWithItsMessage() {
        GitHubApi api = api();
        server.expect(requestTo(GRAPHQL))
                .andRespond(withSuccess(
                        "{\"data\":null,\"errors\":[{\"type\":\"INTERNAL\",\"message\":\"Something went wrong\"}]}",
                        MediaType.APPLICATION_JSON));

        GitHubApiException failed = assertThrows(GitHubApiException.class, () -> api.graphQl("query", Map.of()));

        assertEquals("GitHub GraphQL errors: Something went wrong", failed.getMessage());
    }

    @Test
    void aGraphQlNotFoundErrorIsReportedAsNotFound() {
        GitHubApi api = api();
        server.expect(requestTo(GRAPHQL))
                .andRespond(withSuccess(
                        "{\"data\":{\"repository\":{\"pullRequest\":null}},\"errors\":[{\"type\":\"NOT_FOUND\","
                                + "\"message\":\"Could not resolve to a PullRequest with the number of 1.\"}]}",
                        MediaType.APPLICATION_JSON));

        GitHubNotFoundException notFound =
                assertThrows(GitHubNotFoundException.class, () -> api.graphQl("query", Map.of()));

        assertEquals("Could not resolve to a PullRequest with the number of 1.", notFound.getMessage());
    }

    @Test
    void everyPageOfARestListIsRead() throws Exception {
        GitHubApi api = api();
        HttpHeaders firstPage = new HttpHeaders();
        firstPage.add(
                "Link",
                "<https://api.github.com/repositories/1/pulls/7/files?per_page=100&page=2>; rel=\"next\","
                        + " <https://api.github.com/repositories/1/pulls/7/files?per_page=100&page=2>; rel=\"last\"");
        server.expect(requestTo("https://api.github.com/repos/opf/openproject/pulls/7/files?per_page=100"))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess("[{\"filename\":\"a\"},{\"filename\":\"b\"}]", MediaType.APPLICATION_JSON)
                        .headers(firstPage));
        server.expect(requestTo("https://api.github.com/repositories/1/pulls/7/files?per_page=100&page=2"))
                .andRespond(withSuccess("[{\"filename\":\"c\"}]", MediaType.APPLICATION_JSON));

        List<JsonNode> files = api.getAllPages("/repos/opf/openproject/pulls/7/files?per_page=100");

        assertEquals(
                List.of("a", "b", "c"),
                files.stream().map(file -> file.get("filename").stringValue()).toList());
        server.verify();
    }

    @Test
    void aNextPageOnAnotherHostIsRefusedSoTheTokenNeverLeavesGitHub() {
        GitHubApi api = api();
        HttpHeaders elsewhere = new HttpHeaders();
        elsewhere.add("Link", "<https://example.org/steal?page=2>; rel=\"next\"");
        server.expect(requestTo("https://api.github.com/repos/opf/openproject/pulls/7/files"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON).headers(elsewhere));

        GitHubApiException refused =
                assertThrows(GitHubApiException.class, () -> api.getAllPages("/repos/opf/openproject/pulls/7/files"));

        assertEquals(
                "GitHub sent a next page outside https://api.github.com: https://example.org/steal?page=2",
                refused.getMessage());
    }

    @Test
    void whenTheBudgetFallsToTheReserveTheNextCallWaitsUntilTheReset() throws Exception {
        GitHubApi api = api();
        server.expect(requestTo(GRAPHQL))
                .andRespond(withSuccess("{\"data\":{}}", MediaType.APPLICATION_JSON)
                        .headers(budget(50, NOW.plusSeconds(120))));

        api.graphQl("query", Map.of());

        assertEquals(List.of(Duration.ofSeconds(121)), waits);
    }

    @Test
    void aBudgetAboveTheReserveDoesNotWait() throws Exception {
        GitHubApi api = api();
        server.expect(requestTo(GRAPHQL))
                .andRespond(withSuccess("{\"data\":{}}", MediaType.APPLICATION_JSON)
                        .headers(budget(51, NOW.plusSeconds(120))));

        api.graphQl("query", Map.of());

        assertEquals(List.of(), waits);
    }

    @Test
    void aRateLimitedAnswerWaitsAsToldAndTriesAgain() throws Exception {
        GitHubApi api = api();
        HttpHeaders retryAfter = new HttpHeaders();
        retryAfter.add("retry-after", "30");
        server.expect(requestTo(GRAPHQL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .body("{\"message\":\"You have exceeded a secondary rate limit\"}")
                        .headers(retryAfter));
        server.expect(requestTo(GRAPHQL)).andRespond(withSuccess("{\"data\":{}}", MediaType.APPLICATION_JSON));

        api.graphQl("query", Map.of());

        assertEquals(List.of(Duration.ofSeconds(30)), waits);
        server.verify();
    }

    @Test
    void aSpentBudgetWaitsUntilTheResetAndTriesAgain() throws Exception {
        GitHubApi api = api();
        server.expect(requestTo(GRAPHQL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .body("{\"message\":\"API rate limit exceeded\"}")
                        .headers(budget(0, NOW.plusSeconds(600))));
        server.expect(requestTo(GRAPHQL)).andRespond(withSuccess("{\"data\":{}}", MediaType.APPLICATION_JSON));

        api.graphQl("query", Map.of());

        assertEquals(List.of(Duration.ofSeconds(601)), waits);
    }

    @Test
    void aRateLimitWithoutAnyTimeWaitsOneMinute() throws Exception {
        GitHubApi api = api();
        server.expect(requestTo(GRAPHQL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("{\"message\":\"slow down\"}"));
        server.expect(requestTo(GRAPHQL)).andRespond(withSuccess("{\"data\":{}}", MediaType.APPLICATION_JSON));

        api.graphQl("query", Map.of());

        assertEquals(List.of(Duration.ofMinutes(1)), waits);
    }

    @Test
    void aServerErrorIsTriedAgainWithGrowingWaits() throws Exception {
        GitHubApi api = api();
        server.expect(times(2), requestTo(GRAPHQL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(requestTo(GRAPHQL)).andRespond(withSuccess("{\"data\":{}}", MediaType.APPLICATION_JSON));

        api.graphQl("query", Map.of());

        assertEquals(List.of(Duration.ofSeconds(2), Duration.ofSeconds(4)), waits);
    }

    @Test
    void aServerErrorThatLastsFailsAfterThreeRetries() {
        GitHubApi api = api();
        server.expect(times(4), requestTo(GRAPHQL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        GitHubApiException failed = assertThrows(GitHubApiException.class, () -> api.graphQl("query", Map.of()));

        assertEquals("GitHub answered 502 to POST /graphql, 4 attempts", failed.getMessage());
        server.verify();
    }

    @Test
    void aTimeoutIsTriedAgain() throws Exception {
        GitHubApi api = api();
        server.expect(requestTo(GRAPHQL)).andRespond(withException(new SocketTimeoutException("Read timed out")));
        server.expect(requestTo(GRAPHQL)).andRespond(withSuccess("{\"data\":{}}", MediaType.APPLICATION_JSON));

        api.graphQl("query", Map.of());

        assertEquals(List.of(Duration.ofSeconds(2)), waits);
    }

    @Test
    void aConnectionThatKeepsFailingFailsAfterThreeRetries() {
        GitHubApi api = api();
        server.expect(times(4), requestTo(GRAPHQL)).andRespond(withException(new IOException("Connection reset")));

        GitHubApiException failed = assertThrows(GitHubApiException.class, () -> api.graphQl("query", Map.of()));

        assertEquals("GitHub could not be reached for POST /graphql, 4 attempts", failed.getMessage());
    }

    @Test
    void aRefusedTokenFailsAtOnceWithoutShowingIt() {
        GitHubApi api = api();
        server.expect(times(1), requestTo(GRAPHQL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"message\":\"Bad credentials\"}"));

        GitHubTokenRefusedException refused =
                assertThrows(GitHubTokenRefusedException.class, () -> api.graphQl("query", Map.of()));

        assertEquals("GitHub refused the token (401) for POST /graphql. Check GITHUB_TOKEN", refused.getMessage());
        assertFalse(refused.getMessage().contains(TOKEN));
        server.verify();
    }

    @Test
    void aForbiddenAnswerWithoutARateLimitSignIsARefusedToken() {
        GitHubApi api = api();
        server.expect(requestTo(GRAPHQL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("{\"message\":\"Resource not accessible\"}"));

        assertThrows(GitHubTokenRefusedException.class, () -> api.graphQl("query", Map.of()));
    }

    @Test
    void aRestNotFoundIsReportedAsNotFound() {
        GitHubApi api = api();
        server.expect(requestTo("https://api.github.com/repos/opf/openproject/pulls/1/files"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"message\":\"Not Found\"}"));

        GitHubNotFoundException notFound = assertThrows(
                GitHubNotFoundException.class, () -> api.getAllPages("/repos/opf/openproject/pulls/1/files"));

        assertEquals("GitHub has nothing at GET /repos/opf/openproject/pulls/1/files", notFound.getMessage());
    }

    @Test
    void aMissingTokenFailsBeforeAnyCall() {
        GitHubApi api = api("");

        GitHubTokenMissingException missing =
                assertThrows(GitHubTokenMissingException.class, () -> api.graphQl("query", Map.of()));

        assertEquals("GITHUB_TOKEN is not set", missing.getMessage());
        server.verify();
    }

    @Test
    void thePropertiesNeverShowTheToken() {
        GitHubProperties properties = new GitHubProperties(
                URI.create("https://api.github.com"), TOKEN, 50, 3, Duration.ofSeconds(2), Duration.ofSeconds(60));

        assertFalse(properties.toString().contains(TOKEN));
        assertTrue(properties.hasToken());
    }

    @Test
    void negativeOperatingNumbersAreRefusedAndNamed() {
        IllegalArgumentException refused = assertThrows(
                IllegalArgumentException.class,
                () -> new GitHubProperties(
                        URI.create("https://api.github.com"),
                        TOKEN,
                        50,
                        -1,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(60)));

        assertEquals("ei.github.retries must be zero or more, was -1", refused.getMessage());
    }
}
