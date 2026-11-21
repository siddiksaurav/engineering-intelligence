package com.dsinnovators.ei.api;

import com.dsinnovators.ei.evidence.EvidenceType;
import java.util.Map;

/** How many current evidence items a developer has of each type. */
record TypeCounts(
        long featureWork,
        long bugFix,
        long reviewCommentWritten,
        long testChange,
        long refactorPerformance,
        long security,
        long infraRelease,
        long documentation) {

    static TypeCounts of(Map<EvidenceType, Long> counts) {
        return new TypeCounts(
                countOf(counts, EvidenceType.FEATURE_WORK),
                countOf(counts, EvidenceType.BUG_FIX),
                countOf(counts, EvidenceType.REVIEW_COMMENT_WRITTEN),
                countOf(counts, EvidenceType.TEST_CHANGE),
                countOf(counts, EvidenceType.REFACTOR_PERFORMANCE),
                countOf(counts, EvidenceType.SECURITY),
                countOf(counts, EvidenceType.INFRA_RELEASE),
                countOf(counts, EvidenceType.DOCUMENTATION));
    }

    private static long countOf(Map<EvidenceType, Long> counts, EvidenceType type) {
        return counts.getOrDefault(type, 0L);
    }
}
