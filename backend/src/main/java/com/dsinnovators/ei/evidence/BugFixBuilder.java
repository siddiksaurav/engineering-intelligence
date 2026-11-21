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
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Builds the Bug fix items by rule: one per Bug and PR author, holding that author's pull requests
 * that link the Bug (decision 103). A Bug joins the build once it has a first done day (decision 114).
 */
@Component
class BugFixBuilder {

    private final StoredWorkPackages storedWorkPackages;

    BugFixBuilder(StoredWorkPackages storedWorkPackages) {
        this.storedWorkPackages = storedWorkPackages;
    }

    /**
     * @param linkedPullRequests what {@link LinkedPullRequests#read} returned for this run
     * @param doneStatusesQuarter the quarter whose done statuses apply (decision 125)
     */
    BuildResult build(List<LinkedPullRequest> linkedPullRequests, Quarter doneStatusesQuarter) {
        WorkItemLookup workItems = new WorkItemLookup(storedWorkPackages, doneStatusesQuarter);
        List<BuiltItem> items = new ArrayList<>();
        List<BuilderFailure> failures = new ArrayList<>();
        fixes(linkedPullRequests, workItems).forEach((group, fix) -> {
            Optional<Instant> doneAt = workItems.firstDoneAt(group.bug());
            if (doneAt.isEmpty()) {
                return;
            }
            if (fix.personId().isEmpty()) {
                failures.add(new BuilderFailure(
                        key(group.bug(), fix.authorLogin()), "no person for GitHub account " + fix.authorLogin()));
                return;
            }
            items.add(item(workItems, group.bug(), fix, fix.personId().get(), doneAt.get()));
        });
        return new BuildResult(items, failures);
    }

    private Map<Group, Fix> fixes(List<LinkedPullRequest> linkedPullRequests, WorkItemLookup workItems) {
        Map<Group, Fix> fixes = new LinkedHashMap<>();
        for (LinkedPullRequest linked : linkedPullRequests) {
            for (long number : linked.workItems()) {
                if (workItems.facts(number).filter(WorkPackageFacts::isBug).isPresent()) {
                    fixes.computeIfAbsent(
                                    new Group(number, linked.authorKey()),
                                    group -> new Fix(linked.authorLogin(), linked.personId()))
                            .pullRequests
                            .putIfAbsent(linked.pullRequest().externalId(), linked.pullRequest());
                }
            }
        }
        return fixes;
    }

    private BuiltItem item(WorkItemLookup workItems, long bugNumber, Fix fix, long personId, Instant doneAt) {
        WorkPackageFacts bug = workItems.requiredFacts(bugNumber);
        ObjectNode bugNode = JsonNodeFactory.instance.objectNode();
        bugNode.put("number", bug.number());
        bugNode.put("title", bug.title());
        bugNode.put("url", bug.url());
        bugNode.put("priority", bug.priority());
        ObjectNode fields = JsonNodeFactory.instance.objectNode();
        fields.set("bug", bugNode);
        workItems
                .nearestFeature(bugNumber)
                .ifPresentOrElse(feature -> fields.put("underFeature", feature), () -> fields.putNull("underFeature"));
        fields.set("pullRequests", ItemFields.pullRequests(fix.pullRequests.values()));
        fields.put("doneAt", doneAt.toString());
        List<EvidenceItemSource> sources = new ArrayList<>();
        fix.pullRequests
                .values()
                .forEach(pullRequest ->
                        sources.add(new EvidenceItemSource(pullRequest.rawRecordId(), SourceRole.PULL_REQUEST)));
        sources.add(new EvidenceItemSource(bug.rawRecordId(), SourceRole.WORK_PACKAGE));
        return new BuiltItem(
                key(bugNumber, String.valueOf(personId)),
                EvidenceType.BUG_FIX,
                personId,
                bug.projectId(),
                doneAt.atOffset(ZoneOffset.UTC).toLocalDate(),
                bug.url(),
                fields,
                sources);
    }

    private static String key(long bug, String author) {
        return "bug_fix:" + bug + ":" + author;
    }

    private record Group(long bug, String authorKey) {}

    private record Fix(String authorLogin, Optional<Long> personId, Map<String, PullRequestFacts> pullRequests) {

        Fix(String authorLogin, Optional<Long> personId) {
            this(authorLogin, personId, new LinkedHashMap<>());
        }
    }
}
