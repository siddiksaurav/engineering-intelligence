package com.dsinnovators.ei.scoring;

import static com.dsinnovators.ei.scoring.ScoringFixtures.coding;
import static com.dsinnovators.ei.scoring.ScoringFixtures.featureWork;
import static com.dsinnovators.ei.scoring.ScoringFixtures.judged;
import static com.dsinnovators.ei.scoring.ScoringFixtures.settings;
import static com.dsinnovators.ei.scoring.ScoringFixtures.standards;
import static com.dsinnovators.ei.scoring.ScoringFixtures.unjudged;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.evidence.EvidenceType;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class DimensionScorerTest {

    private final DimensionScorer scorer = new DimensionScorer(settings());
    private final List<ScoredItem> aT3Item = List.of(judged(EvidenceType.SECURITY, Tier.T3));

    @Test
    void aCloseMissHoldsATwoAtOne() {
        DimensionScoreResult result = scorer.score(standards(), Level.L2, aT3Item, gates(3, 10, 5, 10));

        assertEquals(1, result.score());
        assertEquals(2, result.trail().scoreBeforeGates());
        assertTrue(result.trail().notes().contains("held: G1"));
    }

    @Test
    void aCloseMissLeavesAOneAtOne() {
        List<ScoredItem> items = List.of(judged(EvidenceType.SECURITY, Tier.T2));

        assertEquals(
                1,
                scorer.score(standards(), Level.L2, items, gates(3, 10, 5, 10)).score());
    }

    @Test
    void aFarMissGivesZero() {
        DimensionScoreResult result = scorer.score(standards(), Level.L2, aT3Item, gates(0, 10, 5, 10));

        assertEquals(0, result.score());
        assertTrue(result.trail().notes().contains("gate: G2 far"));
    }

    @Test
    void anUnverifiedGateHasNoEffectAndIsNoted() {
        Map<Gate, GateNumbers> numbers = Map.of(
                Gate.G1, numbers(0, 0, 5, 10, 3),
                Gate.G2, numbers(5, 10, 5, 10, 3));

        DimensionScoreResult result = scorer.score(standards(), Level.L2, aT3Item, numbers);

        assertEquals(2, result.score());
        assertTrue(result.trail().notes().contains("gate unverified: G1"));
    }

    @Test
    void g4IsAlwaysUnverified() {
        DimensionScoreResult result = scorer.score(standards(), Level.L2, aT3Item, gates(5, 10, 5, 10));

        GateResult g4 = result.gates().get(2);
        assertEquals(Gate.G4, g4.gate());
        assertEquals(UnverifiedReason.NOT_CALCULATED, g4.unverifiedReason());
        assertTrue(result.trail().notes().contains("gate unverified: G4"));
    }

    @Test
    void scoreFollowsTheWorkingTierAgainstTheLevel() {
        List<ScoredItem> t2Item = List.of(judged(EvidenceType.SECURITY, Tier.T2));

        assertEquals(
                0, scorer.score(standards(), Level.L3, t2Item, passingGates()).score());
        assertEquals(
                1, scorer.score(standards(), Level.L2, t2Item, passingGates()).score());
        assertEquals(
                2, scorer.score(standards(), Level.L1, t2Item, passingGates()).score());
    }

    @Test
    void noItemsGiveZeroWithoutWorkingTier() {
        DimensionScoreResult result = scorer.score(standards(), Level.L2, List.of(), passingGates());

        assertEquals(0, result.score());
        assertNull(result.workingTier());
    }

    @Test
    void bugFixItemsAreCountedNotTiered() {
        ScoredItem bugFix = unjudged(EvidenceType.BUG_FIX);

        DimensionScoreResult result = scorer.score(standards(), Level.L2, List.of(bugFix), passingGates());

        assertEquals(
                List.of(bugFix.evidenceItemId()),
                result.trail().countedNotTiered().stream()
                        .map(TrailItem::evidenceItemId)
                        .toList());
        assertTrue(result.trail().notes().contains("bug_fix: counted, not tiered"));
        assertNull(result.workingTier());
    }

    @Test
    void theTrailNamesTheStandInsAndTheVersions() {
        DimensionScoreResult result = scorer.score(standards(), Level.L2, aT3Item, passingGates());

        assertTrue(result.trail().notes().contains("the repository stands in for the project"));
        assertTrue(result.trail().notes().contains("changes tests stands in for adds a regression test"));
        assertEquals("2026-Q2", result.trail().evidenceTypesVersion());
        assertEquals("2026-Q2", result.trail().gatesVersion());
    }

    @Test
    void codingNotesTheCleanDeliveryRuleAndHasNoGates() {
        DimensionScoreResult result =
                scorer.score(coding(), Level.L2, List.of(featureWork(Tier.T2, "medium")), Map.of());

        assertEquals(1, result.score());
        assertTrue(result.gates().isEmpty());
        assertNull(result.trail().gatesVersion());
        assertEquals(List.of("clean-delivery rule not applied"), result.trail().notes());
    }

    @Test
    void anAppliedGateWithoutNumbersIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> scorer.score(standards(), Level.L2, aT3Item, Map.of()));
    }

    @Test
    void theTrailSerialisesToJsonWithTheCountedItems() {
        DimensionScoreResult result = scorer.score(standards(), Level.L2, aT3Item, passingGates());

        JsonNode json = JsonMapper.builder().build().valueToTree(result.trail());

        assertEquals("T3", json.get("workingTier").stringValue());
        assertEquals(2, json.get("finalScore").intValue());
        assertEquals(
                "T3",
                json.get("evidenceTypes")
                        .get(2)
                        .get("counted")
                        .get(0)
                        .get("tier")
                        .stringValue());
    }

    private static Map<Gate, GateNumbers> passingGates() {
        return gates(5, 10, 5, 10);
    }

    /** G1 and G2 get the same numbers. */
    private static Map<Gate, GateNumbers> gates(
            int personTested, int personTotal, long repositoryTested, long repositoryTotal) {
        GateNumbers numbers = numbers(personTested, personTotal, repositoryTested, repositoryTotal, 3);
        return Map.of(Gate.G1, numbers, Gate.G2, numbers);
    }

    private static GateNumbers numbers(
            int personTested, int personTotal, long repositoryTested, long repositoryTotal, int developers) {
        return new GateNumbers(
                IntStream.range(0, personTested).boxed().toList(),
                IntStream.range(0, personTotal).boxed().toList(),
                repositoryTested,
                repositoryTotal,
                developers,
                0);
    }
}
