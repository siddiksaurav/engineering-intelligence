package com.dsinnovators.ei.source.github;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Small GitHub answers built in a test, for the cases the recorded pull request does not have. */
final class GitHubResponses {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private GitHubResponses() {}

    static ObjectNode node(String json) {
        return (ObjectNode) JSON.readTree(json);
    }

    static ObjectNode connection(boolean hasNextPage, String endCursor, JsonNode... nodes) {
        ObjectNode connection = JSON.createObjectNode();
        connection.putObject("pageInfo").put("hasNextPage", hasNextPage).put("endCursor", endCursor);
        ArrayNode list = connection.putArray("nodes");
        for (JsonNode node : nodes) {
            list.add(node);
        }
        return connection;
    }

    /** A merged pull request of opf/openproject with one file and no reviews or comments. */
    static ObjectNode pullRequest(int number) {
        String url = "https://github.com/opf/openproject/pull/" + number;
        ObjectNode pullRequest = node("{\"databaseId\":" + number + ",\"number\":" + number + ",\"url\":\"" + url
                + "\",\"title\":\"T\",\"body\":\"B\",\"author\":{\"login\":\"dev\",\"__typename\":\"User\","
                + "\"databaseId\":5},\"baseRefName\":\"dev\",\"headRefName\":\"feature\","
                + "\"createdAt\":\"2026-07-01T08:00:00Z\",\"mergedAt\":\"2026-07-02T08:00:00Z\",\"mergedBy\":"
                + "{\"login\":\"dev\",\"__typename\":\"User\",\"databaseId\":5},\"isDraft\":false,"
                + "\"repository\":{\"nameWithOwner\":\"opf/openproject\"}}");
        pullRequest.set("labels", connection(false, null));
        pullRequest.set(
                "files",
                connection(
                        false,
                        null,
                        node("{\"path\":\"a.rb\",\"additions\":1,\"deletions\":0,\"changeType\":\"ADDED\"}")));
        pullRequest.set("reviews", connection(false, null));
        pullRequest.set("reviewThreads", connection(false, null));
        pullRequest.set("comments", connection(false, null));
        return pullRequest;
    }

    /** The answer to the pull request query. */
    static String answer(JsonNode pullRequest) {
        ObjectNode answer = JSON.createObjectNode();
        answer.putObject("data").putObject("repository").set("pullRequest", pullRequest);
        return answer.toString();
    }

    /** The answer to a query for a further page of a part of a pull request. */
    static String page(JsonNode connection) {
        ObjectNode pullRequest = JSON.createObjectNode();
        pullRequest.set("page", connection);
        return answer(pullRequest);
    }

    /** The REST list of files of {@link #pullRequest(int)}. */
    static String diffFiles() {
        return "[{\"filename\":\"a.rb\",\"status\":\"added\",\"patch\":\"@@ -0,0 +1 @@\"}]";
    }

    /** A search answer that finds the given numbers on one page. */
    static String search(int... numbers) {
        ObjectNode search = JSON.createObjectNode();
        ObjectNode result = search.putObject("data").putObject("search");
        result.put("issueCount", numbers.length);
        result.putObject("pageInfo").put("hasNextPage", false).putNull("endCursor");
        ArrayNode nodes = result.putArray("nodes");
        for (int number : numbers) {
            nodes.addObject().put("number", number);
        }
        return search.toString();
    }

    static String notFound(int number) {
        return "{\"data\":{\"repository\":{\"pullRequest\":null}},\"errors\":[{\"type\":\"NOT_FOUND\","
                + "\"message\":\"Could not resolve to a PullRequest with the number of " + number + ".\"}]}";
    }
}
