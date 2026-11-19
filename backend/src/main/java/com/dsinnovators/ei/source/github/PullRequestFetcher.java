package com.dsinnovators.ei.source.github;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Reads one pull request with all its parts: its fields, labels and files, every review, every
 * inline comment of every thread, every conversation comment (GraphQL), and the files with their
 * patches (REST, which handles more than the 300 files the whole-diff endpoint allows).
 */
@Component
class PullRequestFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(PullRequestFetcher.class);

    private static final String PULL_REQUEST = GraphQlDocuments.read("pull-request");
    private static final Map<String, String> PAGE_QUERIES = Map.of(
            "labels", GraphQlDocuments.read("pull-request-labels"),
            "files", GraphQlDocuments.read("pull-request-files"),
            "reviews", GraphQlDocuments.read("pull-request-reviews"),
            "reviewThreads", GraphQlDocuments.read("pull-request-review-threads"),
            "comments", GraphQlDocuments.read("pull-request-comments"));
    private static final String THREAD_COMMENTS = GraphQlDocuments.read("review-thread-comments");

    private final GitHubApi api;

    PullRequestFetcher(GitHubApi api) {
        this.api = api;
    }

    /** Fetches pull request {@code number} of {@code repository} ({@code owner/name}). */
    PullRequestFetch fetch(String repository, int number) throws InterruptedException {
        int slash = repository.indexOf('/');
        if (slash <= 0 || slash != repository.lastIndexOf('/') || slash == repository.length() - 1) {
            throw new IllegalArgumentException("A repository is written owner/name, was '" + repository + "'");
        }
        Map<String, Object> variables = Map.of(
                "owner", repository.substring(0, slash), "name", repository.substring(slash + 1), "number", number);
        JsonNode pullRequest;
        try {
            pullRequest =
                    api.graphQl(PULL_REQUEST, variables).path("repository").path("pullRequest");
        } catch (GitHubNotFoundException notFound) {
            return new PullRequestFetch.NotFound();
        }
        if (!pullRequest.isObject()) {
            return new PullRequestFetch.NotFound();
        }
        if (pullRequest.path("mergedAt").isNull()) {
            return new PullRequestFetch.NotMerged();
        }
        return new PullRequestFetch.Found(fetchParts(repository, number, pullRequest, variables));
    }

    private FetchedPullRequest fetchParts(
            String repository, int number, JsonNode pullRequest, Map<String, Object> variables)
            throws InterruptedException {
        List<JsonNode> labels = allOfPullRequest("labels", pullRequest, variables);
        List<JsonNode> files = allOfPullRequest("files", pullRequest, variables);
        List<JsonNode> reviews = allOfPullRequest("reviews", pullRequest, variables);
        List<JsonNode> reviewComments = new ArrayList<>();
        for (JsonNode thread : allOfPullRequest("reviewThreads", pullRequest, variables)) {
            reviewComments.addAll(allCommentsOf(thread));
        }
        List<JsonNode> pullRequestComments = allOfPullRequest("comments", pullRequest, variables);
        List<JsonNode> diffFiles = api.getAllPages("/repos/" + repository + "/pulls/" + number + "/files?per_page=100");

        ObjectNode payload = (ObjectNode) pullRequest.deepCopy();
        PAGE_QUERIES.keySet().forEach(payload::remove);
        payload.putArray("labels").addAll(labels);
        payload.putArray("files").addAll(files);
        return new FetchedPullRequest(
                pullRequest.path("repository").path("nameWithOwner").asString(),
                pullRequest.path("number").asInt(),
                pullRequest.path("url").asString(),
                payload,
                reviews,
                reviewComments,
                pullRequestComments,
                diffFiles,
                diffFiles.size() < files.size());
    }

    private List<JsonNode> allOfPullRequest(String part, JsonNode pullRequest, Map<String, Object> variables)
            throws InterruptedException {
        return allPages(
                pullRequest.path(part),
                after -> withAfter(variables, after),
                PAGE_QUERIES.get(part),
                data -> data.path("repository").path("pullRequest").path("page"));
    }

    private List<JsonNode> allCommentsOf(JsonNode thread) throws InterruptedException {
        String threadId = thread.path("id").asString();
        return allPages(
                thread.path("comments"),
                after -> Map.of("id", threadId, "after", after),
                THREAD_COMMENTS,
                data -> data.path("node").path("page"));
    }

    private List<JsonNode> allPages(
            JsonNode firstPage,
            Function<String, Map<String, Object>> variablesAfter,
            String pageQuery,
            Function<JsonNode, JsonNode> pageOf)
            throws InterruptedException {
        List<JsonNode> nodes = new ArrayList<>();
        JsonNode page = firstPage;
        while (true) {
            for (JsonNode node : page.path("nodes")) {
                if (node.isNull()) {
                    LOG.warn("GitHub returned an empty node in a list; it is left out");
                } else {
                    nodes.add(node);
                }
            }
            JsonNode pageInfo = page.path("pageInfo");
            if (!pageInfo.path("hasNextPage").asBoolean()) {
                return nodes;
            }
            page = pageOf.apply(api.graphQl(
                    pageQuery, variablesAfter.apply(pageInfo.path("endCursor").asString())));
        }
    }

    private static Map<String, Object> withAfter(Map<String, Object> variables, String after) {
        Map<String, Object> withAfter = new HashMap<>(variables);
        withAfter.put("after", after);
        return withAfter;
    }
}
