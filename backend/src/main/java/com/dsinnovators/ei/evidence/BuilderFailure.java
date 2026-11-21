package com.dsinnovators.ei.evidence;

/**
 * One item a builder could not build.
 *
 * @param itemKey the key the item has, or would have, in {@code evidence_item}; the job never
 *     withdraws an item whose key is named here, whatever the builder
 * @param reason what went wrong, with the value
 */
record BuilderFailure(String itemKey, String reason) {

    /** The text that names the failure in a run's error message. */
    String text() {
        return itemKey + ": " + reason;
    }
}
