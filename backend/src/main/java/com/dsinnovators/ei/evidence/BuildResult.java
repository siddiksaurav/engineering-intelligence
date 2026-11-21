package com.dsinnovators.ei.evidence;

import java.util.List;

/**
 * What a builder made. An item that cannot be built, such as one whose human author has no person
 * yet, is named in {@code failures} and the other items are still returned, so the job can write
 * them and fail at its end naming the failed ones.
 *
 * @param failures one per failed item
 * @param botRecordsSkipped how many records of bots the builder left out
 * @param recordsWithoutAuthor how many records GitHub gives no author, which give no item (decision 126)
 */
record BuildResult(
        List<BuiltItem> items, List<BuilderFailure> failures, int botRecordsSkipped, int recordsWithoutAuthor) {

    BuildResult {
        items = List.copyOf(items);
        failures = List.copyOf(failures);
    }

    BuildResult(List<BuiltItem> items, List<BuilderFailure> failures) {
        this(items, failures, 0, 0);
    }
}
