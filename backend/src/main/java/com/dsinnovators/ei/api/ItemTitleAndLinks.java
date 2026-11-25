package com.dsinnovators.ei.api;

import com.dsinnovators.ei.evidence.EvidenceItem;
import com.dsinnovators.ei.evidence.EvidenceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * Reads the title and the links of an evidence item from its type-specific {@code fields}, whose
 * shapes the builders of the {@code evidence} package write.
 */
final class ItemTitleAndLinks {

    private ItemTitleAndLinks() {}

    static String titleOf(EvidenceItem item) {
        EvidenceType type = item.getEvidenceType();
        JsonNode fields = item.getFields();
        return switch (type) {
            case FEATURE_WORK ->
                hasNoWorkItem(fields)
                        ? firstPullRequest(fields).required("title").stringValue()
                        : fields.required("feature").required("title").stringValue();
            case BUG_FIX ->
                hasNoWorkItem(fields)
                        ? firstPullRequest(fields).required("title").stringValue()
                        : fields.required("bug").required("title").stringValue();
            case REVIEW_COMMENT_WRITTEN, TEST_CHANGE, REFACTOR_PERFORMANCE, SECURITY, INFRA_RELEASE, DOCUMENTATION ->
                fields.required("pullRequest").required("title").stringValue();
        };
    }

    static List<ItemLink> linksOf(EvidenceItem item) {
        EvidenceType type = item.getEvidenceType();
        JsonNode fields = item.getFields();
        List<ItemLink> links = new ArrayList<>();
        switch (type) {
            case FEATURE_WORK -> {
                workItemLinkOf(item).ifPresent(links::add);
                links.addAll(pullRequestLinksOf(item));
            }
            case BUG_FIX -> {
                if (!hasNoWorkItem(fields)) {
                    links.add(workItemLink(fields.required("bug")));
                }
                fields.required("pullRequests").forEach(pullRequest -> links.add(pullRequestLink(pullRequest)));
            }
            case REVIEW_COMMENT_WRITTEN -> {
                links.add(new ItemLink(
                        "Comment on GitHub",
                        fields.required("comment").required("url").stringValue()));
                links.add(pullRequestLink(fields.required("pullRequest")));
            }
            case TEST_CHANGE, REFACTOR_PERFORMANCE, SECURITY, INFRA_RELEASE, DOCUMENTATION ->
                links.add(pullRequestLink(fields.required("pullRequest")));
        }
        return links;
    }

    /** The link to the OpenProject work item of a Feature work item; none for an item with no work item. */
    static Optional<ItemLink> workItemLinkOf(EvidenceItem item) {
        JsonNode fields = item.getFields();
        return hasNoWorkItem(fields) ? Optional.empty() : Optional.of(workItemLink(fields.required("feature")));
    }

    /** The links to the pull requests of a Feature work item, in the order of its fields. */
    static List<ItemLink> pullRequestLinksOf(EvidenceItem item) {
        List<ItemLink> links = new ArrayList<>();
        item.getFields().required("pullRequests").forEach(pullRequest -> links.add(pullRequestLink(pullRequest)));
        return links;
    }

    /** A Feature work or Bug fix item of a pull request with no usable work item has no work item in its fields. */
    private static boolean hasNoWorkItem(JsonNode fields) {
        return fields.path("noWorkItem").asBoolean(false);
    }

    private static JsonNode firstPullRequest(JsonNode fields) {
        return fields.required("pullRequests").get(0);
    }

    private static ItemLink workItemLink(JsonNode workItem) {
        return new ItemLink(
                "Work item #" + workItem.required("number").asLong() + " on OpenProject",
                workItem.required("url").stringValue());
    }

    private static ItemLink pullRequestLink(JsonNode pullRequest) {
        return new ItemLink(
                "Pull request #" + pullRequest.required("number").asLong() + " on GitHub",
                pullRequest.required("url").stringValue());
    }
}
