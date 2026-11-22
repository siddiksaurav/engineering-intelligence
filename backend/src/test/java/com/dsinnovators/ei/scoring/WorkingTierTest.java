package com.dsinnovators.ei.scoring;

import static com.dsinnovators.ei.scoring.ScoringFixtures.coding;
import static com.dsinnovators.ei.scoring.ScoringFixtures.featureWork;
import static com.dsinnovators.ei.scoring.ScoringFixtures.judged;
import static com.dsinnovators.ei.scoring.ScoringFixtures.notSubstantive;
import static com.dsinnovators.ei.scoring.ScoringFixtures.settings;
import static com.dsinnovators.ei.scoring.ScoringFixtures.standards;
import static com.dsinnovators.ei.scoring.ScoringFixtures.unjudged;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.evidence.EvidenceType;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class WorkingTierTest {

    private final WorkingTierCalculator calculator = new WorkingTierCalculator(settings());

    @Test
    void twoLowAndFiveMediumFeatureWorkGiveT2() {
        List<ScoredItem> items = new ArrayList<>(repeat(2, featureWork(Tier.T1, "low")));
        items.addAll(repeat(5, featureWork(Tier.T2, "medium")));

        WorkingTier result = calculator.calculate(coding(), Level.L2, items);

        TierPresence atT2 = presenceOf(result, "feature_work", Tier.T2);
        assertEquals(12, atT2.total());
        assertEquals(10, atT2.atOrAbove());
        assertTrue(atT2.presence());
        assertEquals(Tier.T2, result.tier());
    }

    @Test
    void aVeryHighFeatureWorkItemCountsAsT3WithWeightFour() {
        List<ScoredItem> items =
                List.of(featureWork(Tier.T3, "very_high"), featureWork(Tier.T1, "low"), featureWork(Tier.T1, "low"));

        WorkingTier result = calculator.calculate(coding(), Level.L2, items);

        TierPresence atT3 = presenceOf(result, "feature_work", Tier.T3);
        assertEquals(4, atT3.atOrAbove());
        assertEquals(6, atT3.total());
        assertEquals(Tier.T3, result.tier());
    }

    @Test
    void justBelowOneThirdGivesNoPresenceAtThatTier() {
        List<ScoredItem> items = new ArrayList<>(repeat(2, judged(EvidenceType.TEST_CHANGE, Tier.T2)));
        items.addAll(repeat(5, judged(EvidenceType.TEST_CHANGE, Tier.T1)));

        WorkingTier result = calculator.calculate(standards(), Level.L2, items);

        assertEquals(Tier.T1, result.tier());
        assertTrue(!presenceOf(result, "test_change", Tier.T2).presence());
    }

    @Test
    void oneItemGivesAnAnchorForARareType() {
        WorkingTier result =
                calculator.calculate(standards(), Level.L2, List.of(judged(EvidenceType.SECURITY, Tier.T2)));

        assertEquals(Tier.T2, result.tier());
    }

    @Test
    void noItemGivesNoWorkingTier() {
        WorkingTier result = calculator.calculate(standards(), Level.L2, List.of());

        assertNull(result.tier());
    }

    @Test
    void itemsNotJudgedYetAndNotSubstantiveAreLeftOutAndKept() {
        List<ScoredItem> items = List.of(
                judged(EvidenceType.REVIEW_COMMENT_WRITTEN, Tier.T2),
                unjudged(EvidenceType.REVIEW_COMMENT_WRITTEN),
                notSubstantive());

        EvidenceTypeTrail trail = calculator.calculate(coding(), Level.L2, items).evidenceTypes().stream()
                .filter(candidate -> candidate.evidenceType().equals("review_comment_written"))
                .findFirst()
                .orElseThrow();

        assertEquals(1, trail.counted().size());
        assertEquals(1, trail.notJudgedYet().size());
        assertEquals(1, trail.notSubstantive().size());
        assertEquals(1, trail.presence().get(0).total());
    }

    @Test
    void reviewCommentsAreNotReadAtLevelOne() {
        List<ScoredItem> items = List.of(judged(EvidenceType.REVIEW_COMMENT_WRITTEN, Tier.T3));

        WorkingTier result = calculator.calculate(coding(), Level.L1, items);

        assertNull(result.tier());
        EvidenceTypeTrail trail = result.evidenceTypes().stream()
                .filter(candidate -> candidate.evidenceType().equals("review_comment_written"))
                .findFirst()
                .orElseThrow();
        assertEquals(1, trail.notReadAtLevel().size());
        assertTrue(trail.counted().isEmpty());
    }

    @Test
    void oneT3ItemIsNotEnoughForEngineeringStandardsAtLevelThree() {
        List<ScoredItem> items =
                List.of(judged(EvidenceType.SECURITY, Tier.T3), judged(EvidenceType.INFRA_RELEASE, Tier.T2));

        WorkingTier result = calculator.calculate(standards(), Level.L3, items);

        assertEquals(Tier.T2, result.tier());
        assertEquals(new LevelThreeRule(1, 2, false), result.levelThreeRule());
    }

    @Test
    void twoT3ItemsAcrossTypesAreEnoughAtLevelThree() {
        List<ScoredItem> items =
                List.of(judged(EvidenceType.SECURITY, Tier.T3), judged(EvidenceType.INFRA_RELEASE, Tier.T3));

        WorkingTier result = calculator.calculate(standards(), Level.L3, items);

        assertEquals(Tier.T3, result.tier());
        assertEquals(new LevelThreeRule(2, 2, true), result.levelThreeRule());
    }

    @Test
    void oneT3ItemIsEnoughBelowLevelThree() {
        WorkingTier result =
                calculator.calculate(standards(), Level.L2, List.of(judged(EvidenceType.SECURITY, Tier.T3)));

        assertEquals(Tier.T3, result.tier());
        assertNull(result.levelThreeRule());
    }

    private static List<ScoredItem> repeat(int count, ScoredItem model) {
        return IntStream.range(0, count)
                .mapToObj(index -> new ScoredItem(
                        model.evidenceItemId() * 1000 + index,
                        model.itemKey() + "-" + index,
                        model.evidenceType(),
                        model.judgmentState(),
                        model.tier(),
                        model.complexity(),
                        model.pullRequestNumbers(),
                        model.sourceUrl()))
                .toList();
    }

    private static TierPresence presenceOf(WorkingTier result, String evidenceType, Tier tier) {
        return result.evidenceTypes().stream()
                .filter(trail -> trail.evidenceType().equals(evidenceType))
                .flatMap(trail -> trail.presence().stream())
                .filter(presence -> presence.tier() == tier)
                .findFirst()
                .orElseThrow();
    }
}
