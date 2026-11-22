package com.dsinnovators.ei.scoring;

import com.dsinnovators.ei.evidence.EvidenceType;

/**
 * An evidence type a dimension reads.
 *
 * @param tiered false when the items are counted on the trail and have no tier (Bug fix)
 * @param fromLevel the lowest level at which the type is read
 */
public record MappedEvidenceType(EvidenceType evidenceType, boolean tiered, Level fromLevel) {

    /** True when the type is read for a developer selected at {@code level}. */
    public boolean isReadAt(Level level) {
        return level.isAtLeast(fromLevel);
    }
}
