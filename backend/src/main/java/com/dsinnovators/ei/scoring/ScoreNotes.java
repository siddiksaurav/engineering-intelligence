package com.dsinnovators.ei.scoring;

import com.dsinnovators.ei.evidence.EvidenceType;

/** The note texts stored on a trail (spec 9.1 step 6, decisions 210, 212, 214), kept in one place. */
final class ScoreNotes {

    static final String CLEAN_DELIVERY_NOT_APPLIED = "clean-delivery rule not applied";
    static final String REPOSITORY_STANDS_IN_FOR_PROJECT = "the repository stands in for the project";
    static final String CHANGES_TESTS_STANDS_IN_FOR_REGRESSION_TEST =
            "changes tests stands in for adds a regression test";

    private ScoreNotes() {}

    /** The note of a gate result; null when the gate passes. */
    static String forGate(Gate gate, GateOutcome outcome) {
        return switch (outcome) {
            case PASS -> null;
            case CLOSE_MISS -> "held: " + gate;
            case FAR_MISS -> "gate: " + gate + " far";
            case UNVERIFIED -> "gate unverified: " + gate;
        };
    }

    static String countedNotTiered(EvidenceType type) {
        return type.databaseName() + ": counted, not tiered";
    }
}
