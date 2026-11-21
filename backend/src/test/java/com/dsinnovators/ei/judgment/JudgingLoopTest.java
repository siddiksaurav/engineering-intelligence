package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JudgingLoopTest {

    private final Map<String, Long> counts = new HashMap<>();
    private final List<String> judged = new ArrayList<>();
    private final JudgingLoop<String> loop = new JudgingLoop<>(
            "items_left_for_later", "items", subject -> subject.length(), subject -> "<" + subject + ">");

    private void run(List<String> subjects, List<String> limitReachedAt, List<String> failingAt, List<String> failed) {
        failed.addAll(loop.run(
                subjects,
                subject -> {
                    if (limitReachedAt.contains(subject)) {
                        throw new ModelLimitReachedException("every provider is at its limit");
                    }
                    if (failingAt.contains(subject)) {
                        throw new IllegalStateException("cannot judge " + subject);
                    }
                    judged.add(subject);
                },
                (name, amount) -> counts.merge(name, amount, Long::sum)));
    }

    @Test
    void judgesEverySubjectInOrder() {
        List<String> failed = new ArrayList<>();

        run(List.of("a", "bb"), List.of(), List.of(), failed);

        assertEquals(List.of("a", "bb"), judged);
        assertEquals(List.of(), failed);
        assertNull(counts.get("items_left_for_later"));
    }

    @Test
    void namesAFailedSubjectAndGoesOn() {
        List<String> failed = new ArrayList<>();

        run(List.of("a", "bb", "ccc"), List.of(), List.of("bb"), failed);

        assertEquals(List.of("a", "ccc"), judged);
        assertEquals(List.of("<bb>"), failed);
    }

    @Test
    void stopsAtTheLimitAndCountsTheSizeOfEverySubjectLeft() {
        List<String> failed = new ArrayList<>();

        run(List.of("a", "bb", "ccc", "dddd"), List.of("ccc"), List.of(), failed);

        assertEquals(List.of("a", "bb"), judged);
        assertEquals(7L, counts.get("items_left_for_later"));
        assertEquals(List.of(), failed);
    }
}
