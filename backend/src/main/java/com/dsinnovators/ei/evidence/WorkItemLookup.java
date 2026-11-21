package com.dsinnovators.ei.evidence;

import com.dsinnovators.ei.source.openproject.StoredWorkPackages;
import com.dsinnovators.ei.source.openproject.WorkPackageFacts;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** What a builder asks of stored work items, each answered once per build. */
final class WorkItemLookup {

    private final StoredWorkPackages storedWorkPackages;
    private final Map<Long, Optional<WorkPackageFacts>> facts = new HashMap<>();
    private final Map<Long, Optional<Long>> nearestFeatures = new HashMap<>();
    private final Map<Long, Optional<Instant>> firstDoneAt = new HashMap<>();

    WorkItemLookup(StoredWorkPackages storedWorkPackages) {
        this.storedWorkPackages = storedWorkPackages;
    }

    Optional<WorkPackageFacts> facts(long number) {
        return facts.computeIfAbsent(number, storedWorkPackages::facts);
    }

    /** The work item itself when it is a Feature, else its nearest Feature ancestor. */
    Optional<Long> nearestFeature(long number) {
        return nearestFeatures.computeIfAbsent(number, storedWorkPackages::nearestFeature);
    }

    Optional<Instant> firstDoneAt(long number) {
        return firstDoneAt.computeIfAbsent(number, storedWorkPackages::firstDoneAt);
    }

    /** The facts of a work item that must be stored because something else led to it. */
    WorkPackageFacts requiredFacts(long number) {
        return facts(number).orElseThrow(() -> new IllegalStateException("Work item " + number + " is not stored"));
    }
}
