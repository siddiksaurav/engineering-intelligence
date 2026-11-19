package com.dsinnovators.ei.source;

import java.util.Locale;

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
        NEW_VERSION;

        /** The name of the job count for a record of this kind stored with this outcome. */
        public String countName(String recordKind) {
            return recordKind + "." + name().toLowerCase(Locale.ROOT);
        }
    }
}
