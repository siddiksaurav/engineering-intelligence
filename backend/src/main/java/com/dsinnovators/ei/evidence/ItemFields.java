package com.dsinnovators.ei.evidence;

import com.dsinnovators.ei.source.github.PullRequestFacts;
import com.dsinnovators.ei.source.openproject.WorkPackageFacts;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/** The JSON pieces that the type-specific fields of an evidence item share (design "Tables"). */
final class ItemFields {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private ItemFields() {}

    static ObjectNode workItem(WorkPackageFacts facts) {
        ObjectNode node = JSON.objectNode();
        node.put("number", facts.number());
        node.put("title", facts.title());
        node.put("type", facts.type());
        node.put("url", facts.url());
        return node;
    }

    static ArrayNode workItems(Collection<WorkPackageFacts> facts) {
        ArrayNode nodes = JSON.arrayNode();
        facts.forEach(workItem -> nodes.add(workItem(workItem)));
        return nodes;
    }

    /** The fields every item of one pull request starts with: the pull request and the work items it links. */
    static ObjectNode ofPullRequest(PullRequestFacts pullRequest, Collection<WorkPackageFacts> linkedWorkItems) {
        ObjectNode node = JSON.objectNode();
        node.set("pullRequest", pullRequest(pullRequest));
        node.set("linkedWorkItems", workItems(linkedWorkItems));
        return node;
    }

    /** The fields of an item of a pull request that no usable work item stands behind. */
    static ObjectNode ofPullRequestWithoutWorkItem(PullRequestFacts pullRequest) {
        ObjectNode node = JSON.objectNode();
        node.put("noWorkItem", true);
        node.set("pullRequests", pullRequests(List.of(pullRequest)));
        return node;
    }

    /** The fields of the Feature work of a work item that has no Feature above it: the item stands in for the Feature. */
    static ObjectNode ofWorkItemAlone(
            WorkPackageFacts workItem, Collection<PullRequestFacts> pullRequests, Instant doneAt) {
        ObjectNode node = JSON.objectNode();
        node.set("feature", workItem(workItem));
        node.set("tasks", workItems(List.of(workItem)));
        node.set("pullRequests", pullRequests(pullRequests));
        node.put("doneAt", doneAt.toString());
        return node;
    }

    static ArrayNode texts(Collection<String> texts) {
        ArrayNode nodes = JSON.arrayNode();
        texts.forEach(nodes::add);
        return nodes;
    }

    static ArrayNode pullRequests(Collection<PullRequestFacts> pullRequests) {
        ArrayNode nodes = JSON.arrayNode();
        pullRequests.forEach(pullRequest -> nodes.add(pullRequest(pullRequest)));
        return nodes;
    }

    static ObjectNode pullRequest(PullRequestFacts pullRequest) {
        ObjectNode node = JSON.objectNode();
        node.put("externalId", pullRequest.externalId());
        node.put("number", pullRequest.number());
        node.put("title", pullRequest.title());
        node.put("url", pullRequest.url());
        node.put("mergedAt", pullRequest.mergedAt().toString());
        return node;
    }
}
