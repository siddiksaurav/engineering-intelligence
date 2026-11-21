package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModelJobGuardTest {

    private final ModelJobGuard guard = new ModelJobGuard();

    @Test
    void refusesASecondModelJobWhileOneRunsAndNamesTheRunningOne() {
        ModelJobGuard.Run digest = guard.enter("pull-request-digest");
        try {
            IllegalStateException refused =
                    assertThrows(IllegalStateException.class, () -> guard.enter("feature-work-judgment"));

            assertTrue(refused.getMessage().contains("feature-work-judgment"), refused.getMessage());
            assertTrue(refused.getMessage().contains("pull-request-digest"), refused.getMessage());
        } finally {
            digest.close();
        }
    }

    @Test
    void letsTheNextJobStartWhenTheRunningOneEnds() {
        guard.enter("pull-request-digest").close();

        assertDoesNotThrow(() -> guard.enter("feature-work-judgment").close());
    }

    @Test
    void closingARunTwiceDoesNotEndTheRunOfAnotherJob() {
        ModelJobGuard.Run first = guard.enter("pull-request-digest");
        first.close();
        ModelJobGuard.Run second = guard.enter("review-comment-judgment");
        try {
            first.close();

            assertThrows(IllegalStateException.class, () -> guard.enter("feature-work-judgment"));
        } finally {
            second.close();
        }
    }

    @Test
    void runsTheWorkHoldingTheGuardAndReleasesItWhenTheWorkFails() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> guard.runGuarded("pull-request-digest", () -> {
                    assertThrows(IllegalStateException.class, () -> guard.enter("feature-work-judgment"));
                    throw new IllegalStateException("work failed");
                }));

        assertTrue(failure.getMessage().contains("work failed"));
        assertDoesNotThrow(() -> guard.enter("feature-work-judgment").close());
    }
}
