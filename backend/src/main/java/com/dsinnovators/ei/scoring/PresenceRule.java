package com.dsinnovators.ei.scoring;

/** How presence at a tier is decided for an evidence type (spec 9.3). */
public enum PresenceRule {
    /** A frequent type: the share at the tier or higher reaches the threshold. */
    SHARE,
    /** A rare type: one item at the tier or higher is enough. */
    ONE_ITEM
}
