package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WaitMeterTest {

    private final List<Duration> slept = new ArrayList<>();
    private final WaitMeter meter = new WaitMeter(slept::add);

    @Test
    void addsUpTheWaitsAndPassesThemOn() throws InterruptedException {
        meter.sleep(Duration.ofSeconds(2));
        meter.sleep(Duration.ofMillis(500));

        assertEquals(Duration.ofMillis(2500), meter.waited());
        assertEquals(List.of(Duration.ofSeconds(2), Duration.ofMillis(500)), slept);
    }

    @Test
    void forgetsTheWaitsOnReset() throws InterruptedException {
        meter.sleep(Duration.ofSeconds(2));

        meter.reset();

        assertEquals(Duration.ZERO, meter.waited());
    }
}
