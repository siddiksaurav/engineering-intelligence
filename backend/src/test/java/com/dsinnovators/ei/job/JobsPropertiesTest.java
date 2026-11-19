package com.dsinnovators.ei.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JobsPropertiesTest {

    @Test
    void aNegativeFlushIntervalIsRefusedAndShown() {
        IllegalArgumentException refused =
                assertThrows(IllegalArgumentException.class, () -> new JobsProperties(Duration.ofSeconds(-1)));

        assertEquals("ei.jobs.count-flush-interval must be zero or more, was PT-1S", refused.getMessage());
    }

    @Test
    void aMissingFlushIntervalIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new JobsProperties(null));
    }

    @Test
    void zeroIsAllowed() {
        assertEquals(Duration.ZERO, new JobsProperties(Duration.ZERO).countFlushInterval());
    }
}
