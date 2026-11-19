package com.dsinnovators.ei.job;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** The counts of one run, kept in memory. */
final class JobRunCounts implements JobCounts {

    private final Map<String, LongAdder> counts = new ConcurrentHashMap<>();

    @Override
    public void add(String name, long amount) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A count needs a name");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("The count '" + name + "' cannot go down by " + amount);
        }
        counts.computeIfAbsent(name, key -> new LongAdder()).add(amount);
    }

    /** The counts at this moment, sorted by name. */
    Map<String, Long> snapshot() {
        Map<String, Long> snapshot = new TreeMap<>();
        counts.forEach((name, count) -> snapshot.put(name, count.sum()));
        return snapshot;
    }
}
