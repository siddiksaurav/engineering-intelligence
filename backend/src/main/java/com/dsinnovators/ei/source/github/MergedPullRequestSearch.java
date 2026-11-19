package com.dsinnovators.ei.source.github;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * The numbers of the pull requests merged in a range of days, as GitHub search finds them. One
 * search per day, because one search returns at most 1,000 results (F02 design 5.2 step 1).
 */
@Component
class MergedPullRequestSearch {

    private static final Logger LOG = LoggerFactory.getLogger(MergedPullRequestSearch.class);

    // GitHub's limit: a search returns at most this many results, whatever it counts.
    private static final int MOST_A_SEARCH_RETURNS = 1000;
    private static final String QUERY = GraphQlDocuments.read("search-merged");

    private final GitHubApi api;
    private final GitHubProperties properties;

    MergedPullRequestSearch(GitHubApi api, GitHubProperties properties) {
        this.api = api;
        this.properties = properties;
    }

    /**
     * The numbers of the pull requests of the repository merged from {@code from} to {@code to},
     * both included, in day order.
     *
     * @throws GitHubApiException when a day finds more than a search returns, or stays incomplete
     */
    List<Integer> numbers(String repository, LocalDate from, LocalDate to) throws InterruptedException {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("The merge range ends (" + to + ") before it starts (" + from + ")");
        }
        List<Integer> numbers = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            List<Integer> ofDay = numbersMergedOn(repository, day);
            LOG.info("{}: {} pull requests merged on {}", repository, ofDay.size(), day);
            numbers.addAll(ofDay);
        }
        return numbers;
    }

    // Search results can come back short while GitHub's index is busy; the day is searched again.
    private List<Integer> numbersMergedOn(String repository, LocalDate day) throws InterruptedException {
        String query = "repo:" + repository + " is:pr is:merged merged:" + day;
        for (int attempt = 1; ; attempt++) {
            DayResult result = searchDay(query, repository, day);
            if (result.numbers().size() == result.counted()) {
                return result.numbers();
            }
            if (attempt > properties.retries()) {
                throw new GitHubApiException("GitHub search for pull requests of " + repository + " merged on " + day
                        + " gave " + result.numbers().size() + " of " + result.counted() + ", " + attempt
                        + " attempts");
            }
            LOG.warn(
                    "Search for {} gave {} of {}; searching again",
                    day,
                    result.numbers().size(),
                    result.counted());
        }
    }

    private DayResult searchDay(String query, String repository, LocalDate day) throws InterruptedException {
        List<Integer> numbers = new ArrayList<>();
        String after = null;
        int counted;
        do {
            Map<String, Object> variables = new HashMap<>();
            variables.put("query", query);
            if (after != null) {
                variables.put("after", after);
            }
            JsonNode search = api.graphQl(QUERY, variables).path("search");
            counted = search.path("issueCount").asInt();
            if (counted > MOST_A_SEARCH_RETURNS) {
                throw new GitHubApiException("GitHub search finds " + counted + " pull requests of " + repository
                        + " merged on " + day + "; one search returns at most " + MOST_A_SEARCH_RETURNS);
            }
            for (JsonNode node : search.path("nodes")) {
                numbers.add(number(node, day));
            }
            JsonNode pageInfo = search.path("pageInfo");
            after = pageInfo.path("hasNextPage").asBoolean()
                    ? pageInfo.path("endCursor").asString()
                    : null;
        } while (after != null);
        return new DayResult(numbers, counted);
    }

    private static int number(JsonNode node, LocalDate day) {
        JsonNode number = node.path("number");
        if (!number.isInt()) {
            throw new GitHubApiException("GitHub search for " + day + " gave a result without a number: " + node);
        }
        return number.intValue();
    }

    private record DayResult(List<Integer> numbers, int counted) {}
}
