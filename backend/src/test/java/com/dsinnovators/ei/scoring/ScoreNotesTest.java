package com.dsinnovators.ei.scoring;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Locale;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class ScoreNotesTest {

    @Test
    void noNoteOfAGateResultSaysGate() {
        for (Gate gate : Gate.values()) {
            for (GateOutcome outcome : GateOutcome.values()) {
                String note = Objects.toString(ScoreNotes.forGate(gate, outcome), "");
                assertFalse(
                        note.toLowerCase(Locale.ROOT).contains("gate"),
                        "note of " + gate + " " + outcome + ": " + note);
            }
            String unscored = ScoreNotes.notScoredYet(gate);
            assertFalse(unscored.toLowerCase(Locale.ROOT).contains("gate"), "note: " + unscored);
        }
    }
}
