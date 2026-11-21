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
 * (F05 design, "Main flows"). An item whose records no longer give it is withdrawn (decision 123).
 * Running it again with the same records writes nothing. Run it after {@code identity-build}.
 */
@Component
class EvidenceBuildJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(EvidenceBuildJob.class);

    private final LinkedPullRequests linkedPullRequests;
    private final FeatureWorkBuilder featureWorkBuilder;
    private final BugFixBuilder bugFixBuilder;
    private final ReviewCommentBuilder reviewCommentBuilder;
    private final EvidenceItemStore store;
    private final Clock clock;

    EvidenceBuildJob(
            LinkedPullRequests linkedPullRequests,
            FeatureWorkBuilder featureWorkBuilder,
            BugFixBuilder bugFixBuilder,
            ReviewCommentBuilder reviewCommentBuilder,
            EvidenceItemStore store,
            Clock clock) {
        this.linkedPullRequests = linkedPullRequests;
        this.featureWorkBuilder = featureWorkBuilder;
        this.bugFixBuilder = bugFixBuilder;
        this.reviewCommentBuilder = reviewCommentBuilder;
        this.store = store;
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
     * naming the items. An item that failed is never withdrawn: it is not gone, it failed.
     */
    @Override
    public void run(JobArguments arguments, JobCounts counts) {
        Quarter quarter = Quarter.of(LocalDate.now(clock));
        LinkedPullRequests.Read read = linkedPullRequests.read(quarter);
        counts.add("pull_requests_read", read.pullRequestsRead());
        counts.add("bot_records_skipped", read.botPullRequestsSkipped());
        List<BuildResult> results = List.of(
                featureWorkBuilder.build(read.pullRequests(), quarter),
                bugFixBuilder.build(read.pullRequests(), quarter),
                reviewCommentBuilder.build(quarter));
        List<String> failedNames = new ArrayList<>();
        Set<String> keptKeys = new HashSet<>();
        for (BuildResult result : results) {
            counts.add("bot_records_skipped", result.botRecordsSkipped());
            failedNames.addAll(result.failures());
            for (BuiltItem item : result.items()) {
                keptKeys.add(item.key());
                write(item, failedNames, counts);
            }
        }
        withdrawGone(keptKeys, failedNames, counts);
        counts.add("items_failed", failedNames.size());
        if (!failedNames.isEmpty()) {
            throw new EvidenceBuildFailedException(
                    failedNames.size() + " items failed: " + FailedItems.named(failedNames));
        }
    }

    private void write(BuiltItem item, List<String> failedNames, JobCounts counts) {
        try {
            String outcome = store.write(item).name().toLowerCase(Locale.ROOT);
            counts.add("items_" + outcome, 1);
            counts.add(item.type().databaseName() + "_" + outcome, 1);
        } catch (RuntimeException failure) {
            LOG.error("Evidence item {} could not be written", item.key(), failure);
            failedNames.add(item.key());
        }
    }

    // keptKeys holds every key a builder made, a key whose write failed included. A builder failure
    // names a key with the login, which no stored item can have, so nothing failed is withdrawn.
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
