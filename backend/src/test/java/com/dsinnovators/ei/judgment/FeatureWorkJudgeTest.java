package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.evidence.EvidenceItemStore;
import com.dsinnovators.ei.job.JobCounts;
import com.dsinnovators.ei.setting.SettingService;
import com.dsinnovators.ei.source.RawRecordStore;
import com.dsinnovators.ei.source.github.StoredPullRequests;
import com.dsinnovators.ei.source.openproject.StoredWorkPackages;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.node.ObjectNode;

class FeatureWorkJudgeTest extends DatabaseTest {

    private static final Quarter QUARTER = Quarter.parse("2026-Q3");

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

    private final Map<String, Long> counts = new HashMap<>();
    private final JobCounts jobCounts = (name, amount) -> counts.merge(name, amount, Long::sum);
    private final FakeJudgmentModel model = new FakeJudgmentModel();
    private StoredFeatureWorkFixture stored;
    private FeatureWorkJudge judge;

    @BeforeEach
    void storeAFeatureWithOnePullRequest() {
        stored = new StoredFeatureWorkFixture(jdbc, rawRecords, judgmentStore, evidenceItems, featureWorkItems);
        judge = judgeWith(properties);
        stored.workPackage(100, "Feature", "Users miss alerts");
        stored.pullRequest(1, "2026-07-02T08:00:00Z");
        stored.digest(1);
    }

    private FeatureWorkJudge judgeWith(JudgmentProperties limits) {
        return new FeatureWorkJudge(
                storedWorkPackages,
                storedPullRequests,
                pullRequestDigests,
                promptLibrary,
                model,
                judgmentStore,
                settings,
                limits);
    }

    private Judgment currentJudgmentOf(FeatureWorkToJudge item) {
        return judgmentStore
                .findCurrent(JudgmentKind.FEATURE_WORK_COMPLEXITY, JudgmentSubject.evidenceItem(item.itemRowId()))
                .orElseThrow();
    }

    @ParameterizedTest
    @CsvSource({"low,T1", "medium,T2", "high,T3", "very_high,T3"})
    void writesTheTierOfTheComplexityFromTheSetting(String complexity, String tier) {
        FeatureWorkToJudge item = stored.item(100, List.of(), 1);
        model.answeringByDefault(FakeJudgmentModel.aFeatureWork(complexity));

        judge.judge(item, QUARTER, jobCounts);

        Judgment judgment = currentJudgmentOf(item);
        assertEquals(tier, judgment.getTier());
        assertEquals(complexity, judgment.getValue().path("complexity").asString());
    }

    @Test
    void storesTheAnswerWithItsReasonFactsModelPromptVersionAndHash() {
        FeatureWorkToJudge item = stored.item(100, List.of(), 1);
        model.answeringByDefault(FakeJudgmentModel.aFeatureWork("medium"));

        judge.judge(item, QUARTER, jobCounts);

        Judgment judgment = currentJudgmentOf(item);
        assertEquals("Two modules and one schema change.", judgment.getReason());
        assertEquals(FakeJudgmentModel.MODEL, judgment.getModel());
        assertEquals("feature-work-complexity/v1", judgment.getPromptVersion());
        assertEquals(JudgedBy.MODEL, judgment.getJudgedBy());
        assertEquals(64, judgment.getInputHash().length());
        assertNull(judgment.getBasedOnJudgmentId());
        assertEquals("feature", judgment.getValue().path("workType").asString());
        assertTrue(judgment.getValue().path("confidence").isMissingNode());
        assertFalse(judgment.getValue().path("judgedOnPartialInput").asBoolean());
        assertEquals(
                List.of(
                        new JudgmentFact(
                                "The feature adds a table (\"Nature of the change\")",
                                "https://github.com/opf/openproject/pull/1"),
                        new JudgmentFact(
                                "The ticket asks for a series (\"Users miss alerts\")",
                                "https://community.openproject.org/wp/100")),
                judgment.getFacts());
    }

    @Test
    void storesTheModelsConfidenceInItsColumn() {
        FeatureWorkToJudge item = stored.item(100, List.of(), 1);
        model.answeringByDefault(FakeJudgmentModel.aFeatureWork("medium"));

        judge.judge(item, QUARTER, jobCounts);

        assertEquals(0.8, currentJudgmentOf(item).getConfidence());
        assertEquals(
                0,
                new BigDecimal("0.8")
                        .compareTo(jdbc.queryForObject(
                                "select confidence from judgment where kind = 'feature_work_complexity'",
                                BigDecimal.class)));
    }

    @Test
    void countsTheWrittenJudgmentAndTheModelUse() {
        FeatureWorkToJudge item = stored.item(100, List.of(), 1);
        model.answeringByDefault(FakeJudgmentModel.aFeatureWork("low"));

        judge.judge(item, QUARTER, jobCounts);

        assertEquals(1L, counts.get("judgments_written"));
        assertEquals(1L, counts.get("model_calls"));
        assertNull(counts.get("judged_on_partial_input"));
    }

    @Test
    void aFactWithAnUnknownSourceGetsTheLinkOfTheItem() {
        FeatureWorkToJudge item = stored.item(100, List.of(), 1);
        ObjectNode answer = FakeJudgmentModel.aFeatureWork("low");
        ((ObjectNode) answer.path("facts").get(0)).put("source", "WI-999");
        ((ObjectNode) answer.path("facts").get(1)).put("source", "").put("quote", "");
        model.answeringByDefault(answer);

        judge.judge(item, QUARTER, jobCounts);

        List<JudgmentFact> facts = currentJudgmentOf(item).getFacts();
        assertEquals("https://community.openproject.org/wp/100", facts.get(0).link());
        assertEquals("https://community.openproject.org/wp/100", facts.get(1).link());
        assertEquals("The ticket asks for a series", facts.get(1).fact());
    }

    @Test
    void sendsThePromptTheSchemaAndTheInputWithoutAnyLineCount() {
        FeatureWorkToJudge item = stored.item(100, List.of(), 1);
        model.answeringByDefault(FakeJudgmentModel.aFeatureWork("low"));

        judge.judge(item, QUARTER, jobCounts);

        ModelRequest request = model.requests().getFirst();
        assertEquals("feature-work-complexity/v1", request.promptVersion());
        assertEquals(promptLibrary.instructions("feature-work-complexity/v1"), request.instructions());
        assertEquals(promptLibrary.schema("feature-work-complexity/v1"), request.jsonSchema());
        assertTrue(request.input().contains("[WI-100] Work item 100"));
        assertTrue(request.input().contains("[PR-1] Pull request 1"));
        assertFalse(request.input().toLowerCase(Locale.ROOT).contains("lines"));
        assertFalse(request.input().toLowerCase(Locale.ROOT).contains("additions"));
    }

    @Test
    void countsAnItemWhosePullRequestsWereLeftOutForSize() {
        stored.pullRequest(2, "2026-07-03T08:00:00Z");
        stored.digest(2);
        FeatureWorkToJudge item = stored.item(100, List.of(), 1, 2);
        model.answeringByDefault(FakeJudgmentModel.aFeatureWork("low"));
        JudgmentProperties small = JudgmentPropertiesBuilder.properties()
                .maxFeatureWorkCharacters(10)
                .build();

        judgeWith(small).judge(item, QUARTER, jobCounts);

        Judgment judgment = currentJudgmentOf(item);
        assertTrue(judgment.getValue().path("judgedOnPartialInput").asBoolean());
        assertEquals(
                "opf/openproject#2",
                judgment.getValue().path("leftOutPullRequests").get(0).asString());
        assertEquals(1L, counts.get("judged_on_partial_input"));
    }

    @Test
    void waitsForAPullRequestWithoutADigestAndMakesNoCall() {
        stored.pullRequest(2, "2026-07-03T08:00:00Z");
        FeatureWorkToJudge item = stored.item(100, List.of(), 1, 2);

        judge.judge(item, QUARTER, jobCounts);

        assertEquals(0, model.calls());
        assertEquals(1L, counts.get("items_waiting_for_digest"));
        assertNull(counts.get("judgments_written"));
    }

    @Test
    void doesNotJudgeTheSameInputTwice() {
        FeatureWorkToJudge item = stored.item(100, List.of(), 1);
        model.answeringByDefault(FakeJudgmentModel.aFeatureWork("low"));
        judge.judge(item, QUARTER, jobCounts);

        counts.clear();
        judge.judge(item, QUARTER, jobCounts);

        assertEquals(1, model.calls());
        assertEquals(1L, counts.get("skipped_same_input"));
        assertNull(counts.get("model_calls"));
    }

    @Test
    void aNewDigestOfAPullRequestIsJudgedAgainAndTheNewJudgmentSupersedesTheOld() {
        FeatureWorkToJudge item = stored.item(100, List.of(), 1);
        model.answeringByDefault(FakeJudgmentModel.aFeatureWork("low"));
        judge.judge(item, QUARTER, jobCounts);
        Judgment first = currentJudgmentOf(item);
        stored.digest(1);

        judge.judge(item, QUARTER, jobCounts);

        assertEquals(2, model.calls());
        assertEquals(first.getId(), currentJudgmentOf(item).getSupersedesId());
    }

    @Test
    void asksOnceMoreWhenTheAnswerBreaksTheSchemaAndFailsAfterTheSecondRefusal() {
        FeatureWorkToJudge item = stored.item(100, List.of(), 1);
        ObjectNode broken = FakeJudgmentModel.aFeatureWork("low").put("confidence", 2);
        model.thenAnswers(broken).thenAnswers(FakeJudgmentModel.aFeatureWork("low"));

        judge.judge(item, QUARTER, jobCounts);

        assertEquals(2, model.calls());
        assertEquals(1L, counts.get("judgments_written"));

        FeatureWorkToJudge other = stored.item(100, List.of(), 1);
        stored.digest(1);
        model.thenAnswers(broken).thenAnswers(broken);
        assertThrows(InvalidAnswerException.class, () -> judge.judge(other, QUARTER, jobCounts));
    }

    @Test
    void failsNamingTheComplexityWhenTheSettingHasNoTierForIt() {
        FeatureWorkToJudge item = stored.item(100, List.of(), 1);
        model.answeringByDefault(FakeJudgmentModel.aFeatureWork("high"));
        jdbc.update("update setting set value = '{\"low\": \"T1\"}'::jsonb where key = 'evidence.complexity_tiers'");

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> judge.judge(item, QUARTER, jobCounts));

        assertTrue(failure.getMessage().contains("evidence.complexity_tiers"));
        assertTrue(failure.getMessage().contains("high"));
    }

    @Test
    void failsNamingTheWorkItemWhenItIsNotStored() {
        FeatureWorkToJudge item = stored.item(100, List.of(), 1);
        FeatureWorkToJudge withTask = new FeatureWorkToJudge(
                item.itemRowId(),
                item.key(),
                item.sourceUrl(),
                item.sourceRecordIds(),
                item.featureNumber(),
                List.of(555L),
                item.pullRequestExternalIds());

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> judge.judge(withTask, QUARTER, jobCounts));

        assertTrue(failure.getMessage().contains("555"));
        assertEquals(0, model.calls());
    }
}
