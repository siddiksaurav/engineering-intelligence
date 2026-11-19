package com.dsinnovators.ei.source.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

class MergedPullRequestSearchTest {

    private static final String REPOSITORY = "opf/openproject";
    private static final LocalDate JULY_1 = LocalDate.parse("2026-07-01");
    private static final LocalDate JULY_2 = LocalDate.parse("2026-07-02");

    private final GitHubApiFixture github = new GitHubApiFixture();
    private final MockRestServiceServer server = github.server;
    private final MergedPullRequestSearch search = new MergedPullRequestSearch(github.api, github.properties);

    private static String page(int issueCount, boolean hasNextPage, String endCursor, int... numbers) {
        String nodes = IntStream.of(numbers)
                .mapToObj(number -> "{\"number\":" + number + "}")
                .collect(Collectors.joining(","));
        return "{\"data\":{\"search\":{\"issueCount\":" + issueCount + ",\"pageInfo\":{\"hasNextPage\":" + hasNextPage
                + ",\"endCursor\":" + (endCursor == null ? "null" : "\"" + endCursor + "\"") + "},\"nodes\":[" + nodes
                + "]}}}";
    }

    private void expectDay(LocalDate day, String after, String answer) {
        server.expect(requestTo(GitHubApiFixture.GRAPHQL))
                .andExpect(jsonPath("$.variables.query").value("repo:" + REPOSITORY + " is:pr is:merged merged:" + day))
                .andExpect(
                        after == null
                                ? jsonPath("$.variables.after").doesNotExist()
                                : jsonPath("$.variables.after").value(after))
                .andRespond(withSuccess(answer, MediaType.APPLICATION_JSON));
    }

    @Test
    void aRangeIsSearchedOneDayAtATimeAndEveryPageIsRead() throws Exception {
        expectDay(JULY_1, null, page(3, true, "cursor-1", 101, 102));
        expectDay(JULY_1, "cursor-1", page(3, false, "cursor-2", 103));
        expectDay(JULY_2, null, page(1, false, "cursor-3", 201));

        List<Integer> numbers = search.numbers(REPOSITORY, JULY_1, JULY_2);

        assertEquals(List.of(101, 102, 103, 201), numbers);
        server.verify();
    }

    @Test
    void aDayWithoutMergesGivesNothing() throws Exception {
        expectDay(JULY_1, null, page(0, false, null));

        assertEquals(List.of(), search.numbers(REPOSITORY, JULY_1, JULY_1));
    }

    @Test
    void aDayThatGivesFewerPullRequestsThanItCountsIsSearchedAgain() throws Exception {
        expectDay(JULY_1, null, page(3, false, "cursor-1", 101, 102));
        expectDay(JULY_1, null, page(3, false, "cursor-1", 101, 102, 103));

        assertEquals(List.of(101, 102, 103), search.numbers(REPOSITORY, JULY_1, JULY_1));
    }

    @Test
    void aDayThatStaysIncompleteFailsAndSaysHowManyWereMissing() {
        server.expect(times(4), requestTo(GitHubApiFixture.GRAPHQL))
                .andRespond(withSuccess(page(3, false, "cursor-1", 101, 102), MediaType.APPLICATION_JSON));

        GitHubApiException failed =
                assertThrows(GitHubApiException.class, () -> search.numbers(REPOSITORY, JULY_1, JULY_1));

        assertEquals(
                "GitHub search for pull requests of opf/openproject merged on 2026-07-01 gave 2 of 3, 4 attempts",
                failed.getMessage());
    }

    @Test
    void aDayWithMoreThanASearchCanReturnFails() {
        expectDay(JULY_1, null, page(1001, true, "cursor-1", 101));

        GitHubApiException failed =
                assertThrows(GitHubApiException.class, () -> search.numbers(REPOSITORY, JULY_1, JULY_1));

        assertEquals(
                "GitHub search finds 1001 pull requests of opf/openproject merged on 2026-07-01;"
                        + " one search returns at most 1000",
                failed.getMessage());
    }

    @Test
    void anEndBeforeTheStartIsRefused() {
        IllegalArgumentException refused =
                assertThrows(IllegalArgumentException.class, () -> search.numbers(REPOSITORY, JULY_2, JULY_1));

        assertEquals("The merge range ends (2026-07-01) before it starts (2026-07-02)", refused.getMessage());
    }
}
