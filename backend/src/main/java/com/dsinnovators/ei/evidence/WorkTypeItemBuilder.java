package com.dsinnovators.ei.evidence;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.judgment.PullRequestDigests.CurrentDigest;
import com.dsinnovators.ei.source.github.PullRequestDiff;
import com.dsinnovators.ei.source.github.PullRequestFacts;
import com.dsinnovators.ei.source.github.StoredPullRequestDiffs;
import com.dsinnovators.ei.source.openproject.StoredWorkPackages;
import com.dsinnovators.ei.source.openproject.WorkPackageFacts;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ObjectNode;

/**
 * Builds the items that follow from the work type of a pull request's digest, for the pull requests
 * the tracker does not place (F06 design, "Main flows" 4): Feature work and Bug fix with no work
 * item, Refactor and performance, Security, Infra and release and Documentation items, and the
 * Feature work of a work item that has no Feature above it (decision 149).
 *
 * <p>A pull request that links a work item under a Feature gives nothing here: F05's Feature work
 * group holds it. A pull request that links only Bugs gives nothing here either: F05's Bug fix
 * holds it (decision 141). Every item of one pull request is dated by its merge day (decision 150).
 */
@Component
class WorkTypeItemBuilder {

    private final StoredPullRequestDiffs storedDiffs;
    private final StoredWorkPackages storedWorkPackages;

    WorkTypeItemBuilder(StoredPullRequestDiffs storedDiffs, StoredWorkPackages storedWorkPackages) {
        this.storedDiffs = storedDiffs;
        this.storedWorkPackages = storedWorkPackages;
    }

    /**
     * @param linkedPullRequests what {@link LinkedPullRequests#read} returned for this run
     * @param doneStatusesQuarter the quarter whose done statuses apply (decision 125)
     */
    BuildResult build(List<LinkedPullRequest> linkedPullRequests, Quarter doneStatusesQuarter) {
        Build build = new Build(new WorkItemLookup(storedWorkPackages, doneStatusesQuarter));
        linkedPullRequests.forEach(build::add);
        return build.result();
    }

    private record Group(long workItem, String authorKey) {}

    /** The pull requests of one author on one work item that has no Feature above it. */
    private record Members(
            String authorLogin,
            Optional<Long> personId,
            WorkPackageFacts workItem,
            Map<String, LinkedPullRequest> pullRequests) {}

    /** The state of one build: the items made so far and the groups still to be made. */
    private final class Build {

        private final WorkItemLookup workItems;
        private final List<BuiltItem> items = new ArrayList<>();
        private final List<BuilderFailure> failures = new ArrayList<>();
        private final Map<Group, Members> featureWorkGroups = new LinkedHashMap<>();

        Build(WorkItemLookup workItems) {
            this.workItems = workItems;
        }

        void add(LinkedPullRequest linked) {
            if (linked.digest().isEmpty()) {
                return;
            }
            Optional<List<WorkPackageFacts>> standalone = standaloneItems(linked);
            if (standalone.isEmpty()) {
                return;
            }
            CurrentDigest digest = linked.digest().get();
            switch (digest.digest().workType()) {
                case FEATURE, ENHANCEMENT -> addFeatureWork(linked, digest, standalone.get());
                case FIX ->
                    addItem(
                            EvidenceType.BUG_FIX,
                            "bug_fix:pull_request:",
                            linked,
                            digest,
                            standalone.get(),
                            diff -> ItemFields.ofPullRequestWithoutWorkItem(linked.pullRequest()));
                case REFACTOR, PERFORMANCE ->
                    addItem(EvidenceType.REFACTOR_PERFORMANCE, linked, digest, standalone.get(), diff -> {
                        ObjectNode fields = ItemFields.ofPullRequest(linked.pullRequest(), standalone.get());
                        fields.put("workType", digest.digest().workType().wireName());
                        fields.set(
                                "modulesTouched",
                                ItemFields.texts(digest.digest().modulesTouched()));
                        fields.put("dependencyChanges", digest.digest().dependencyChanges());
                        return fields;
                    });
                case SECURITY ->
                    addItem(EvidenceType.SECURITY, linked, digest, standalone.get(), diff -> {
                        ObjectNode fields = ItemFields.ofPullRequest(linked.pullRequest(), standalone.get());
                        fields.put("kind", digest.digest().securityKind().wireName());
                        fields.putNull("vulnerabilityReference");
                        return fields;
                    });
                case INFRA ->
                    addItem(EvidenceType.INFRA_RELEASE, linked, digest, standalone.get(), diff -> {
                        ObjectNode fields = ItemFields.ofPullRequest(linked.pullRequest(), standalone.get());
                        fields.put("kind", digest.digest().infraKind().wireName());
                        fields.put("scope", digest.digest().infraScope().wireName());
                        return fields;
                    });
                case DOCS ->
                    addItem(EvidenceType.DOCUMENTATION, linked, digest, standalone.get(), diff -> {
                        ObjectNode fields = ItemFields.ofPullRequest(linked.pullRequest(), standalone.get());
                        fields.set(
                                "files",
                                ItemFields.texts(diff.files().stream()
                                        .map(PullRequestDiff.File::path)
                                        .toList()));
                        fields.put("scope", digest.digest().documentationScope().wireName());
                        return fields;
                    });
                case MAINTENANCE, RESEARCH, TECHNICAL_TASK, OPERATIONAL_SUPPORT, TEST -> {
                    // The spec has no evidence type for these work types: the pull request keeps its work type only.
                }
            }
        }

        BuildResult result() {
            featureWorkGroups.forEach(this::addFeatureWorkOfWorkItem);
            return new BuildResult(items, failures);
        }

        /**
         * The non-Bug work items the pull request links when the work type decides what it gives:
         * none when it links nothing usable. Empty when the tracker places the pull request:
         * a link under a Feature, or Bugs only.
         */
        private Optional<List<WorkPackageFacts>> standaloneItems(LinkedPullRequest linked) {
            List<WorkPackageFacts> linkedItems = workItems.linkedFacts(linked.workItems());
            List<WorkPackageFacts> nonBugs =
                    linkedItems.stream().filter(item -> !item.isBug()).toList();
            boolean underAFeature = nonBugs.stream()
                    .anyMatch(item -> workItems.nearestFeature(item.number()).isPresent());
            boolean bugsOnly = nonBugs.isEmpty() && !linkedItems.isEmpty();
            return underAFeature || bugsOnly ? Optional.empty() : Optional.of(nonBugs);
        }

        private void addFeatureWork(LinkedPullRequest linked, CurrentDigest digest, List<WorkPackageFacts> standalone) {
            if (standalone.isEmpty()) {
                addItem(EvidenceType.FEATURE_WORK, "feature_work:pull_request:", linked, digest, standalone, diff -> {
                    ObjectNode fields = ItemFields.ofPullRequestWithoutWorkItem(linked.pullRequest());
                    fields.put("workType", digest.digest().workType().wireName());
                    return fields;
                });
                return;
            }
            for (WorkPackageFacts item : standalone) {
                featureWorkGroups
                        .computeIfAbsent(
                                new Group(item.number(), linked.authorKey()),
                                group -> new Members(
                                        linked.authorLogin(), linked.personId(), item, new LinkedHashMap<>()))
                        .pullRequests()
                        .putIfAbsent(linked.pullRequest().externalId(), linked);
            }
        }

        private void addItem(
                EvidenceType type,
                LinkedPullRequest linked,
                CurrentDigest digest,
                List<WorkPackageFacts> linkedItems,
                Function<PullRequestDiff, ObjectNode> fieldsOf) {
            addItem(type, type.databaseName() + ":", linked, digest, linkedItems, fieldsOf);
        }

        private void addItem(
                EvidenceType type,
                String keyPrefix,
                LinkedPullRequest linked,
                CurrentDigest digest,
                List<WorkPackageFacts> linkedItems,
                Function<PullRequestDiff, ObjectNode> fieldsOf) {
            PullRequestFacts pullRequest = linked.pullRequest();
            String key = keyPrefix + pullRequest.externalId();
            if (linked.personId().isEmpty()) {
                failures.add(linked.noPerson(key));
                return;
            }
            Optional<PullRequestDiff> diff = storedDiffs.latestOf(pullRequest.externalId());
            if (diff.isEmpty()) {
                failures.add(noDiff(key, pullRequest));
                return;
            }
            items.add(new BuiltItem(
                    key,
                    type,
                    linked.personId().get(),
                    null,
                    linked.mergeDay(),
                    pullRequest.url(),
                    fieldsOf.apply(diff.get()),
                    ItemSources.ofPullRequest(pullRequest, diff.get(), linkedItems),
                    List.of(digest)));
        }

        private void addFeatureWorkOfWorkItem(Group group, Members members) {
            Optional<Instant> doneAt = workItems.firstDoneAt(group.workItem());
            if (doneAt.isEmpty()) {
                return;
            }
            WorkPackageFacts workItem = members.workItem();
            String key = "feature_work:" + group.workItem() + ":"
                    + members.personId().map(String::valueOf).orElse(members.authorLogin());
            if (members.personId().isEmpty()) {
                failures.add(new BuilderFailure(key, "no person for GitHub account " + members.authorLogin()));
                return;
            }
            List<PullRequestFacts> pullRequests = new ArrayList<>();
            List<EvidenceItemSource> sources = new ArrayList<>();
            List<CurrentDigest> digests = new ArrayList<>();
            for (LinkedPullRequest linked : members.pullRequests().values()) {
                Optional<PullRequestDiff> diff =
                        storedDiffs.latestOf(linked.pullRequest().externalId());
                if (diff.isEmpty()) {
                    failures.add(noDiff(key, linked.pullRequest()));
                    return;
                }
                pullRequests.add(linked.pullRequest());
                sources.addAll(ItemSources.ofPullRequest(linked.pullRequest(), diff.get(), List.of()));
                digests.add(linked.digest().orElseThrow());
            }
            sources.add(new EvidenceItemSource(workItem.rawRecordId(), SourceRole.WORK_PACKAGE));
            ObjectNode fields = ItemFields.ofWorkItemAlone(workItem, pullRequests, doneAt.get());
            items.add(new BuiltItem(
                    key,
                    EvidenceType.FEATURE_WORK,
                    members.personId().get(),
                    workItem.projectId(),
                    doneAt.get().atOffset(ZoneOffset.UTC).toLocalDate(),
                    workItem.url(),
                    fields,
                    sources,
                    digests));
        }

        private BuilderFailure noDiff(String key, PullRequestFacts pullRequest) {
            return new BuilderFailure(key, "no diff record is stored for " + pullRequest.externalId());
        }
    }
}
