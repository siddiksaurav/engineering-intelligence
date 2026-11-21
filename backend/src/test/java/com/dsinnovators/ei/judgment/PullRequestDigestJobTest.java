package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.identity.BotAccounts;
import com.dsinnovators.ei.job.JobArguments;
import com.dsinnovators.ei.job.JobCounts;
import com.dsinnovators.ei.source.RawRecordStore;
import com.dsinnovators.ei.source.github.StoredPullRequestDiffs;
import com.dsinnovators.ei.source.github.StoredPullRequests;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PullRequestDigestJobTest extends DatabaseTest {

    private static final String REPOSITORY = "opf/openproject";
    private static final String PROMPT_VERSION = "pull-request-digest/v1";
    private static final String CODE_FILE =
            "[{\"filename\":\"app/list.rb\",\"status\":\"modified\",\"patch\":\"@@ fix @@\"}]";

    @Autowired
    private RawRecordStore rawRecords;

    @Autowired
    private StoredPullRequests storedPullRequests;

    @Autowired
    private StoredPullRequestDiffs storedDiffs;

    @Autowired
    private BotAccounts botAccounts;

    @Autowired
    private DiffFileFilter diffFileFilter;

    @Autowired
    private PromptLibrary promptLibrary;

    @Autowired
    private JudgmentStore judgmentStore;

    @Autowired
    private JudgmentProperties properties;

    @Autowired
    private Clock clock;

    private final Map<String, Long> counts = new HashMap<>();
    private final JobCounts jobCounts = (name, amount) -> counts.merge(name, amount, Long::sum);
    private final FakeJudgmentModel model = new FakeJudgmentModel();
    private final WaitMeter waitMeter = new WaitMeter(duration -> {});
    private final ModelJobGuard guard = new ModelJobGuard();
    private PullRequestDigestJob job;

    @BeforeEach
    void assembleTheJobOnAFakeModel() {
        job = jobOn(new ProviderChain(List.of(new ProviderChain.Provider("fake", model)), waitMeter));
    }

    private PullRequestDigestJob jobOn(ProviderChain chain) {
        PullRequestDigester digester =
                new PullRequestDigester(storedDiffs, diffFileFilter, promptLibrary, chain, judgmentStore, properties);
        return new PullRequestDigestJob(storedPullRequests, botAccounts, digester, chain, guard, clock);
    }

    private void storePullRequest(int number, String mergedAt, String title) {
        storePullRequest(number, mergedAt, title, "{\"login\":\"ada\",\"__typename\":\"User\",\"databaseId\":1}");
    }

    private void storePullRequest(int number, String mergedAt, String title, String author) {
        String url = "https://github.com/" + REPOSITORY + "/pull/" + number;
        rawRecords.store(
                "github",
                "pull_request",
                REPOSITORY + "#" + number,
                url,
                CanonicalJson.parse("{\"number\":" + number + ",\"title\":\"" + title + "\",\"body\":\"Body " + number
                        + "\",\"url\":\"" + url + "\",\"mergedAt\":\"" + mergedAt + "\",\"author\":" + author + "}"));
    }

    private void storeDiff(int number, String filesJson) {
        rawRecords.store(
                "github",
                "pull_request_diff",
                REPOSITORY + "#" + number,
                "https://github.com/" + REPOSITORY + "/pull/" + number + "/files",
                CanonicalJson.parse("{\"files\":" + filesJson + ",\"truncated\":false}"));
    }

    private void storePullRequestWithDiff(int number, String mergedAt) {
        storePullRequest(number, mergedAt, "PR " + number);
        storeDiff(number, CODE_FILE);
    }

    private void run() throws Exception {
        counts.clear();
        job.run(JobArguments.of(Map.of()), jobCounts);
    }

    private long judgmentRows() {
        return jdbc.queryForObject("select count(*) from judgment", Long.class);
    }

    @Test
    void aSecondRunWithTheSameInputMakesNoCall() throws Exception {
        storePullRequestWithDiff(1, "2026-07-02T08:00:00Z");

        run();
        assertEquals(1, model.calls());
        assertEquals(1, judgmentRows());

        run();

        assertEquals(1, model.calls());
        assertEquals(1, judgmentRows());
        assertEquals(1L, counts.get("skipped_same_input"));
        assertNull(counts.get("model_calls"));
        assertNull(counts.get("digests_written"));
    }

    @Test
    void storesTheDigestOfEachHumanPullRequestOldestMergeFirst() throws Exception {
        storePullRequestWithDiff(2, "2026-07-03T08:00:00Z");
        storePullRequestWithDiff(1, "2026-07-02T08:00:00Z");

        run();

        List<String> titlesAsked = model.requests().stream()
                .map(request -> request.input().lines().findFirst().orElseThrow())
                .toList();
        assertEquals(List.of("Title: PR 1", "Title: PR 2"), titlesAsked);
        assertEquals(2L, counts.get("digests_written"));
        Judgment digest = judgmentStore
                .findCurrentOfPullRequest(JudgmentKind.PULL_REQUEST_DIGEST, REPOSITORY + "#1")
                .orElseThrow();
        assertEquals("fix", digest.getValue().path("workType").asString());
        assertFalse(digest.getValue().path("judgedOnPartialCode").asBoolean());
        assertNull(digest.getTier());
        assertEquals("The title names a crash that is now handled.", digest.getReason());
        assertEquals(FakeJudgmentModel.MODEL, digest.getModel());
        assertEquals(PROMPT_VERSION, digest.getPromptVersion());
        assertEquals(JudgedBy.MODEL, digest.getJudgedBy());
        assertEquals(64, digest.getInputHash().length());
        assertEquals(1, digest.getFacts().size());
        assertEquals(
                "https://github.com/opf/openproject/pull/1",
                digest.getFacts().getFirst().link());
    }

    @Test
    void failsAtOnceWhenAnotherModelJobRunsAndReleasesTheGuardWhenItEnds() throws Exception {
        storePullRequestWithDiff(1, "2026-07-02T08:00:00Z");
        ModelJobGuard.Run judging = guard.enter("feature-work-judgment");
        try {
            IllegalStateException refused = assertThrows(IllegalStateException.class, this::run);

            assertTrue(refused.getMessage().contains("feature-work-judgment"), refused.getMessage());
            assertEquals(0, model.calls());
        } finally {
            judging.close();
        }

        run();

        guard.enter("feature-work-judgment").close();
    }

    @Test
    void sendsThePromptTheSchemaAndTheDigestInput() throws Exception {
        storePullRequestWithDiff(1, "2026-07-02T08:00:00Z");

        run();

        ModelRequest request = model.requests().getFirst();
        assertEquals(PROMPT_VERSION, request.promptVersion());
        assertEquals(promptLibrary.instructions(PROMPT_VERSION), request.instructions());
        assertEquals(promptLibrary.schema(PROMPT_VERSION), request.jsonSchema());
        assertTrue(request.input().contains("- app/list.rb (modified)"));
        assertTrue(request.input().contains("@@ fix @@"));
    }

    @Test
    void skipsBotPullRequestsAndPullRequestsWithoutAnAuthorAndCountsThem() throws Exception {
        storePullRequestWithDiff(1, "2026-07-02T08:00:00Z");
        storePullRequest(
                2,
                "2026-07-03T08:00:00Z",
                "Bump",
                "{\"login\":\"dependabot[bot]\",\"__typename\":\"Bot\",\"databaseId\":9}");
        storePullRequest(3, "2026-07-04T08:00:00Z", "Ghost", "null");

        run();

        assertEquals(1, model.calls());
        assertEquals(3L, counts.get("pull_requests_read"));
        assertEquals(1L, counts.get("bot_pull_requests_skipped"));
        assertEquals(1L, counts.get("records_without_author"));
    }

    @Test
    void aNewVersionOfThePullRequestGetsADigestThatSupersedesTheOldOne() throws Exception {
        storePullRequestWithDiff(1, "2026-07-02T08:00:00Z");
        run();
        Judgment first = judgmentStore
                .findCurrentOfPullRequest(JudgmentKind.PULL_REQUEST_DIGEST, REPOSITORY + "#1")
                .orElseThrow();
        storePullRequest(1, "2026-07-02T08:00:00Z", "PR 1, retitled");

        run();

        Judgment second = judgmentStore
                .findCurrentOfPullRequest(JudgmentKind.PULL_REQUEST_DIGEST, REPOSITORY + "#1")
                .orElseThrow();
        assertEquals(first.getId(), second.getSupersedesId());
        assertEquals(2, model.calls());
        assertEquals(2, judgmentRows());
    }

    @Test
    void asksAgainOnceWhenTheAnswerBreaksTheSchema() throws Exception {
        storePullRequestWithDiff(1, "2026-07-02T08:00:00Z");
        model.thenAnswers(FakeJudgmentModel.aDigest().put("workType", "bugfix"));

        run();

        assertEquals(2, model.calls());
        assertEquals(1L, counts.get("digests_written"));
        assertEquals(2L, counts.get("model_calls"));
        assertNull(counts.get("pull_requests_failed"));
    }

    @Test
    void theInputHashDoesNotChangeWithTheRunDay() throws Exception {
        storePullRequestWithDiff(1, "2026-07-02T08:00:00Z");
        PullRequestDigestJob firstDay = jobOnDay("2026-07-10");
        PullRequestDigestJob otherDay = jobOnDay("2026-07-20");

        firstDay.run(JobArguments.of(Map.of()), jobCounts);
        counts.clear();
        otherDay.run(JobArguments.of(Map.of()), jobCounts);

        assertEquals(1, model.calls());
        assertEquals(1L, counts.get("skipped_same_input"));
        assertEquals(1, judgmentRows());
    }

    private PullRequestDigestJob jobOnDay(String day) {
        Clock dayClock = Clock.fixed(Instant.parse(day + "T09:00:00Z"), ZoneOffset.UTC);
        PullRequestDigester digester = new PullRequestDigester(
                storedDiffs,
                diffFileFilter,
                promptLibrary,
                new ProviderChain(List.of(new ProviderChain.Provider("fake", model)), waitMeter),
                judgmentStore,
                properties);
        return new PullRequestDigestJob(
                storedPullRequests,
                botAccounts,
                digester,
                new ProviderChain(List.of(new ProviderChain.Provider("fake", model)), waitMeter),
                guard,
                dayClock);
    }

    @Test
    void asksAgainOnceWhenTheAnswerCannotBeRead() throws Exception {
        storePullRequestWithDiff(1, "2026-07-02T08:00:00Z");
        model.thenFails(new UnreadableAnswerException("Provider fake sent the answer that is not JSON"));

        run();

        assertEquals(2, model.calls());
        assertEquals(1L, counts.get("digests_written"));
        assertNull(counts.get("pull_requests_failed"));
    }

    @Test
    void failsThePullRequestWhenTheAnswerCannotBeReadTwice() {
        storePullRequestWithDiff(1, "2026-07-02T08:00:00Z");
        model.thenFails(new UnreadableAnswerException("Provider fake cut off the answer at the token limit"))
                .thenFails(new UnreadableAnswerException("Provider fake cut off the answer at the token limit"));

        assertThrows(PullRequestDigestFailedException.class, this::run);

        assertEquals(2, model.calls());
        assertEquals(1L, counts.get("pull_requests_failed"));
    }

    @Test
    void failsThePullRequestAfterTwoBadAnswersAndGoesOn() {
        storePullRequestWithDiff(1, "2026-07-02T08:00:00Z");
        storePullRequestWithDiff(2, "2026-07-03T08:00:00Z");
        model.thenAnswers(FakeJudgmentModel.aDigest().put("workType", "bugfix"))
                .thenAnswers(FakeJudgmentModel.aDigest().put("workType", "bugfix"));

        PullRequestDigestFailedException failed = assertThrows(PullRequestDigestFailedException.class, this::run);

        assertTrue(failed.getMessage().contains("1 of 2"), failed.getMessage());
        assertTrue(failed.getMessage().contains("opf/openproject#1"), failed.getMessage());
        assertFalse(failed.getMessage().contains("opf/openproject#2"), failed.getMessage());
        assertEquals(3, model.calls());
        assertEquals(1, judgmentRows());
        assertEquals(1L, counts.get("pull_requests_failed"));
        assertEquals(1L, counts.get("digests_written"));
    }

    @Test
    void failsAPullRequestWhoseCallFailedAndGoesOn() {
        storePullRequestWithDiff(1, "2026-07-02T08:00:00Z");
        storePullRequestWithDiff(2, "2026-07-03T08:00:00Z");
        model.thenFails(new ModelCallException("Provider fake answered 413: request too large"));

        assertThrows(PullRequestDigestFailedException.class, this::run);

        assertEquals(1, judgmentRows());
        assertEquals(1L, counts.get("pull_requests_failed"));
    }

    @Test
    void failsAPullRequestWithoutADiffRecordAndNamesIt() {
        storePullRequest(1, "2026-07-02T08:00:00Z", "No diff");

        PullRequestDigestFailedException failed = assertThrows(PullRequestDigestFailedException.class, this::run);

        assertTrue(failed.getMessage().contains("opf/openproject#1"));
        assertEquals(0, model.calls());
    }

    @Test
    void stopsWhenEveryProviderIsAtItsLimitCountsWhatIsLeftAndSucceeds() throws Exception {
        storePullRequestWithDiff(1, "2026-07-02T08:00:00Z");
        storePullRequestWithDiff(2, "2026-07-03T08:00:00Z");
        storePullRequestWithDiff(3, "2026-07-04T08:00:00Z");
        model.thenAnswers(FakeJudgmentModel.aDigest())
                .thenFails(new ProviderLimitReachedException("fake", "the daily tokens are used"));

        run();

        assertEquals(2, model.calls());
        assertEquals(1, judgmentRows());
        assertEquals(1L, counts.get("digests_written"));
        assertEquals(2L, counts.get("pull_requests_left_for_later"));
        assertNull(counts.get("pull_requests_failed"));
    }

    @Test
    void theNextRunGoesOnWithThePullRequestsNotYetJudged() throws Exception {
        storePullRequestWithDiff(1, "2026-07-02T08:00:00Z");
        storePullRequestWithDiff(2, "2026-07-03T08:00:00Z");
        model.thenAnswers(FakeJudgmentModel.aDigest())
                .thenFails(new ProviderLimitReachedException("fake", "the daily tokens are used"));
        run();

        run();

        assertEquals(3, model.calls());
        assertEquals(2, judgmentRows());
        assertEquals(1L, counts.get("skipped_same_input"));
        assertEquals(1L, counts.get("digests_written"));
    }

    @Test
    void countsCallsTokensWaitsAndPartialDiffs() throws Exception {
        storePullRequestWithDiff(1, "2026-07-02T08:00:00Z");
        storePullRequest(2, "2026-07-03T08:00:00Z", "Large");
        storeDiff(
                2,
                "[{\"filename\":\"app/huge.rb\",\"status\":\"modified\",\"patch\":\""
                        + "x".repeat(properties.maxDiffCharacters() + 1) + "\"}]");
        model.thenDoes(() -> sleep(Duration.ofSeconds(3)));

        run();

        assertEquals(2L, counts.get("model_calls"));
        assertEquals(2 * FakeJudgmentModel.INPUT_TOKENS, counts.get("input_tokens"));
        assertEquals(2 * FakeJudgmentModel.OUTPUT_TOKENS, counts.get("output_tokens"));
        assertEquals(3L, counts.get("rate_limit_wait_seconds"));
        assertEquals(1L, counts.get("judged_on_partial_code"));
        assertEquals(2L, counts.get("calls_fake"));
        assertEquals(2L, counts.get("digests_written"));
        Judgment large = judgmentStore
                .findCurrentOfPullRequest(JudgmentKind.PULL_REQUEST_DIGEST, REPOSITORY + "#2")
                .orElseThrow();
        assertTrue(large.getValue().path("judgedOnPartialCode").asBoolean());
    }

    private void sleep(Duration duration) {
        try {
            waitMeter.sleep(duration);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    @Test
    void digestsOnlyTheListedPullRequests() throws Exception {
        storePullRequestWithDiff(1, "2026-07-02T08:00:00Z");
        storePullRequestWithDiff(2, "2026-07-03T08:00:00Z");
        storePullRequestWithDiff(3, "2026-07-04T08:00:00Z");

        job.run(JobArguments.of(Map.of("pullRequestNumbers", "3, 2")), jobCounts);

        assertEquals(
                List.of("Title: PR 2", "Title: PR 3"),
                model.requests().stream()
                        .map(request -> request.input().lines().findFirst().orElseThrow())
                        .toList());
    }

    @Test
    void failsAListedNumberThatWasNotStored() {
        storePullRequestWithDiff(1, "2026-07-02T08:00:00Z");

        PullRequestDigestFailedException failed = assertThrows(
                PullRequestDigestFailedException.class,
                () -> job.run(JobArguments.of(Map.of("pullRequestNumbers", "1,9")), jobCounts));

        assertTrue(failed.getMessage().contains("#9"), failed.getMessage());
        assertEquals(1, judgmentRows());
    }

    @Test
    void refusesAnArgumentItDoesNotKnowAndANumberListThatIsNotOne() {
        assertThrows(
                IllegalArgumentException.class, () -> job.checkArguments(JobArguments.of(Map.of("repository", "a/b"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> job.checkArguments(JobArguments.of(Map.of("pullRequestNumbers", "one"))));
        job.checkArguments(JobArguments.of(Map.of()));
        job.checkArguments(JobArguments.of(Map.of("pullRequestNumbers", "2,3")));
    }

    @Test
    void failsAtItsStartWhenNoProviderHasAKey() {
        PullRequestDigestJob withoutProvider = jobOn(new ProviderChain(List.of(), waitMeter));
        storePullRequestWithDiff(1, "2026-07-02T08:00:00Z");

        IllegalStateException refused = assertThrows(
                IllegalStateException.class, () -> withoutProvider.run(JobArguments.of(Map.of()), jobCounts));

        assertTrue(refused.getMessage().contains("LLM_PROVIDER_1_API_KEY"));
        assertEquals(0, judgmentRows());
    }
}
