package com.dsinnovators.ei.evidence;

import java.util.List;

/**
 * What a builder made. An item that cannot be built, such as one whose human author has no person
 * yet, is named in {@code failures} and the other items are still returned, so the job can write
 * them and fail at its end naming the failed ones.
 *
 * @param failures one text per failed item: its key, then the reason
 */
record BuildResult(List<BuiltItem> items, List<String> failures) {

    BuildResult {
        items = List.copyOf(items);
        failures = List.copyOf(failures);
    }
}
