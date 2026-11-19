package com.dsinnovators.ei.source.openproject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * What one {@code openproject-import} run has found, resolved, collected and stored so far. The
 * completion rounds of the run share it, so that a work package is fetched and stored once per run
 * and every link is written with all the work packages of the run in view.
 */
final class ImportRun {

    private final Map<String, List<WorkPackageReference>> referencesByPullRequest = new LinkedHashMap<>();
    private final Map<String, ResolvedReference> resolved = new LinkedHashMap<>();
    private final Map<Long, JsonNode> collected = new LinkedHashMap<>();
    private final Set<Long> requested = new HashSet<>();
    private final Set<Long> featuresWithTreeCollected = new HashSet<>();
    private final List<FetchedWorkPackage> stored = new ArrayList<>();
    private final List<Long> failedNumbers = new ArrayList<>();

    /** The references found in each pull request of the run, by its F02 external id. */
    Map<String, List<WorkPackageReference>> referencesByPullRequest() {
        return referencesByPullRequest;
    }

    /** The references resolved so far, by reference. */
    Map<String, ResolvedReference> resolved() {
        return resolved;
    }

    /** The work packages collected so far as OpenProject returned them, by number. */
    Map<Long, JsonNode> collected() {
        return collected;
    }

    /** The numbers that no earlier call of the run asked for, which are now taken; a hidden one is asked once. */
    List<Long> claimUnrequested(Collection<Long> numbers) {
        return numbers.stream().filter(requested::add).toList();
    }

    /** Claims the collection of the tree under a Feature; false when an earlier round took it. */
    boolean claimTreeOf(long feature) {
        return featuresWithTreeCollected.add(feature);
    }

    void add(WorkPackageImporter.Imported imported) {
        stored.addAll(imported.stored());
        failedNumbers.addAll(imported.failedNumbers());
    }

    /** The work packages stored or found unchanged in this run. */
    List<FetchedWorkPackage> stored() {
        return stored;
    }

    List<Long> failedNumbers() {
        return failedNumbers;
    }

    int totalWorkPackages() {
        return stored.size() + failedNumbers.size();
    }
}
