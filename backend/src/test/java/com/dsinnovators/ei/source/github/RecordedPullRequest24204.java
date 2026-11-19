package com.dsinnovators.ei.source.github;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * GitHub's answers for pull request 24204 of opf/openproject, recorded on 19 Nov 2026: 135 files on
 * two pages, 1 label, 13 reviews, 12 threads with 17 inline comments, 3 conversation comments.
 */
final class RecordedPullRequest24204 {

    static final int NUMBER = 24204;
    static final int REVIEWS = 13;
    static final int REVIEW_COMMENTS = 17;
    static final int PULL_REQUEST_COMMENTS = 3;
    static final int FILES = 135;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String FILES_URL =
            "https://api.github.com/repos/opf/openproject/pulls/24204/files?per_page=100";
    private static final String FILES_PAGE_2 =
            "https://api.github.com/repositories/1/pulls/24204/files?per_page=100&page=2";

    private RecordedPullRequest24204() {}

    /** Expects the calls that fetch the pull request, in order, and answers them as recorded. */
    static void expect(MockRestServiceServer server) {
        expect(server, recorded("graphql-pull-request.json"));
    }

    /** As {@link #expect}, with the pull request's body changed to {@code body}. */
    static void expectWithBody(MockRestServiceServer server, String body) {
        ObjectNode answer = (ObjectNode) JSON.readTree(recorded("graphql-pull-request.json"));
        ((ObjectNode) answer.path("data").path("repository").path("pullRequest")).put("body", body);
        expect(server, answer.toString());
    }

    private static void expect(MockRestServiceServer server, String pullRequestAnswer) {
        server.expect(requestTo(GitHubApiFixture.GRAPHQL))
                .andExpect(jsonPath("$.variables.number").value(NUMBER))
                .andExpect(jsonPath("$.variables.after").doesNotExist())
                .andRespond(withSuccess(pullRequestAnswer, MediaType.APPLICATION_JSON));
        server.expect(requestTo(GitHubApiFixture.GRAPHQL))
                .andExpect(jsonPath("$.variables.after").value("MTAw"))
                .andRespond(withSuccess(recorded("graphql-files-page-2.json"), MediaType.APPLICATION_JSON));
        HttpHeaders nextPage = new HttpHeaders();
        nextPage.add("Link", "<" + FILES_PAGE_2 + ">; rel=\"next\"");
        server.expect(requestTo(FILES_URL))
                .andRespond(withSuccess(recorded("rest-files-page-1.json"), MediaType.APPLICATION_JSON)
                        .headers(nextPage));
        server.expect(requestTo(FILES_PAGE_2))
                .andRespond(withSuccess(recorded("rest-files-page-2.json"), MediaType.APPLICATION_JSON));
    }

    private static String recorded(String file) {
        try (InputStream in = RecordedPullRequest24204.class.getResourceAsStream("/github/pr-24204/" + file)) {
            if (in == null) {
                throw new IllegalStateException("No recorded response " + file);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the recorded response " + file, e);
        }
    }
}
