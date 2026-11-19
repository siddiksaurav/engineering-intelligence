package com.dsinnovators.ei.source.github;

import static com.dsinnovators.ei.source.github.GitHubResponses.answer;
import static com.dsinnovators.ei.source.github.GitHubResponses.connection;
import static com.dsinnovators.ei.source.github.GitHubResponses.diffFiles;
import static com.dsinnovators.ei.source.github.GitHubResponses.node;
import static com.dsinnovators.ei.source.github.GitHubResponses.notFound;
import static com.dsinnovators.ei.source.github.GitHubResponses.pullRequest;
import static com.dsinnovators.ei.source.github.GitHubResponses.search;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.job.JobArguments;
import com.dsinnovators.ei.job.JobCounts;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import tools.jackson.databind.node.ObjectNode;

class GitHubImportJobTest extends DatabaseTest {

    @Autowired
    private PullRequestRecorder recorder;

    private GitHubApiFixture github = new GitHubApiFixture();
    private MockRestServiceServer server = github.server;

    private final Map<String, Long> counts = new TreeMap<>();
    private final JobCounts jobCounts = (name, amount) -> counts.merge(name, amount, Long::sum);

    private GitHubImportJob job() {
        return new GitHubImportJob(
                new MergedPullRequestSearch(github.api, github.properties),
                new PullRequestFetcher(github.api),
                recorder);
    }

    private static JobArguments numbers(String numbers) {
        return JobArguments.of(Map.of("repository", "opf/openproject", "pullRequestNumbers", numbers));
    }

    private void expectPullRequest(int number) {
        expectPullRequest(number, pullRequest(number));
    }

    private void expectPullRequest(int number, ObjectNode pullRequest) {
        server.expect(requestTo(GitHubApiFixture.GRAPHQL))
                .andExpect(jsonPath("$.variables.number").value(number))
                .andRespond(withSuccess(answer(pullRequest), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/repos/opf/openproject/pulls/" + number + "/files?per_page=100"))
                .andRespond(withSuccess(diffFiles(), MediaType.APPLICATION_JSON));
    }

    private void expectGraphQl(int number, String answer) {
        server.expect(requestTo(GitHubApiFixture.GRAPHQL))
                .andExpect(jsonPath("$.variables.number").value(number))
                .andRespond(withSuccess(answer, MediaType.APPLICATION_JSON));
    }

    private int pullRequestRows() {
        return jdbc.queryForObject("select count(*) from raw_record where record_kind = 'pull_request'", Integer.class);
    }

    @Test
    void theJobIsCalledGithubImport() {
        assertEquals("github-import", job().name());
    }

    @Test
    void aRangeIsListedThenEveryPullRequestIsStoredAndCounted() throws Exception {
        server.expect(requestTo(GitHubApiFixture.GRAPHQL))
                .andExpect(
                        jsonPath("$.variables.query").value("repo:opf/openproject is:pr is:merged merged:2026-07-01"))
                .andRespond(withSuccess(search(7, 8), MediaType.APPLICATION_JSON));
        expectPullRequest(7);
        expectPullRequest(8);

        job().run(
                        JobArguments.of(Map.of(
                                "repository", "opf/openproject", "mergedFrom", "2026-07-01", "mergedTo", "2026-07-01")),
                        jobCounts);

        server.verify();
        assertEquals(
                Map.of("pull_requests_listed", 2L, "pull_request.created", 2L, "pull_request_diff.created", 2L),
                counts);
        assertEquals(2, pullRequestRows());
    }

    @Test
    void aSecondRunCountsEverythingUnchanged() throws Exception {
        expectPullRequest(7);
        job().run(numbers("7"), jobCounts);
        github = new GitHubApiFixture();
        server = github.server;
        expectPullRequest(7);
        counts.clear();

        job().run(numbers("7"), jobCounts);

        assertEquals(
                Map.of("pull_requests_listed", 1L, "pull_request.unchanged", 1L, "pull_request_diff.unchanged", 1L),
                counts);
    }

    @Test
    void aNumberThatDoesNotExistOrIsNotMergedIsCountedAndTheRunGoesOn() throws Exception {
        expectGraphQl(999999, notFound(999999));
        ObjectNode open = pullRequest(9);
        open.putNull("mergedAt");
        expectGraphQl(9, answer(open));
        expectPullRequest(7);

        job().run(numbers("999999,9,7"), jobCounts);

        assertEquals(1L, counts.get("pull_requests_not_found"));
        assertEquals(1L, counts.get("pull_requests_not_merged"));
        assertEquals(1L, counts.get("pull_request.created"));
    }

    @Test
    void aPullRequestThatFailsIsCountedTheOthersAreStoredAndTheRunFailsNamingIt() {
        server.expect(times(4), requestTo(GitHubApiFixture.GRAPHQL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        expectPullRequest(8);

        GitHubImportFailedException failed =
                assertThrows(GitHubImportFailedException.class, () -> job().run(numbers("7,8"), jobCounts));

        assertEquals("1 of 2 pull requests failed: #7", failed.getMessage());
        assertEquals(1L, counts.get("pull_requests_failed"));
        assertEquals(1, pullRequestRows());
    }

    @Test
    void aRefusedTokenFailsTheRunAtOnce() {
        server.expect(times(1), requestTo(GitHubApiFixture.GRAPHQL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"message\":\"Bad credentials\"}"));

        assertThrows(GitHubTokenRefusedException.class, () -> job().run(numbers("7,8"), jobCounts));

        server.verify();
        assertEquals(0, pullRequestRows());
    }

    @Test
    void aMissingTokenFailsTheRunAtOnce() {
        github = new GitHubApiFixture("");

        GitHubTokenMissingException missing =
                assertThrows(GitHubTokenMissingException.class, () -> job().run(numbers("7"), jobCounts));

        assertEquals("GITHUB_TOKEN is not set", missing.getMessage());
    }

    @Test
    void aListingThatFailsFailsTheRunBeforeAnyPullRequestIsFetched() {
        server.expect(times(4), requestTo(GitHubApiFixture.GRAPHQL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThrows(
                GitHubApiException.class,
                () -> job().run(
                                JobArguments.of(Map.of(
                                        "repository",
                                        "opf/openproject",
                                        "mergedFrom",
                                        "2026-07-01",
                                        "mergedTo",
                                        "2026-07-01")),
                                jobCounts));

        server.verify();
        assertEquals(0, pullRequestRows());
    }

    @Test
    void aTruncatedDiffIsCounted() throws Exception {
        ObjectNode pullRequest = pullRequest(7);
        pullRequest.set(
                "files",
                connection(
                        false,
                        null,
                        node("{\"path\":\"a.rb\",\"additions\":1,\"deletions\":0,\"changeType\":\"ADDED\"}"),
                        node("{\"path\":\"b.rb\",\"additions\":1,\"deletions\":0,\"changeType\":\"ADDED\"}")));
        expectPullRequest(7, pullRequest);

        job().run(numbers("7"), jobCounts);

        assertEquals(1L, counts.get("pull_request_diff.truncated"));
    }

    @Test
    void badArgumentsAreRefusedBeforeARunIsRecorded() {
        IllegalArgumentException refused = assertThrows(
                IllegalArgumentException.class,
                () -> job().checkArguments(JobArguments.of(Map.of("repository", "openproject"))));

        assertEquals("The argument 'repository' must be owner/name, was 'openproject'", refused.getMessage());
    }

    @Test
    void aPullRequestWithoutReviewsOrCommentsIsStoredAsTwoRows() throws Exception {
        expectPullRequest(7);

        job().run(numbers("7"), jobCounts);

        assertEquals(
                List.of("pull_request", "pull_request_diff"),
                jdbc.queryForList("select record_kind from raw_record order by record_kind", String.class));
    }
}
