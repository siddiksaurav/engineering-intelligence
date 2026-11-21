package com.dsinnovators.ei.api;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.evidence.EvidenceItem;
import com.dsinnovators.ei.evidence.EvidenceType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * One evidence item as the web shows it. The title and the links are read from the type-specific
 * {@code fields}, whose shapes the builders of the {@code evidence} package write.
 */
record EvidenceItemResponse(
        long id,
        String type,
        LocalDate date,
        String quarter,
        String title,
        String sourceUrl,
        List<ItemLink> links,
        JsonNode fields) {

    static EvidenceItemResponse of(EvidenceItem item) {
        JsonNode fields = item.getFields();
        return new EvidenceItemResponse(
                item.getId(),
                item.getEvidenceType().databaseName(),
                item.getOccurredOn(),
                Quarter.of(item.getOccurredOn()).toString(),
                title(item.getEvidenceType(), fields),
                item.getSourceUrl(),
                links(item.getEvidenceType(), fields),
                fields);
    }

    private static String title(EvidenceType type, JsonNode fields) {
        return switch (type) {
            case FEATURE_WORK -> fields.required("feature").required("title").stringValue();
            case BUG_FIX -> fields.required("bug").required("title").stringValue();
            case REVIEW_COMMENT_WRITTEN ->
                fields.required("pullRequest").required("title").stringValue();
        };
    }

    private static List<ItemLink> links(EvidenceType type, JsonNode fields) {
        List<ItemLink> links = new ArrayList<>();
        switch (type) {
            case FEATURE_WORK -> {
                links.add(workItemLink(fields.required("feature")));
                fields.required("pullRequests").forEach(pullRequest -> links.add(pullRequestLink(pullRequest)));
            }
            case BUG_FIX -> {
                links.add(workItemLink(fields.required("bug")));
                fields.required("pullRequests").forEach(pullRequest -> links.add(pullRequestLink(pullRequest)));
            }
            case REVIEW_COMMENT_WRITTEN -> {
                links.add(new ItemLink(
                        "Comment on GitHub",
                        fields.required("comment").required("url").stringValue()));
                links.add(pullRequestLink(fields.required("pullRequest")));
            }
        }
        return links;
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
