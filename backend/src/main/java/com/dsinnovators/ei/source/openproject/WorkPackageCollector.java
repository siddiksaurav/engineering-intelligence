package com.dsinnovators.ei.source.openproject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Gathers the work packages to store for a set of referenced ones (F03 design, step 4): the
 * referenced work packages, their ancestors, their direct children and the whole tree under every
 * Feature among these. A parent the API does not show ends the walk up; it is not an error.
 */
@Component
class WorkPackageCollector {

    private final WorkPackageFetcher fetcher;

    WorkPackageCollector(WorkPackageFetcher fetcher) {
        this.fetcher = fetcher;
    }

    /**
     * @param referenced numbers of work packages that pull requests name
     * @param run the run, which already holds what earlier calls collected; that is not fetched again
     * @return the work packages added to the run by this call, as OpenProject returned them, referenced
     *     ones first; a number that is not visible is missing
     * @throws OpenProjectApiException when a call fails after its retries or an answer is malformed
     */
    List<JsonNode> collect(Collection<Long> referenced, ImportRun run) throws InterruptedException {
        Map<Long, JsonNode> collected = run.collected();
        List<JsonNode> added = new ArrayList<>();
        add(collected, added, fetcher.fetchWorkPackages(run.claimUnrequested(referenced)));
        add(collected, added, fetcher.fetchWorkPackages(run.claimUnrequested(relatedTo(collected))));
        for (JsonNode feature : features(collected)) {
            long number = feature.path("id").asLong();
            if (run.claimTreeOf(number)) {
                add(collected, added, fetcher.fetchTreeUnder(number));
            }
        }
        return added;
    }

    private static Set<Long> relatedTo(Map<Long, JsonNode> collected) {
        Set<Long> related = new LinkedHashSet<>();
        for (JsonNode workPackage : collected.values()) {
            JsonNode links = workPackage.path("_links");
            linkedNumbers(links.path("ancestors"), related);
            linkedNumbers(links.path("children"), related);
        }
        return related;
    }

    private static void linkedNumbers(JsonNode links, Set<Long> numbers) {
        for (JsonNode link : links) {
            HrefNumbers.trailingNumber(link.path("href").asString("")).ifPresent(numbers::add);
        }
    }

    private static List<JsonNode> features(Map<Long, JsonNode> collected) {
        List<JsonNode> features = new ArrayList<>();
        for (JsonNode workPackage : collected.values()) {
            if (WorkPackageType.isFeature(workPackage)) {
                features.add(workPackage);
            }
        }
        return features;
    }

    private static void add(Map<Long, JsonNode> collected, List<JsonNode> added, List<JsonNode> workPackages) {
        for (JsonNode workPackage : workPackages) {
            if (collected.putIfAbsent(workPackage.path("id").asLong(), workPackage) == null) {
                added.add(workPackage);
            }
        }
    }
}
