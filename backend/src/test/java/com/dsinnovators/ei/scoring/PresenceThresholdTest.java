package com.dsinnovators.ei.scoring;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PresenceThresholdTest {

    private final PresenceThreshold oneThird = new PresenceThreshold(1, 3);

    @Test
    void exactlyOneThirdIsEnough() {
        assertTrue(oneThird.isReachedBy(1, 3));
        assertTrue(oneThird.isReachedBy(4, 12));
    }

    @Test
    void justBelowOneThirdIsNotEnough() {
        assertFalse(oneThird.isReachedBy(1, 4));
        assertFalse(oneThird.isReachedBy(3, 10));
    }

    @Test
    void aFractionAboveOneIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new PresenceThreshold(4, 3));
    }
}
