package com.dsinnovators.ei.analytics;

/** The one rule for a share: count divided by total, not rounded, and null when the total is 0 (decision 186). */
final class Shares {

    private Shares() {}

    static Double of(long count, long total) {
        return total == 0 ? null : (double) count / total;
    }
}
