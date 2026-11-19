package com.dsinnovators.ei.source.openproject;

import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Reduces OpenProject's list of linked pull requests to the fields that do not change on their own
 * (decision 69). Counters, update times and check runs change with every call, so a stored copy that
 * kept them would be a new version at every run.
 */
final class PullRequestListReducer {

    private static final List<String> KEPT_FIELDS = List.of(
            "id", "number", "repository", "htmlUrl", "title", "state", "draft", "merged", "mergedAt", "createdAt");

    private PullRequestListReducer() {}

    /**
     * @param answer the collection answer of {@code /work_packages/{id}/github_pull_requests}
     * @return {@code {"reduced": true, "pullRequests": [...]}} in the order of the answer, each entry
     *     with the kept fields and {@code githubUser} (the login)
     * @throws OpenProjectApiException when the answer has no list of elements or an entry has no
     *     numeric {@code number} or no {@code repository}
     */
    static JsonNode reduce(JsonNode answer) {
        JsonNode elements = OpenProjectAnswers.elementsOf(answer, "the pull request list");
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ArrayNode pullRequests = factory.arrayNode();
        for (JsonNode element : elements) {
            pullRequests.add(reduceEntry(element, factory));
        }
        ObjectNode reduced = factory.objectNode();
        reduced.put("reduced", true);
        reduced.set("pullRequests", pullRequests);
        return reduced;
    }

    private static ObjectNode reduceEntry(JsonNode element, JsonNodeFactory factory) {
        requireIdentity(element);
        ObjectNode entry = factory.objectNode();
        for (String field : KEPT_FIELDS) {
            entry.set(field, orNull(element.path(field), factory));
        }
        entry.set(
                "githubUser",
                orNull(element.path("_embedded").path("githubUser").path("login"), factory));
        return entry;
    }

    private static void requireIdentity(JsonNode element) {
        if (!element.path("number").isIntegralNumber()) {
            throw malformed(element, "\"number\"");
        }
        if (element.path("repository").asString("").isBlank()) {
            throw malformed(element, "\"repository\"");
        }
    }

    private static OpenProjectApiException malformed(JsonNode element, String field) {
        return new OpenProjectApiException("OpenProject's answer to the pull request list has entry "
                + element.path("id").asString("unknown") + " without " + field);
    }

    private static JsonNode orNull(JsonNode value, JsonNodeFactory factory) {
        return value.isMissingNode() ? factory.nullNode() : value;
    }
}
