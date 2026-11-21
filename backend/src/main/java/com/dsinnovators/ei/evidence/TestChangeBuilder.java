package com.dsinnovators.ei.evidence;

import com.dsinnovators.ei.common.PathPatterns;
import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.judgment.PullRequestDigests.CurrentDigest;
import com.dsinnovators.ei.setting.SettingService;
import com.dsinnovators.ei.source.github.PullRequestDiff;
import com.dsinnovators.ei.source.github.PullRequestFacts;
import com.dsinnovators.ei.source.github.StoredPullRequestDiffs;
import com.dsinnovators.ei.source.openproject.StoredWorkPackages;
import com.dsinnovators.ei.source.openproject.WorkPackageFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ObjectNode;

/**
 * Builds the Test change items by rule: one per pull request of a human with a digest that changes
 * a file matching the setting {@code evidence.test_paths} (decision 148). The test kind is the
 * digest's; the item is dated by the merge day (decision 150) and has no project (decision 139).
 */
@Component
class TestChangeBuilder {

    private static final String TEST_PATHS_SETTING = "evidence.test_paths";

    private final StoredPullRequestDiffs storedDiffs;
    private final StoredWorkPackages storedWorkPackages;
    private final SettingService settings;

    TestChangeBuilder(
            StoredPullRequestDiffs storedDiffs, StoredWorkPackages storedWorkPackages, SettingService settings) {
        this.storedDiffs = storedDiffs;
        this.storedWorkPackages = storedWorkPackages;
        this.settings = settings;
    }

    /**
     * @param linkedPullRequests what {@link LinkedPullRequests#read} returned for this run
     * @param quarter the quarter whose settings apply
     */
    BuildResult build(List<LinkedPullRequest> linkedPullRequests, Quarter quarter) {
        PathPatterns testPaths = new PathPatterns(settings.getTextList(TEST_PATHS_SETTING, quarter));
        WorkItemLookup workItems = new WorkItemLookup(storedWorkPackages, quarter);
        List<BuiltItem> items = new ArrayList<>();
        List<BuilderFailure> failures = new ArrayList<>();
        for (LinkedPullRequest linked : linkedPullRequests) {
            Optional<CurrentDigest> digest = linked.digest();
            if (digest.isEmpty()) {
                continue;
            }
            String key = key(linked.pullRequest());
            Optional<PullRequestDiff> diff =
                    storedDiffs.latestOf(linked.pullRequest().externalId());
            if (diff.isEmpty()) {
                failures.add(new BuilderFailure(
                        key,
                        "no diff record is stored for " + linked.pullRequest().externalId()));
                continue;
            }
            List<String> testFiles = diff.get().files().stream()
                    .map(PullRequestDiff.File::path)
                    .filter(testPaths::matches)
                    .toList();
            if (testFiles.isEmpty()) {
                continue;
            }
            if (linked.personId().isEmpty()) {
                failures.add(linked.noPerson(key));
                continue;
            }
            items.add(item(key, linked, linked.personId().get(), digest.get(), diff.get(), testFiles, workItems));
        }
        return new BuildResult(items, failures);
    }

    private static BuiltItem item(
            String key,
            LinkedPullRequest linked,
            long personId,
            CurrentDigest digest,
            PullRequestDiff diff,
            List<String> testFiles,
            WorkItemLookup workItems) {
        PullRequestFacts pullRequest = linked.pullRequest();
        List<WorkPackageFacts> linkedWorkItems = workItems.linkedFacts(linked.workItems());
        ObjectNode fields = ItemFields.ofPullRequest(pullRequest, linkedWorkItems);
        fields.put("testKind", digest.digest().testKind().wireName());
        fields.set("testFiles", ItemFields.texts(testFiles));
        return new BuiltItem(
                key,
                EvidenceType.TEST_CHANGE,
                personId,
                null,
                linked.mergeDay(),
                pullRequest.url(),
                fields,
                ItemSources.ofPullRequest(pullRequest, diff, linkedWorkItems),
                List.of(digest));
    }

    private static String key(PullRequestFacts pullRequest) {
        return "test_change:" + pullRequest.externalId();
    }
}
