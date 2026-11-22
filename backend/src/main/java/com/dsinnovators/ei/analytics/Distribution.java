package com.dsinnovators.ei.analytics;

import java.util.List;
import java.util.Map;

/**
 * A total split into buckets. The shares of the buckets add up to 1 because every counted row is in
 * exactly one bucket, the bucket {@value #NOT_JUDGED_YET} included (decision 198).
 *
 * @param total the rows counted
 * @param slices the buckets in the order given
 */
public record Distribution(long total, List<Slice> slices) {

    /** The bucket of a row whose judgment does not exist yet. */
    public static final String NOT_JUDGED_YET = "not_judged_yet";

    public Distribution {
        slices = List.copyOf(slices);
    }

    /** The distribution of the counts, in the iteration order of the map. */
    static Distribution of(Map<String, Count> countsByBucket) {
        long total = countsByBucket.values().stream().mapToLong(Count::count).sum();
        List<Slice> slices = countsByBucket.entrySet().stream()
                .map(entry -> new Slice(
                        entry.getKey(),
                        entry.getValue(),
                        Shares.of(entry.getValue().count(), total)))
                .toList();
        return new Distribution(total, slices);
    }
}
