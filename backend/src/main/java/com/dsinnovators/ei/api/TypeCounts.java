package com.dsinnovators.ei.api;

import com.dsinnovators.ei.evidence.EvidenceType;
import java.util.Map;

/** How many current evidence items a developer has of each type. */
record TypeCounts(long featureWork, long bugFix, long reviewCommentWritten) {

    static TypeCounts of(Map<EvidenceType, Long> counts) {
        return new TypeCounts(
                counts.getOrDefault(EvidenceType.FEATURE_WORK, 0L),
                counts.getOrDefault(EvidenceType.BUG_FIX, 0L),
                counts.getOrDefault(EvidenceType.REVIEW_COMMENT_WRITTEN, 0L));
    }
}
