package com.dsinnovators.ei.scoring;

import com.dsinnovators.ei.evidence.EvidenceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Scores one person in one dimension at a selected level (spec 9.1 steps 5 and 6). */
public final class DimensionScorer {

    private final ScoringSettings settings;
    private final WorkingTierCalculator workingTierCalculator;
    private final GateCheck gateCheck;

    public DimensionScorer(ScoringSettings settings) {
        this.settings = settings;
        this.workingTierCalculator = new WorkingTierCalculator(settings);
        this.gateCheck = new GateCheck(settings);
    }

    /**
     * Scores {@code items}, the person's items of the quarter, for the dimension at the level.
     *
     * @param gateNumbers the numbers of every applied gate of the dimension; an applied gate without numbers is refused
     */
    public DimensionScoreResult score(
            DimensionMapping mapping, Level level, List<ScoredItem> items, Map<Gate, GateNumbers> gateNumbers) {
        WorkingTier workingTier = workingTierCalculator.calculate(mapping, level, items);
        int scoreBeforeGates = scoreOf(workingTier.tier(), level);
        List<GateResult> gates = mapping.gates().stream()
                .map(mappedGate -> checkGate(mapping, mappedGate, gateNumbers))
                .toList();
        int finalScore = applyGates(scoreBeforeGates, gates);
        List<TrailItem> countedNotTiered = countedNotTiered(mapping, items);
        DimensionTrail trail = new DimensionTrail(
                mapping.key(),
                level,
                mapping.evidenceTypesVersion().toString(),
                mapping.gatesVersion().map(Object::toString).orElse(null),
                settings,
                workingTier.evidenceTypes(),
                workingTier.levelThreeRule(),
                workingTier.tier(),
                scoreBeforeGates,
                gates,
                countedNotTiered,
                notesOf(mapping, gates),
                finalScore);
        return new DimensionScoreResult(
                mapping.dimensionId(), mapping.key(), level, workingTier.tier(), finalScore, gates, trail);
    }

    /** Lower than the level gives 0, the same number 1, higher 2; no working tier gives 0. */
    private static int scoreOf(Tier workingTier, Level level) {
        if (workingTier == null) {
            return 0;
        }
        return Integer.signum(workingTier.compareTo(level.sameNumberTier())) + 1;
    }

    private GateResult checkGate(DimensionMapping mapping, MappedGate mappedGate, Map<Gate, GateNumbers> gateNumbers) {
        if (!mappedGate.applied()) {
            return GateResult.notCalculated(mappedGate.gate());
        }
        GateNumbers numbers = Optional.ofNullable(gateNumbers.get(mappedGate.gate()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No numbers for gate " + mappedGate.gate() + " of dimension " + mapping.key()));
        return gateCheck.check(mappedGate.gate(), numbers);
    }

    /** A far miss gives 0, a close miss turns a 2 into a 1, nothing else changes the score. */
    private static int applyGates(int score, List<GateResult> gates) {
        if (hasOutcome(gates, GateOutcome.FAR_MISS)) {
            return 0;
        }
        if (hasOutcome(gates, GateOutcome.CLOSE_MISS)) {
            return Math.min(score, 1);
        }
        return score;
    }

    private static boolean hasOutcome(List<GateResult> gates, GateOutcome outcome) {
        return gates.stream().anyMatch(gate -> gate.outcome() == outcome);
    }

    private static List<TrailItem> countedNotTiered(DimensionMapping mapping, List<ScoredItem> items) {
        List<EvidenceType> types = untieredTypes(mapping);
        return items.stream()
                .filter(item -> types.contains(item.evidenceType()))
                .map(item -> TrailItem.of(item, null))
                .toList();
    }

    private static List<EvidenceType> untieredTypes(DimensionMapping mapping) {
        return mapping.evidenceTypes().stream()
                .filter(mapped -> !mapped.tiered())
                .map(MappedEvidenceType::evidenceType)
                .toList();
    }

    private static List<String> notesOf(DimensionMapping mapping, List<GateResult> gates) {
        List<String> notes = new ArrayList<>();
        untieredTypes(mapping).stream().map(ScoreNotes::countedNotTiered).forEach(notes::add);
        gates.stream().map(GateResult::note).filter(note -> note != null).forEach(notes::add);
        if (hasAppliedGate(gates, Gate.G1) || hasAppliedGate(gates, Gate.G2)) {
            notes.add(ScoreNotes.REPOSITORY_STANDS_IN_FOR_PROJECT);
        }
        if (hasAppliedGate(gates, Gate.G2)) {
            notes.add(ScoreNotes.CHANGES_TESTS_STANDS_IN_FOR_REGRESSION_TEST);
        }
        boolean readsFeatureWork =
                mapping.evidenceTypes().stream().anyMatch(mapped -> mapped.evidenceType() == EvidenceType.FEATURE_WORK);
        if (readsFeatureWork) {
            notes.add(ScoreNotes.CLEAN_DELIVERY_NOT_APPLIED);
        }
        return List.copyOf(notes);
    }

    private static boolean hasAppliedGate(List<GateResult> gates, Gate gate) {
        return gates.stream()
                .anyMatch(result ->
                        result.gate() == gate && result.unverifiedReason() != UnverifiedReason.NOT_CALCULATED);
    }
}
