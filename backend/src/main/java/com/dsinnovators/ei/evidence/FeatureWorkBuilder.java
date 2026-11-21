package com.dsinnovators.ei.evidence;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.source.github.PullRequestFacts;
import com.dsinnovators.ei.source.openproject.StoredWorkPackages;
import com.dsinnovators.ei.source.openproject.WorkPackageFacts;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Builds the Feature work items by rule: one per Feature and PR author, holding the non-Bug work
 * items that the author's pull requests link under that Feature and those pull requests (design
 * "Main flows" step 3). A Feature joins the build once it has a first done day (decisions 112, 113).
 */
@Component
class FeatureWorkBuilder {

    private final StoredWorkPackages storedWorkPackages;

    FeatureWorkBuilder(StoredWorkPackages storedWorkPackages) {
        this.storedWorkPackages = storedWorkPackages;
    }

    /**
     * @param linkedPullRequests what {@link LinkedPullRequests#read} returned for this run
     * @param doneStatusesQuarter the quarter whose done statuses apply (decision 125)
     */
    BuildResult build(List<LinkedPullRequest> linkedPullRequests, Quarter doneStatusesQuarter) {
        WorkItemLookup workItems = new WorkItemLookup(storedWorkPackages, doneStatusesQuarter);
        List<BuiltItem> items = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        groups(linkedPullRequests, workItems).forEach((group, members) -> {
            Optional<Instant> doneAt = workItems.firstDoneAt(group.feature());
            if (doneAt.isEmpty()) {
                return;
            }
            if (members.personId().isEmpty()) {
                failures.add(key(group.feature(), members.authorLogin()) + ": no person for GitHub account "
                        + members.authorLogin());
                return;
            }
            items.add(
                    item(workItems, group.feature(), members, members.personId().get(), doneAt.get()));
        });
        return new BuildResult(items, failures);
    }

    private Map<Group, Members> groups(List<LinkedPullRequest> linkedPullRequests, WorkItemLookup workItems) {
        Map<Group, Members> groups = new LinkedHashMap<>();
        for (LinkedPullRequest linked : linkedPullRequests) {
            for (long number : linked.workItems()) {
                Optional<WorkPackageFacts> facts = workItems.facts(number).filter(candidate -> !candidate.isBug());
                Optional<Long> feature = facts.flatMap(candidate -> workItems.nearestFeature(number));
                if (feature.isPresent()) {
                    groups.computeIfAbsent(
                                    new Group(feature.get(), linked.authorKey()),
                                    group -> new Members(linked.authorLogin(), linked.personId()))
                            .add(linked.pullRequest(), facts.get());
                }
            }
        }
        return groups;
    }

    private static BuiltItem item(
            WorkItemLookup workItems, long featureNumber, Members members, long personId, Instant doneAt) {
        WorkPackageFacts feature = workItems.requiredFacts(featureNumber);
        ObjectNode fields = JsonNodeFactory.instance.objectNode();
        fields.set("feature", ItemFields.workItem(feature));
        fields.set("tasks", ItemFields.workItems(members.tasks.values()));
        fields.set("pullRequests", ItemFields.pullRequests(members.pullRequests.values()));
        fields.put("doneAt", doneAt.toString());
        List<EvidenceItemSource> sources = new ArrayList<>();
        members.pullRequests
                .values()
                .forEach(pullRequest ->
                        sources.add(new EvidenceItemSource(pullRequest.rawRecordId(), SourceRole.PULL_REQUEST)));
        sources.add(new EvidenceItemSource(feature.rawRecordId(), SourceRole.WORK_PACKAGE));
        members.tasks.values().stream()
                .filter(task -> task.number() != featureNumber)
                .forEach(task -> sources.add(new EvidenceItemSource(task.rawRecordId(), SourceRole.WORK_PACKAGE)));
        return new BuiltItem(
                key(featureNumber, String.valueOf(personId)),
                EvidenceType.FEATURE_WORK,
                personId,
                feature.projectId(),
                doneAt.atOffset(ZoneOffset.UTC).toLocalDate(),
                feature.url(),
                fields,
                sources);
    }

    private static String key(long feature, String author) {
        return "feature_work:" + feature + ":" + author;
    }

    private record Group(long feature, String authorKey) {}

    /** The pull requests and work items one author brought to one Feature. */
    private static final class Members {

        private final String authorLogin;
        private final Optional<Long> personId;
        private final Map<String, PullRequestFacts> pullRequests = new LinkedHashMap<>();
        private final Map<Long, WorkPackageFacts> tasks = new TreeMap<>();

        Members(String authorLogin, Optional<Long> personId) {
            this.authorLogin = authorLogin;
            this.personId = personId;
        }

        String authorLogin() {
            return authorLogin;
        }

        Optional<Long> personId() {
            return personId;
        }

        void add(PullRequestFacts pullRequest, WorkPackageFacts task) {
            pullRequests.putIfAbsent(pullRequest.externalId(), pullRequest);
            tasks.putIfAbsent(task.number(), task);
        }
    }
}
