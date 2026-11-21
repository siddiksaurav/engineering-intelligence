package com.dsinnovators.ei.evidence;

import com.dsinnovators.ei.source.github.PullRequestFacts;
import com.dsinnovators.ei.source.openproject.WorkPackageFacts;
import java.util.Collection;
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

    static ArrayNode pullRequests(Collection<PullRequestFacts> pullRequests) {
        ArrayNode nodes = JSON.arrayNode();
        for (PullRequestFacts pullRequest : pullRequests) {
            ObjectNode node = nodes.addObject();
            node.put("externalId", pullRequest.externalId());
            node.put("number", pullRequest.number());
            node.put("title", pullRequest.title());
            node.put("url", pullRequest.url());
            node.put("mergedAt", pullRequest.mergedAt().toString());
        }
        return nodes;
    }
}
