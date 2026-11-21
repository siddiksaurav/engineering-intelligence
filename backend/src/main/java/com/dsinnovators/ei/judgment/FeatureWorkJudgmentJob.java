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

    private static final JudgingLoop<FeatureWorkToJudge> LOOP =
            new JudgingLoop<>("items_left_for_later", "items", item -> 1L, FeatureWorkToJudge::key);

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
        arguments.requireNone(name());
    }

    /**
     * Fails at once when no provider has a key or another model job runs. An item that fails on its
     * own, with an answer that breaks the schema twice or a work item that is not stored, is counted
     * and the run goes on; the run then fails at the end, naming the first twenty.
     */
    @Override
    public void run(JobArguments arguments, JobCounts counts) {
        modelJobGuard.runGuarded(name(), () -> runGuarded(counts));
    }

    private void runGuarded(JobCounts counts) {
        providerChain.startRunRequiringAProvider();
        Quarter quarter = Quarter.of(LocalDate.now(clock));
        List<FeatureWorkToJudge> items = featureWorkItems.current();
        counts.add("items_read", items.size());
        List<String> failed = new ArrayList<>();
        try {
            failed.addAll(LOOP.run(items, item -> judge.judge(item, quarter, counts), counts));
        } finally {
            providerChain.reportUseTo(counts);
        }
        if (!failed.isEmpty()) {
            counts.add("items_failed", failed.size());
            throw new JudgmentJobFailedException(
                    failed.size() + " of " + items.size() + " Feature work items failed: " + FailedItems.named(failed));
        }
    }
}
