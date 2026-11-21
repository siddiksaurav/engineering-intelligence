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
import com.dsinnovators.ei.source.RawRecordStore;
import com.dsinnovators.ei.source.github.StoredPullRequests;
import com.dsinnovators.ei.source.github.StoredReviewComments;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReviewCommentJudgmentJobTest extends DatabaseTest {

    @Autowired
    private ReviewCommentItems reviewCommentItems;

    @Autowired
    private StoredReviewComments storedReviewComments;

    @Autowired
    private StoredPullRequests storedPullRequests;

    @Autowired
    private PromptLibrary promptLibrary;

    @Autowired
    private JudgmentStore judgmentStore;

    @Autowired
    private ReviewCommentTypes commentTypes;

    @Autowired
    private JudgmentProperties properties;

    @Autowired
    private RawRecordStore rawRecords;

    @Autowired
    private EvidenceItemStore evidenceItems;

    @Autowired
    private Clock clock;

    private final Map<String, Long> counts = new HashMap<>();
    private final JobCounts jobCounts = (name, amount) -> counts.merge(name, amount, Long::sum);
    private final FakeJudgmentModel model = new FakeJudgmentModel();
    private final WaitMeter waitMeter = new WaitMeter(duration -> {});
    private final ModelJobGuard guard = new ModelJobGuard();
    private StoredReviewCommentFixture stored;
    private ReviewCommentJudgmentJob job;

    @BeforeEach
    void storeThreeCommentsOnTwoPullRequests() {
        stored = new StoredReviewCommentFixture(jdbc, rawRecords, evidenceItems);
        job = jobOn(new ProviderChain(List.of(new ProviderChain.Provider("fake", model)), waitMeter), properties);
        stored.pullRequest(7, "Add the series");
        stored.pullRequest(8, "Fix the export");
        stored.comment(1, 7, "2026-07-03T09:00:00Z", "Why a second query?");
        stored.comment(2, 7, "2026-07-03T09:05:00Z", "Rename this.");
        stored.comment(3, 8, "2026-07-03T10:00:00Z", "This can be nil.");
    }

    private ReviewCommentJudgmentJob jobOn(ProviderChain chain, JudgmentProperties limits) {
        ReviewCommentJudge judge = new ReviewCommentJudge(promptLibrary, chain, judgmentStore, commentTypes);
        return new ReviewCommentJudgmentJob(
                reviewCommentItems, storedReviewComments, storedPullRequests, judge, chain, guard, clock, limits);
    }

    private void answerTheThreeComments() {
        model.thenAnswers(FakeJudgmentModel.aReviewComments("C-1", "question", "C-2", "style"))
                .thenAnswers(FakeJudgmentModel.aReviewComments("C-3", "correctness"));
    }

    private void run() throws Exception {
        counts.clear();
        job.run(JobArguments.of(Map.of()), jobCounts);
    }

    private long reviewCommentJudgments() {
        return jdbc.queryForObject("select count(*) from judgment where kind = 'review_comment'", Long.class);
    }

    @Test
    void everyCurrentReviewCommentGetsOneJudgment() throws Exception {
        answerTheThreeComments();

        run();

        assertEquals(3, reviewCommentJudgments());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "select count(*) from evidence_item e where evidence_type = 'review_comment_written'"
                                + " and not exists (select 1 from judgment j where j.evidence_item_id = e.id)",
                        Long.class));
        assertEquals(
                List.of("T1", "null", "T2"),
                jdbc.queryForList(
                        "select coalesce(j.tier, 'null') from judgment j join evidence_item e"
                                + " on e.id = j.evidence_item_id order by e.item_key",
                        String.class));
    }

    @Test
    void countsTheRun() throws Exception {
        answerTheThreeComments();

        run();

        assertEquals(3L, counts.get("items_read"));
        assertEquals(2L, counts.get("pull_requests_read"));
        assertEquals(2L, counts.get("chunks"));
        assertEquals(3L, counts.get("judgments_written"));
        assertEquals(2L, counts.get("model_calls"));
        assertEquals(2 * FakeJudgmentModel.INPUT_TOKENS, counts.get("input_tokens"));
        assertEquals(2 * FakeJudgmentModel.OUTPUT_TOKENS, counts.get("output_tokens"));
        assertEquals(2L, counts.get("calls_fake"));
        assertEquals(0L, counts.get("rate_limit_wait_seconds"));
        assertNull(counts.get("items_left_for_later"));
        assertNull(counts.get("chunks_failed"));
    }

    @Test
    void sendsOneCallPerPullRequestWithItsCommentsInCreationOrder() throws Exception {
        answerTheThreeComments();

        run();

        String firstInput = model.requests().get(0).input();
        assertTrue(firstInput.startsWith("Pull request: Add the series\n"), firstInput);
        assertTrue(firstInput.indexOf("[C-1]") < firstInput.indexOf("[C-2]"), firstInput);
        assertFalse(firstInput.contains("[C-3]"), firstInput);
        assertTrue(model.requests().get(1).input().contains("[C-3]"));
    }

    @Test
    void aReplyIsSentWithTheTextOfTheCommentItAnswersEvenWhenTheAuthorWroteIt() throws Exception {
        stored.commentRecord(4, 8, "2026-07-03T10:30:00Z", "I will check the nil case.", null);
        stored.reply(5, 8, "2026-07-03T10:40:00Z", "Thanks.", 4);
        model.thenAnswers(FakeJudgmentModel.aReviewComments("C-1", "question", "C-2", "style"))
                .thenAnswers(FakeJudgmentModel.aReviewComments("C-3", "correctness", "C-5", "acknowledgement"));

        run();

        String input = model.requests().get(1).input();
        assertTrue(input.contains("This comment replies to:\nI will check the nil case."), input);
        assertFalse(input.contains("[C-4]"), input);
    }

    @Test
    void aSecondRunWithTheSameInputMakesNoCall() throws Exception {
        answerTheThreeComments();
        run();

        run();

        assertEquals(2, model.calls());
        assertEquals(3, reviewCommentJudgments());
        assertEquals(3L, counts.get("skipped_same_input"));
        assertEquals(2L, counts.get("chunks"));
        assertNull(counts.get("model_calls"));
        assertNull(counts.get("judgments_written"));
    }

    @Test
    void splitsAPullRequestWithMoreCommentsThanOneCallHolds() throws Exception {
        JudgmentProperties tight = new JudgmentProperties(
                List.of(),
                Duration.ofSeconds(120),
                3,
                Duration.ofSeconds(2),
                Duration.ofSeconds(60),
                6_000,
                12_000,
                4_000,
                300,
                100,
                14_000,
                100,
                100,
                50,
                600);
        ReviewCommentJudgmentJob tightJob =
                jobOn(new ProviderChain(List.of(new ProviderChain.Provider("fake", model)), waitMeter), tight);
        stored.comment(6, 7, "2026-07-03T09:10:00Z", "x".repeat(100));
        stored.comment(7, 7, "2026-07-03T09:15:00Z", "y".repeat(100));
        model.thenAnswers(FakeJudgmentModel.aReviewComments("C-1", "question", "C-2", "style", "C-6", "style"))
                .thenAnswers(FakeJudgmentModel.aReviewComments("C-7", "style"))
                .thenAnswers(FakeJudgmentModel.aReviewComments("C-3", "correctness"));
        counts.clear();

        tightJob.run(JobArguments.of(Map.of()), jobCounts);

        assertEquals(3L, counts.get("chunks"));
        assertEquals(5L, counts.get("judgments_written"));
        assertEquals(3, model.requests().size());
        model.requests().forEach(request -> assertTrue(request.input().length() <= 600));
    }

    @Test
    void stopsWhenEveryProviderIsAtItsLimitCountsTheCommentsLeftAndSucceeds() throws Exception {
        model.thenAnswers(FakeJudgmentModel.aReviewComments("C-1", "question", "C-2", "style"))
                .thenFails(new ProviderLimitReachedException("fake", "the daily tokens are used"));

        run();

        assertEquals(2, reviewCommentJudgments());
        assertEquals(1L, counts.get("items_left_for_later"));
        assertEquals(2L, counts.get("judgments_written"));
        assertNull(counts.get("chunks_failed"));
    }

    @Test
    void theNextRunGoesOnWithTheCommentsNotYetJudged() throws Exception {
        model.thenAnswers(FakeJudgmentModel.aReviewComments("C-1", "question", "C-2", "style"))
                .thenFails(new ProviderLimitReachedException("fake", "the daily tokens are used"));
        run();
        model.thenAnswers(FakeJudgmentModel.aReviewComments("C-3", "correctness"));

        run();

        assertEquals(3, reviewCommentJudgments());
        assertEquals(2L, counts.get("skipped_same_input"));
        assertEquals(1L, counts.get("judgments_written"));
    }

    @Test
    void failsAChunkAfterTwoBadAnswersGoesOnAndFailsTheRunAtTheEndNamingIt() {
        model.thenAnswers(FakeJudgmentModel.aReviewComments("C-1", "question"))
                .thenAnswers(FakeJudgmentModel.aReviewComments("C-1", "question"))
                .thenAnswers(FakeJudgmentModel.aReviewComments("C-3", "correctness"));

        JudgmentJobFailedException failed = assertThrows(JudgmentJobFailedException.class, this::run);

        assertTrue(failed.getMessage().contains("1 review comment chunks failed"), failed.getMessage());
        assertTrue(failed.getMessage().contains("opf/openproject#7 (review_comment_written:1)"), failed.getMessage());
        assertFalse(failed.getMessage().contains("opf/openproject#8"), failed.getMessage());
        assertEquals(1, reviewCommentJudgments());
        assertEquals(1L, counts.get("chunks_failed"));
        assertEquals(1L, counts.get("judgments_written"));
    }

    @Test
    void failsTheRunNamingAPullRequestThatIsNotStoredAndJudgesTheRest() {
        stored.commentOfAnUnstoredPullRequest(10, 9, 8, "2026-07-03T11:00:00Z", "No pull request stored.");
        model.thenAnswers(FakeJudgmentModel.aReviewComments("C-1", "question", "C-2", "style"))
                .thenAnswers(FakeJudgmentModel.aReviewComments("C-3", "correctness"));

        JudgmentJobFailedException failed = assertThrows(JudgmentJobFailedException.class, this::run);

        assertTrue(failed.getMessage().contains("opf/openproject#9"), failed.getMessage());
        assertEquals(3, reviewCommentJudgments());
        assertEquals(1L, counts.get("chunks_failed"));
    }

    @Test
    void failsTheRunNamingAPullRequestWhoseCommentWasEditedAfterTheItemWasBuilt() {
        stored.commentRecord(1, 7, "2026-07-03T09:00:00Z", "Why a second query? Edited.", null);
        model.thenAnswers(FakeJudgmentModel.aReviewComments("C-3", "correctness"));

        JudgmentJobFailedException failed = assertThrows(JudgmentJobFailedException.class, this::run);

        assertTrue(failed.getMessage().contains("opf/openproject#7"), failed.getMessage());
        assertEquals(1, reviewCommentJudgments());
        assertEquals(1L, counts.get("chunks_failed"));
    }

    @Test
    void failsAtOnceWhenNoProviderHasAKey() {
        ReviewCommentJudgmentJob withoutProvider = jobOn(new ProviderChain(List.of(), waitMeter), properties);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class, () -> withoutProvider.run(JobArguments.of(Map.of()), jobCounts));

        assertTrue(failure.getMessage().contains("LLM_PROVIDER_1_API_KEY"));
        assertEquals(0, model.calls());
    }

    @Test
    void failsAtOnceWhenAnotherModelJobRuns() {
        ModelJobGuard.Run digest = guard.enter("feature-work-judgment");
        try {
            IllegalStateException failure = assertThrows(IllegalStateException.class, this::run);

            assertTrue(failure.getMessage().contains("feature-work-judgment"), failure.getMessage());
            assertEquals(0, model.calls());
        } finally {
            digest.close();
        }
    }

    @Test
    void releasesTheGuardWhenTheRunEnds() throws Exception {
        answerTheThreeComments();
        run();

        guard.enter("feature-work-judgment").close();
    }

    @Test
    void countsTheWaitOfTheProviders() throws Exception {
        model.answeringByDefault(FakeJudgmentModel.aReviewComments("C-1", "question", "C-2", "style"));
        model.thenDoes(() -> {
            try {
                waitMeter.sleep(Duration.ofSeconds(4));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        });
        model.thenAnswers(FakeJudgmentModel.aReviewComments("C-3", "correctness"));

        run();

        assertEquals(4L, counts.get("rate_limit_wait_seconds"));
    }

    @Test
    void takesNoArguments() {
        assertThrows(
                IllegalArgumentException.class, () -> job.checkArguments(JobArguments.of(Map.of("repository", "a/b"))));
        job.checkArguments(JobArguments.of(Map.of()));
        assertEquals("review-comment-judgment", job.name());
    }
}
