package com.dsinnovators.ei.evidence;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.job.FailedItems;
import com.dsinnovators.ei.job.Job;
import com.dsinnovators.ei.job.JobArguments;
import com.dsinnovators.ei.job.JobCounts;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Builds all evidence items from the stored records by rule and writes what is new or changed
 * (F05 design, "Main flows"), the items that rest on a pull request digest included (F06 design,
 * "Main flows" 4), with the tier judgment of those that have a tier. An item whose records no
 * longer give it is withdrawn (decision 123).
 * Running it again with the same records writes nothing. Run it after {@code identity-build}.
 */
@Component
class EvidenceBuildJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(EvidenceBuildJob.class);

    private final LinkedPullRequests linkedPullRequests;
    private final FeatureWorkBuilder featureWorkBuilder;
    private final BugFixBuilder bugFixBuilder;
    private final ReviewCommentBuilder reviewCommentBuilder;
    private final TestChangeBuilder testChangeBuilder;
    private final WorkTypeItemBuilder workTypeItemBuilder;
    private final EvidenceItemStore store;
    private final ItemTiers itemTiers;
    private final Clock clock;

    EvidenceBuildJob(
            LinkedPullRequests linkedPullRequests,
            FeatureWorkBuilder featureWorkBuilder,
            BugFixBuilder bugFixBuilder,
            ReviewCommentBuilder reviewCommentBuilder,
            TestChangeBuilder testChangeBuilder,
            WorkTypeItemBuilder workTypeItemBuilder,
            EvidenceItemStore store,
            ItemTiers itemTiers,
            Clock clock) {
        this.linkedPullRequests = linkedPullRequests;
        this.featureWorkBuilder = featureWorkBuilder;
        this.bugFixBuilder = bugFixBuilder;
        this.reviewCommentBuilder = reviewCommentBuilder;
        this.testChangeBuilder = testChangeBuilder;
        this.workTypeItemBuilder = workTypeItemBuilder;
        this.store = store;
        this.itemTiers = itemTiers;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "evidence-build";
    }

    @Override
    public void checkArguments(JobArguments arguments) {
        if (!arguments.names().isEmpty()) {
            throw new IllegalArgumentException(
                    "Unknown argument '" + arguments.names().iterator().next() + "'. evidence-build takes none");
        }
    }

    /**
     * An item that fails on its own is counted and the run goes on; the run then fails at the end,
     * naming the items. An item that failed is never withdrawn, whatever the builder: the key a
     * failure carries is kept, so a current item whose rebuild failed stays current.
     */
    @Override
    public void run(JobArguments arguments, JobCounts counts) {
        Quarter quarter = Quarter.of(LocalDate.now(clock));
        LinkedPullRequests.Read read = linkedPullRequests.read(quarter);
        counts.add("pull_requests_read", read.pullRequestsRead());
        counts.add("bot_records_skipped", read.botPullRequestsSkipped());
        counts.add("records_without_author", read.pullRequestsWithoutAuthor());
        counts.add("pull_requests_without_digest", read.pullRequestsWithoutDigest());
        List<BuildResult> results = List.of(
                featureWorkBuilder.build(read.pullRequests(), quarter),
                bugFixBuilder.build(read.pullRequests(), quarter),
                reviewCommentBuilder.build(quarter),
                testChangeBuilder.build(read.pullRequests(), quarter),
                workTypeItemBuilder.build(read.pullRequests(), quarter));
        List<String> failedNames = new ArrayList<>();
        Set<String> keptKeys = new HashSet<>();
        for (BuildResult result : results) {
            counts.add("bot_records_skipped", result.botRecordsSkipped());
            counts.add("records_without_author", result.recordsWithoutAuthor());
            for (BuilderFailure failure : result.failures()) {
                failedNames.add(failure.text());
                keptKeys.add(failure.itemKey());
            }
            for (BuiltItem item : result.items()) {
                keptKeys.add(item.key());
                write(item, quarter, failedNames, counts);
            }
        }
        withdrawGone(keptKeys, failedNames, counts);
        counts.add("items_failed", failedNames.size());
        if (!failedNames.isEmpty()) {
            throw new EvidenceBuildFailedException(
                    failedNames.size() + " items failed: " + FailedItems.named(failedNames));
        }
    }

    private void write(BuiltItem item, Quarter quarter, List<String> failedNames, JobCounts counts) {
        try {
            String outcome = store.write(item).name().toLowerCase(Locale.ROOT);
            counts.add("items_" + outcome, 1);
            counts.add(item.type().databaseName() + "_" + outcome, 1);
        } catch (RuntimeException failure) {
            LOG.error("Evidence item {} could not be written", item.key(), failure);
            failedNames.add(item.key());
            return;
        }
        if (ItemTiers.givesTier(item.type())) {
            writeTier(item, quarter, failedNames, counts);
        }
    }

    // Tried for an unchanged item too, so a tier that a failed run did not write is written by the next.
    private void writeTier(BuiltItem item, Quarter quarter, List<String> failedNames, JobCounts counts) {
        try {
            if (itemTiers.write(item, quarter)) {
                counts.add("item_tiers_written", 1);
            }
        } catch (RuntimeException failure) {
            LOG.error("The tier of evidence item {} could not be written", item.key(), failure);
            failedNames.add(item.key() + " (tier)");
        }
    }

    // keptKeys holds every key a builder made or failed to make, a key whose write failed included.
    private void withdrawGone(Set<String> keptKeys, List<String> failedNames, JobCounts counts) {
        Set<String> goneKeys = new TreeSet<>(store.currentKeys());
        goneKeys.removeAll(keptKeys);
        for (String key : goneKeys) {
            try {
                EvidenceType type = store.withdraw(key);
                counts.add("items_withdrawn", 1);
                counts.add(type.databaseName() + "_withdrawn", 1);
            } catch (RuntimeException failure) {
                LOG.error("Evidence item {} could not be withdrawn", key, failure);
                failedNames.add(key);
            }
        }
    }
}
