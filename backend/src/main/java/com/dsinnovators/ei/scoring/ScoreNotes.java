package com.dsinnovators.ei.scoring;

/** The note texts stored on a trail (spec 9.1 step 6, decisions 210, 212, 214), kept in one place. */
final class ScoreNotes {

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
}
