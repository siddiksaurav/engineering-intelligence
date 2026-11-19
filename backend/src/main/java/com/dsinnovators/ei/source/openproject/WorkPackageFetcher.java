package com.dsinnovators.ei.source.openproject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Reads work packages, the tree under a Feature, activities, linked pull request lists and projects from
 * OpenProject. A collection is read page by page until every element it announces has been read.
 */
public class WorkPackageFetcher {

    private final OpenProjectApi api;
    private final OpenProjectProperties properties;

    WorkPackageFetcher(OpenProjectApi api, OpenProjectProperties properties) {
        this.api = api;
        this.properties = properties;
    }

    /**
     * Fetches work packages by number in collection calls of up to one page of numbers. A number that is
     * not visible is missing from the result.
     *
     * @throws OpenProjectApiException when a call fails after its retries or an answer is malformed
     */
    List<JsonNode> fetchWorkPackages(Collection<Long> numbers) throws InterruptedException {
        List<String> references =
                numbers.stream().distinct().map(String::valueOf).toList();
        List<JsonNode> workPackages = new ArrayList<>();
        for (int start = 0; start < references.size(); start += properties.pageSize()) {
            List<String> batch = references.subList(start, Math.min(start + properties.pageSize(), references.size()));
            workPackages.addAll(readAllPages(WorkPackageCollectionPath.withIds(batch, properties.pageSize())));
        }
        return workPackages;
    }

    /** Fetches every work package under the given one, at any depth, across all pages. */
    List<JsonNode> fetchTreeUnder(long number) throws InterruptedException {
        return readAllPages(WorkPackageCollectionPath.underAncestor(number, properties.pageSize()));
    }

    /** Fetches a work package's activities and its reduced pull request list. */
    FetchedWorkPackage fetchWithDetails(JsonNode workPackage) throws InterruptedException {
        JsonNode id = workPackage.path("id");
        if (!id.isIntegralNumber()) {
            throw new OpenProjectApiException("A work package has no numeric id");
        }
        long number = id.asLong();
        JsonNode activities = api.get("/work_packages/" + number + "/activities");
        OpenProjectAnswers.elementsOf(activities, "the activities of work package " + number);
        JsonNode pullRequestList =
                PullRequestListReducer.reduce(api.get("/work_packages/" + number + "/github_pull_requests"));
        return new FetchedWorkPackage(number, workPackage, activities, pullRequestList);
    }

    /** Fetches a project as OpenProject returns it. */
    JsonNode fetchProject(long projectId) throws InterruptedException {
        return api.get("/projects/" + projectId);
    }

    private List<JsonNode> readAllPages(String firstPagePath) throws InterruptedException {
        List<JsonNode> elements = new ArrayList<>();
        for (int page = 1; ; page++) {
            JsonNode answer = api.get(page == 1 ? firstPagePath : firstPagePath + "&offset=" + page);
            JsonNode pageElements = OpenProjectAnswers.elementsOf(answer, firstPagePath);
            JsonNode total = answer.path("total");
            if (!total.isIntegralNumber()) {
                throw new OpenProjectApiException("OpenProject's answer to " + firstPagePath + " has no total");
            }
            for (JsonNode element : pageElements) {
                OpenProjectAnswers.requireWorkPackage(element, firstPagePath);
                elements.add(element);
            }
            if (pageElements.isEmpty() || elements.size() >= total.asInt()) {
                return elements;
            }
        }
    }
}
