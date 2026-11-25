package com.dsinnovators.ei.scoring;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.evidence.EvidenceType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Seeded scoring settings, the seeded mapping of the two dimensions and scored items, for plain unit tests. */
final class ScoringFixtures {

    static final String CODING = "coding_and_code_quality";
    static final String STANDARDS = "engineering_standards_and_system_health";

    private static final AtomicLong NEXT_ID = new AtomicLong();

    private ScoringFixtures() {}

    static ScoringSettings settings() {
        return settings(Map.of(Gate.G1, true, Gate.G2, false));
    }

    /** The seeded settings with another switch of which gates are scored. */
    static ScoringSettings settings(Map<Gate, Boolean> gateScored) {
        GateLimits limits = new GateLimits(10, 25);
        return new ScoringSettings(
                new PresenceThreshold(1, 3),
                Map.of(Tier.T1, 1, Tier.T2, 2, Tier.T3, 4),
                Set.of(EvidenceType.FEATURE_WORK, EvidenceType.REVIEW_COMMENT_WRITTEN, EvidenceType.TEST_CHANGE),
                Map.of(STANDARDS, 2),
                Map.of(Gate.G1, limits, Gate.G2, limits),
                gateScored,
                3,
                1);
    }

    static DimensionMapping coding() {
        return new DimensionMapping(
                1,
                CODING,
                "Coding and code quality",
                new Quarter(2026, 2),
                List.of(
                        new MappedEvidenceType(EvidenceType.FEATURE_WORK, true, Level.L1),
                        new MappedEvidenceType(EvidenceType.REVIEW_COMMENT_WRITTEN, true, Level.L2)),
                Optional.empty(),
                List.of());
    }

    static DimensionMapping standards() {
        return new DimensionMapping(
                2,
                STANDARDS,
                "Engineering standards and system health",
                new Quarter(2026, 2),
                List.of(
                        new MappedEvidenceType(EvidenceType.TEST_CHANGE, true, Level.L1),
                        new MappedEvidenceType(EvidenceType.BUG_FIX, false, Level.L1),
                        new MappedEvidenceType(EvidenceType.REFACTOR_PERFORMANCE, true, Level.L1),
                        new MappedEvidenceType(EvidenceType.SECURITY, true, Level.L1),
                        new MappedEvidenceType(EvidenceType.INFRA_RELEASE, true, Level.L1)),
                Optional.of(new Quarter(2026, 2)),
                List.of(new MappedGate(Gate.G1, true), new MappedGate(Gate.G2, true), new MappedGate(Gate.G4, false)));
    }

    static ScoredItem judged(EvidenceType type, Tier tier) {
        return item(type, JudgmentState.JUDGED, tier, null);
    }

    static ScoredItem featureWork(Tier tier, String complexity) {
        return item(EvidenceType.FEATURE_WORK, JudgmentState.JUDGED, tier, complexity);
    }

    static ScoredItem unjudged(EvidenceType type) {
        return item(type, JudgmentState.NOT_JUDGED_YET, null, null);
    }

    static ScoredItem notSubstantive() {
        return item(EvidenceType.REVIEW_COMMENT_WRITTEN, JudgmentState.NOT_SUBSTANTIVE, null, null);
    }

    private static ScoredItem item(EvidenceType type, JudgmentState state, Tier tier, String complexity) {
        long id = NEXT_ID.incrementAndGet();
        return new ScoredItem(
                id,
                type.databaseName() + "-" + id,
                type,
                state,
                tier,
                complexity,
                List.of((int) id),
                "https://example.test/" + id);
    }
}
