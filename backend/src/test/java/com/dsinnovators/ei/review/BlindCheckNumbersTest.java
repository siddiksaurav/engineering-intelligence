package com.dsinnovators.ei.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.setting.SettingNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BlindCheckNumbersTest extends DatabaseTest {

    @Autowired
    private BlindCheckSample sample;

    @Autowired
    private LabelImport labelImport;

    @Autowired
    private ReasonMarks reasonMarks;

    @Autowired
    private BlindCheckNumbers numbers;

    private BlindCheckFixture stored;
    private long thinLow;
    private long lowLabeledMedium;
    private long mediumRightLabel;
    private long mediumLabeledHigh;
    private long highLabeledLow;
    private long highCannotJudge;

    @BeforeEach
    void pickASampleOfSix() {
        stored = new BlindCheckFixture(jdbc);
        jdbc.update("update setting set value = '2'::jsonb where key = 'blind_check.items_per_complexity'");
        thinLow = stored.judgedItem("low", true);
        lowLabeledMedium = stored.judgedItem("low");
        mediumRightLabel = stored.judgedItem("medium");
        mediumLabeledHigh = stored.judgedItem("medium");
        highLabeledLow = stored.judgedItem("high");
        highCannotJudge = stored.judgedItem("high");
        sample.pick();
    }

    private void label() {
        labelImport.importLabels("item_id,complexity,note\n" + thinLow + ",low,\n" + lowLabeledMedium
                + ",medium,looks bigger\n" + mediumRightLabel + ",medium,\n" + mediumLabeledHigh + ",high,\n"
                + highLabeledLow + ",low,\n" + highCannotJudge + ",cannot_judge,no ticket\n");
    }

    private void mark(long itemId, boolean reasonRight, String verdict) {
        reasonMarks.mark(itemId, reasonRight, List.of(new ProblemMark("Cache invalidation", verdict)), "n" + itemId);
    }

    private static Set<Long> idsOf(List<ItemOutcome> outcomes) {
        return outcomes.stream().map(ItemOutcome::itemId).collect(Collectors.toSet());
    }

    @Test
    void exactMatchWithinOneStepAndReasonRightAreCountedOverTheRightItems() {
        label();
        mark(thinLow, true, "right");
        mark(mediumRightLabel, false, "should_be_routine");
        mark(highLabeledLow, true, "right");

        BlindCheckResult result = numbers.compute();
        BlindCheckSummary summary = result.summary();

        assertEquals(6, summary.sampled());
        assertEquals(6, summary.labeled());
        assertEquals(1, summary.cannotJudge());
        assertEquals(3, summary.reasonMarked());
        assertEquals(new Rate(2, 5, 0.4), summary.exactMatch());
        assertEquals(new Rate(4, 5, 0.8), summary.withinOneStep());
        assertEquals(new Rate(2, 5, 0.4), summary.tableExactMatch());
        assertEquals(new Rate(2, 3, 2.0 / 3.0), summary.reasonRight());
        assertEquals(
                Set.of(
                        new ConfusionCell("low", "low", 1),
                        new ConfusionCell("medium", "low", 1),
                        new ConfusionCell("medium", "medium", 1),
                        new ConfusionCell("high", "medium", 1),
                        new ConfusionCell("low", "high", 1)),
                Set.copyOf(summary.confusion()));
        assertEquals(
                Map.of(
                        "right", 2,
                        "should_be_routine", 1,
                        "should_be_answer_exists", 0,
                        "should_be_no_ready_answer", 0,
                        "not_a_problem", 0),
                summary.problemVerdicts());
        assertEquals(new ThinEvidence(1, new Rate(1, 1, 1.0), new Rate(1, 1, 1.0)), summary.thinEvidence());
        assertEquals(new Targets(0.70, 0.95, 0.90), summary.targets());
        assertEquals(
                Set.of(lowLabeledMedium, mediumRightLabel, mediumLabeledHigh, highLabeledLow),
                idsOf(result.disagreements()));
        assertEquals(Set.of(lowLabeledMedium, mediumLabeledHigh), idsOf(result.reasonsToMark()));
        assertEquals(Set.of(highCannotJudge), idsOf(result.cannotJudgeItems()));
        ItemOutcome wrongReason = result.disagreements().stream()
                .filter(outcome -> outcome.itemId() == mediumRightLabel)
                .findFirst()
                .orElseThrow();
        assertEquals(false, wrongReason.reasonRight());
        assertEquals("n" + mediumRightLabel, wrongReason.markNote());
        assertEquals("Reason for medium item " + mediumRightLabel, wrongReason.aiReason());
        assertEquals("Ada", wrongReason.developer());
    }

    @Test
    void everyShareIsNullBeforeAnyLabel() {
        BlindCheckSummary summary = numbers.compute().summary();

        assertEquals(6, summary.sampled());
        assertEquals(0, summary.labeled());
        assertEquals(new Rate(0, 0, null), summary.exactMatch());
        assertNull(summary.withinOneStep().share());
        assertNull(summary.reasonRight().share());
        assertNull(summary.thinEvidence().exactMatch().share());
        assertEquals(List.of(), summary.confusion());
    }

    @Test
    void aMissingTargetSettingIsAnErrorNotADefault() {
        jdbc.update("delete from setting where key = 'blind_check.targets'");

        assertThrows(SettingNotFoundException.class, () -> numbers.compute());
    }

    @Test
    void theNumbersKeepTheJudgmentTheSampleFixedAfterANewJudgmentOfTheItem() {
        label();
        long fixedJudgment = jdbc.queryForObject(
                "select judgment_id from blind_check_item where evidence_item_id = ?", Long.class, lowLabeledMedium);
        stored.judgment(lowLabeledMedium, "medium", fixedJudgment);

        BlindCheckSummary summary = numbers.compute().summary();

        assertEquals(new Rate(2, 5, 0.4), summary.exactMatch());
        assertEquals(
                1,
                summary.confusion().stream()
                        .filter(cell -> cell.equals(new ConfusionCell("medium", "low", 1)))
                        .count());
    }

    @Test
    void aVeryHighLabelAgainstAnAiHighIsWithinOneStepAndNotExact() {
        labelImport.importLabels("item_id,complexity,note\n" + highLabeledLow + ",very_high,\n");

        BlindCheckSummary summary = numbers.compute().summary();

        assertEquals(new Rate(0, 1, 0.0), summary.exactMatch());
        assertEquals(new Rate(1, 1, 1.0), summary.withinOneStep());
        assertEquals(Set.of(new ConfusionCell("very_high", "high", 1)), Set.copyOf(summary.confusion()));
    }
}
