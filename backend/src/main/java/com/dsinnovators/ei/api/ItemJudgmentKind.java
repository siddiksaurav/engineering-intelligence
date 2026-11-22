package com.dsinnovators.ei.api;

import com.dsinnovators.ei.evidence.EvidenceType;
import com.dsinnovators.ei.judgment.JudgmentKind;
import java.util.Optional;

/** Which judgment an evidence item of a type shows (decision 241). */
final class ItemJudgmentKind {

    private ItemJudgmentKind() {}

    /** The kind of judgment that belongs to the type; empty for the types that are counted, not tiered. */
    static Optional<JudgmentKind> of(EvidenceType type) {
        return switch (type) {
            case FEATURE_WORK -> Optional.of(JudgmentKind.FEATURE_WORK_COMPLEXITY);
            case REVIEW_COMMENT_WRITTEN -> Optional.of(JudgmentKind.REVIEW_COMMENT);
            case TEST_CHANGE, REFACTOR_PERFORMANCE, SECURITY, INFRA_RELEASE -> Optional.of(JudgmentKind.ITEM_TIER);
            case BUG_FIX, DOCUMENTATION -> Optional.empty();
        };
    }
}
