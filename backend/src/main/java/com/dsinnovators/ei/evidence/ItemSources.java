package com.dsinnovators.ei.evidence;

import com.dsinnovators.ei.source.github.PullRequestDiff;
import com.dsinnovators.ei.source.github.PullRequestFacts;
import com.dsinnovators.ei.source.openproject.WorkPackageFacts;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** The source records of the items that rest on one pull request (design "New evidence types"). */
final class ItemSources {

    private ItemSources() {}

    /** The pull request, its diff, and the work items it links, each as the exact record version read. */
    static List<EvidenceItemSource> ofPullRequest(
            PullRequestFacts pullRequest, PullRequestDiff diff, Collection<WorkPackageFacts> linkedWorkItems) {
        List<EvidenceItemSource> sources = new ArrayList<>();
        sources.add(new EvidenceItemSource(pullRequest.rawRecordId(), SourceRole.PULL_REQUEST));
        sources.add(new EvidenceItemSource(diff.recordId(), SourceRole.PULL_REQUEST_DIFF));
        linkedWorkItems.forEach(
                workItem -> sources.add(new EvidenceItemSource(workItem.rawRecordId(), SourceRole.WORK_PACKAGE)));
        return sources;
    }
}
