package com.dsinnovators.ei.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class JobRunCountsTest {

    private final JobRunCounts counts = new JobRunCounts();

    @Test
    void amountsAddUpPerName() {
        counts.add("pull_request.created", 2);
        counts.add("pull_request.created", 3);
        counts.add("review.created", 1);

        assertEquals(Map.of("pull_request.created", 5L, "review.created", 1L), counts.snapshot());
    }

    @Test
    void theSnapshotIsSortedByName() {
        counts.add("b", 1);
        counts.add("a", 1);

        assertEquals(List.of("a", "b"), List.copyOf(counts.snapshot().keySet()));
    }

    @Test
    void addsFromManyThreadsAreAllCounted() {
        IntStream.range(0, 10_000).parallel().forEach(index -> counts.add("listed", 1));

        assertEquals(Map.of("listed", 10_000L), counts.snapshot());
    }

    @Test
    void aNegativeAmountIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> counts.add("listed", -1));
    }

    @Test
    void aBlankNameIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> counts.add("", 1));
    }
}
