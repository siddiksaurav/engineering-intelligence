package com.dsinnovators.ei.evidence;

import com.dsinnovators.ei.judgment.ReviewCommentItems;
import com.dsinnovators.ei.judgment.ReviewCommentToJudge;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Hands the current Review comment written items to {@code judgment}, read from their fields and sources. */
@Service
class ReviewCommentItemsForJudgment implements ReviewCommentItems {

    private final EvidenceItemRepository items;

    ReviewCommentItemsForJudgment(EvidenceItemRepository items) {
        this.items = items;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewCommentToJudge> current() {
        return items.findAllCurrent().stream()
                .filter(item -> item.getEvidenceType() == EvidenceType.REVIEW_COMMENT_WRITTEN)
                .map(ReviewCommentItemsForJudgment::toJudge)
                .toList();
    }

    private static ReviewCommentToJudge toJudge(EvidenceItem item) {
        return new ReviewCommentToJudge(
                item.getId(),
                item.getItemKey(),
                sourceOfRole(item, SourceRole.REVIEW_COMMENT),
                sourceOfRole(item, SourceRole.PULL_REQUEST),
                item.getFields().path("pullRequest").path("externalId").asString());
    }

    private static long sourceOfRole(EvidenceItem item, SourceRole role) {
        return item.getSources().stream()
                .filter(source -> source.role() == role)
                .map(EvidenceItemSource::rawRecordId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "The item " + item.getItemKey() + " has no source record of the role " + role));
    }
}
