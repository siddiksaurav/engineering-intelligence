package com.dsinnovators.ei.judgment;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.job.FailedItems;
import com.dsinnovators.ei.job.Job;
import com.dsinnovators.ei.job.JobArguments;
import com.dsinnovators.ei.job.JobCounts;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stage 2: the complexity of every current Feature work item (F07 design, "Main flows" 1), one call at
 * a time, oldest item row first. An item whose input was judged before is skipped without a call; an
 * item with a pull request that has no digest waits. Run it after {@code evidence-build}. When every
 * provider has reached its limit the run stops and succeeds; the next run goes on with the items not
 * yet judged (decision 152).
 */
@Component
class FeatureWorkJudgmentJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(FeatureWorkJudgmentJob.class);

    private final FeatureWorkItems featureWorkItems;
    private final FeatureWorkJudge judge;
    private final ProviderChain providerChain;
    private final ModelJobGuard modelJobGuard;
    private final Clock clock;

    FeatureWorkJudgmentJob(
            FeatureWorkItems featureWorkItems,
            FeatureWorkJudge judge,
            ProviderChain providerChain,
            ModelJobGuard modelJobGuard,
            Clock clock) {
        this.featureWorkItems = featureWorkItems;
        this.judge = judge;
        this.providerChain = providerChain;
        this.modelJobGuard = modelJobGuard;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "feature-work-judgment";
    }

    @Override
    public void checkArguments(JobArguments arguments) {
        if (!arguments.names().isEmpty()) {
            throw new IllegalArgumentException(
                    "The job " + name() + " takes no arguments, was given " + arguments.names());
        }
    }

    /**
     * Fails at once when no provider has a key or another model job runs. An item that fails on its
     * own, with an answer that breaks the schema twice or a work item that is not stored, is counted
     * and the run goes on; the run then fails at the end, naming the first twenty.
     */
    @Override
    public void run(JobArguments arguments, JobCounts counts) {
        ModelJobGuard.Run hold = modelJobGuard.enter(name());
        try {
            runGuarded(counts);
        } finally {
            hold.close();
        }
    }

    private void runGuarded(JobCounts counts) {
        providerChain.startRunRequiringAProvider();
        Quarter quarter = Quarter.of(LocalDate.now(clock));
        List<FeatureWorkToJudge> items = featureWorkItems.current();
        counts.add("items_read", items.size());
        List<String> failed = new ArrayList<>();
        try {
            judgeAll(items, quarter, counts, failed);
        } finally {
            providerChain.reportUseTo(counts);
        }
        if (!failed.isEmpty()) {
            counts.add("items_failed", failed.size());
            throw new JudgmentJobFailedException(
                    failed.size() + " of " + items.size() + " Feature work items failed: " + FailedItems.named(failed));
        }
    }

    private void judgeAll(List<FeatureWorkToJudge> items, Quarter quarter, JobCounts counts, List<String> failed) {
        for (int index = 0; index < items.size(); index++) {
            FeatureWorkToJudge item = items.get(index);
            try {
                judge.judge(item, quarter, counts);
            } catch (ModelLimitReachedException limit) {
                LOG.warn("{}; the next run goes on with the items not yet judged", limit.getMessage());
                counts.add("items_left_for_later", items.size() - index);
                return;
            } catch (RuntimeException failure) {
                LOG.error("{} could not be judged", item.key(), failure);
                failed.add(item.key());
            }
        }
    }
}
