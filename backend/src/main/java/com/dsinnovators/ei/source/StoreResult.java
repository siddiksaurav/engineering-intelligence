package com.dsinnovators.ei.source;

/**
 * What {@link RawRecordStore#store} did. {@code record} is the row that was written, or the
 * unchanged latest row.
 */
public record StoreResult(Outcome outcome, RawRecord record) {

    public enum Outcome {
        /** No copy existed. Version 1 was written. */
        CREATED,
        /** The latest version has the same content. Nothing was written. */
        UNCHANGED,
        /** The latest version has other content. The next version was written. */
        NEW_VERSION
    }
}
