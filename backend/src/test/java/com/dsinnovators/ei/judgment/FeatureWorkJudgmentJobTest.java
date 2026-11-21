package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.evidence.EvidenceItemStore;
import com.dsinnovators.ei.job.JobArguments;
import com.dsinnovators.ei.job.JobCounts;
import com.dsinnovators.ei.setting.SettingService;
import com.dsinnovators.ei.source.RawRecordStore;
import com.dsinnovators.ei.source.github.StoredPullRequests;
import com.dsinnovators.ei.source.openproject.StoredWorkPackages;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FeatureWorkJudgmentJobTest extends DatabaseTest {

    @Autowired
    private StoredWorkPackages storedWorkPackages;

    @Autowired
    private StoredPullRequests storedPullRequests;

    @Autowired
    private PullRequestDigests pullRequestDigests;

    @Autowired
    private PromptLibrary promptLibrary;

    @Autowired
    private JudgmentStore judgmentStore;

    @Autowired
    private SettingService settings;

    @Autowired
    private JudgmentProperties properties;

    @Autowired
    private RawRecordStore rawRecords;

    @Autowired
    private EvidenceItemStore evidenceItems;

    @Autowired
    private FeatureWorkItems featureWorkItems;

    @Autowired
    private Clock clock;

    private final Map<String, Long> counts = new HashMap<>();
    private final JobCounts jobCounts = (name, amount) -> counts.merge(name, amount, Long::sum);
    private final FakeJudgmentModel model =
            new FakeJudgmentModel().answeringByDefault(FakeJudgmentModel.aFeatureWork("medium"));
    private final WaitMeter waitMeter = new WaitMeter(duration -> {});
    private final ModelJobGuard guard = new ModelJobGuard();
    private StoredFeatureWorkFixture stored;
    private FeatureWorkJudgmentJob job;

    @BeforeEach
    void storeTwoFeaturesWithADigestedPullRequestEach() {
        stored = new StoredFeatureWorkFixture(jdbc, rawRecords, judgmentStore, evidenceItems, featureWorkItems);
        job = jobOn(new ProviderChain(List.of(new ProviderChain.Provider("fake", model)), waitMeter));
        stored.workPackage(100, "Feature", "Users miss alerts");
        stored.workPackage(200, "Feature", "Export is slow");
        stored.pullRequest(1, "2026-07-02T08:00:00Z");
        stored.pullRequest(2, "2026-07-03T08:00:00Z");
        stored.digest(1);
        stored.digest(2);
        stored.item(100, List.of(), 1);
        stored.item(200, List.of(), 2);
    }

    private FeatureWorkJudgmentJob jobOn(ProviderChain chain) {
        FeatureWorkJudge judge = new FeatureWorkJudge(
                storedWorkPackages,
                storedPullRequests,
                pullRequestDigests,
                promptLibrary,
                chain,
                judgmentStore,
                settings,
                properties);
        return new FeatureWorkJudgmentJob(featureWorkItems, judge, chain, guard, clock);
    }

    private void run() throws Exception {
        counts.clear();
        job.run(JobArguments.of(Map.of()), jobCounts);
    }

    private long complexityJudgments() {
        return jdbc.queryForObject("select count(*) from judgment where kind = 'feature_work_complexity'", Long.class);
    }

    @Test
    void aSecondRunWithTheSameInputMakesNoCall() throws Exception {
        run();
        assertEquals(2, model.calls());

        run();

        assertEquals(2, model.calls());
        assertEquals(2, complexityJudgments());
        assertEquals(2L, counts.get("skipped_same_input"));
        assertNull(counts.get("model_calls"));
        assertNull(counts.get("judgments_written"));
    }

    @Test
    void judgesEveryCurrentFeatureWorkItemOnceAndCountsTheRun() throws Exception {
        run();

        assertEquals(2, complexityJudgments());
        assertEquals(2L, counts.get("items_read"));
        assertEquals(2L, counts.get("judgments_written"));
        assertEquals(2L, counts.get("model_calls"));
        assertEquals(2 * FakeJudgmentModel.INPUT_TOKENS, counts.get("input_tokens"));
        assertEquals(2 * FakeJudgmentModel.OUTPUT_TOKENS, counts.get("output_tokens"));
        assertEquals(2L, counts.get("calls_fake"));
        assertEquals(0L, counts.get("rate_limit_wait_seconds"));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "select count(*) from evidence_item e where evidence_type = 'feature_work' and not exists"
                                + " (select 1 from judgment j where j.evidence_item_id = e.id)",
                        Long.class));
    }

    @Test
    void anItemWhosePullRequestHasNoDigestWaitsAndMakesNoCall() throws Exception {
        stored.workPackage(300, "Feature", "No digest yet");
        stored.pullRequest(3, "2026-07-04T08:00:00Z");
        stored.item(300, List.of(), 3);

        run();

        assertEquals(2, model.calls());
        assertEquals(3L, counts.get("items_read"));
        assertEquals(1L, counts.get("items_waiting_for_digest"));
        assertEquals(2L, counts.get("judgments_written"));
    }

    @Test
    void stopsWhenEveryProviderIsAtItsLimitCountsWhatIsLeftAndSucceeds() throws Exception {
        stored.workPackage(300, "Feature", "Third");
        stored.pullRequest(3, "2026-07-04T08:00:00Z");
        stored.digest(3);
        stored.item(300, List.of(), 3);
        model.thenAnswers(FakeJudgmentModel.aFeatureWork("low"))
                .thenFails(new ProviderLimitReachedException("fake", "the daily tokens are used"));

        run();

        assertEquals(1, complexityJudgments());
        assertEquals(2L, counts.get("items_left_for_later"));
        assertEquals(1L, counts.get("judgments_written"));
        assertNull(counts.get("items_failed"));
    }

    @Test
    void theNextRunGoesOnWithTheItemsNotYetJudged() throws Exception {
        model.thenAnswers(FakeJudgmentModel.aFeatureWork("low"))
                .thenFails(new ProviderLimitReachedException("fake", "the daily tokens are used"));
        run();

        run();

        assertEquals(2, complexityJudgments());
        assertEquals(1L, counts.get("skipped_same_input"));
        assertEquals(1L, counts.get("judgments_written"));
    }

    @Test
    void failsAnItemAfterTwoBadAnswersGoesOnAndFailsTheRunAtTheEndNamingIt() {
        model.thenAnswers(FakeJudgmentModel.aFeatureWork("low").put("confidence", 7))
                .thenAnswers(FakeJudgmentModel.aFeatureWork("low").put("confidence", 7));

        JudgmentJobFailedException failed = assertThrows(JudgmentJobFailedException.class, this::run);

        assertTrue(failed.getMessage().contains("1 of 2"), failed.getMessage());
        assertTrue(failed.getMessage().contains("feature_work:100:"), failed.getMessage());
        assertFalse(failed.getMessage().contains("feature_work:200:"), failed.getMessage());
        assertEquals(1, complexityJudgments());
        assertEquals(1L, counts.get("items_failed"));
        assertEquals(1L, counts.get("judgments_written"));
    }

    @Test
    void failsAtOnceWhenNoProviderHasAKey() {
        FeatureWorkJudgmentJob withoutProvider = jobOn(new ProviderChain(List.of(), waitMeter));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class, () -> withoutProvider.run(JobArguments.of(Map.of()), jobCounts));

        assertTrue(failure.getMessage().contains("LLM_PROVIDER_1_API_KEY"));
        assertEquals(0, model.calls());
    }

    @Test
    void failsAtOnceWhenAnotherModelJobRuns() {
        ModelJobGuard.Run digest = guard.enter("pull-request-digest");
        try {
            IllegalStateException failure = assertThrows(IllegalStateException.class, this::run);

            assertTrue(failure.getMessage().contains("pull-request-digest"), failure.getMessage());
            assertEquals(0, model.calls());
        } finally {
            digest.close();
        }
    }

    @Test
    void releasesTheGuardWhenTheRunEnds() throws Exception {
        run();

        guard.enter("review-comment-judgment").close();
    }

    @Test
    void countsTheWaitOfTheProviders() throws Exception {
        model.thenDoes(() -> {
            try {
                waitMeter.sleep(Duration.ofSeconds(4));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        });

        run();

        assertEquals(4L, counts.get("rate_limit_wait_seconds"));
    }

    @Test
    void takesNoArguments() {
        assertThrows(
                IllegalArgumentException.class, () -> job.checkArguments(JobArguments.of(Map.of("repository", "a/b"))));
        job.checkArguments(JobArguments.of(Map.of()));
        assertEquals("feature-work-judgment", job.name());
    }
}
