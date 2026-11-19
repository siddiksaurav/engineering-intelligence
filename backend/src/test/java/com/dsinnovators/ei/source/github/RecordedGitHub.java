package com.dsinnovators.ei.source.github;

import static com.dsinnovators.ei.source.github.GitHubResponses.answer;
import static com.dsinnovators.ei.source.github.GitHubResponses.diffFiles;
import static com.dsinnovators.ei.source.github.GitHubResponses.pullRequest;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import tools.jackson.databind.node.ObjectNode;

/**
 * Puts a {@link GitHubApiFixture} in a Spring test context in place of the real GitHub client, so that
 * an end-to-end test of any package answers GitHub from a mock server.
 */
@TestConfiguration
public class RecordedGitHub {

    @Bean
    GitHubApiFixture gitHubApiFixture() {
        return new GitHubApiFixture();
    }

    @Bean
    @Primary
    GitHubApi recordedGitHubApi(GitHubApiFixture fixture) {
        return fixture.api;
    }

    /** Expects the two calls that fetch merged pull request {@code number} of opf/openproject, and answers them. */
    public static void expectMergedPullRequest(MockRestServiceServer server, int number, String body, String mergedAt) {
        ObjectNode pullRequest = pullRequest(number);
        pullRequest.put("body", body).put("mergedAt", mergedAt);
        server.expect(requestTo(GitHubApiFixture.GRAPHQL))
                .andRespond(withSuccess(answer(pullRequest), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/repos/opf/openproject/pulls/" + number + "/files?per_page=100"))
                .andRespond(withSuccess(diffFiles(), MediaType.APPLICATION_JSON));
    }
}
