package com.dsinnovators.ei.source.openproject;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * A work package as OpenProject gave it, with what hangs on it.
 *
 * @param number the work package number
 * @param workPackage the collection element, unchanged
 * @param activities the answer of the activities call, unchanged
 * @param pullRequestList the reduced list of linked pull requests (decision 69)
 */
record FetchedWorkPackage(long number, JsonNode workPackage, JsonNode activities, JsonNode pullRequestList) {

    /** The F02 external ids, such as {@code opf/openproject#24204}, of the pull requests OpenProject lists on it. */
    List<String> linkedPullRequestExternalIds() {
        List<String> externalIds = new ArrayList<>();
        for (JsonNode pullRequest : pullRequestList.path("pullRequests")) {
            externalIds.add(pullRequest.path("repository").asString() + "#"
                    + pullRequest.path("number").asString());
        }
        return externalIds;
    }
}
