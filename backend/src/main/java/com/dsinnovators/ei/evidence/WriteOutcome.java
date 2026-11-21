package com.dsinnovators.ei.evidence;

/** What {@link EvidenceItemStore#write(BuiltItem)} did with a built item. */
public enum WriteOutcome {
    /** The key had no current item; a first row was written. */
    CREATED,
    /** The current item has the same content; nothing was written. */
    UNCHANGED,
    /** The content differs; a new row replaces the current one. */
    SUPERSEDED
}
