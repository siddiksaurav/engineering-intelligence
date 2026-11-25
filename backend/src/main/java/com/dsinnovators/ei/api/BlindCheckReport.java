package com.dsinnovators.ei.api;

import com.dsinnovators.ei.evidence.CurrentEvidenceItems;
import com.dsinnovators.ei.evidence.EvidenceItem;
import com.dsinnovators.ei.review.BlindCheckNumbers;
import com.dsinnovators.ei.review.BlindCheckResult;
import com.dsinnovators.ei.review.ItemOutcome;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** The numbers of the blind check with the title of every item they list; the titles come from the items, which {@code review} may not read. */
@Component
class BlindCheckReport {

    private final BlindCheckNumbers numbers;
    private final CurrentEvidenceItems evidenceItems;

    BlindCheckReport(BlindCheckNumbers numbers, CurrentEvidenceItems evidenceItems) {
        this.numbers = numbers;
        this.evidenceItems = evidenceItems;
    }

    BlindCheckResponse report() {
        BlindCheckResult result = numbers.compute();
        Map<Long, String> titles = titlesOf(result);
        return new BlindCheckResponse(
                result.summary(),
                result.reasonsToMark().stream()
                        .map(outcome ->
                                new BlindCheckResponse.ReasonToMark(outcome.itemId(), titles.get(outcome.itemId())))
                        .toList(),
                result.disagreements().stream()
                        .map(outcome -> disagreement(outcome, titles.get(outcome.itemId())))
                        .toList(),
                result.cannotJudgeItems().stream()
                        .map(outcome -> new BlindCheckResponse.CannotJudgeItem(
                                outcome.itemId(), titles.get(outcome.itemId()), outcome.labelNote()))
                        .toList());
    }

    private static BlindCheckResponse.Disagreement disagreement(ItemOutcome outcome, String title) {
        return new BlindCheckResponse.Disagreement(
                outcome.itemId(),
                title,
                outcome.developer(),
                outcome.labelComplexity(),
                outcome.aiComplexity(),
                outcome.complexityByTable(),
                outcome.labelNote(),
                outcome.aiReason(),
                outcome.reasonRight(),
                outcome.markNote());
    }

    private Map<Long, String> titlesOf(BlindCheckResult result) {
        List<Long> itemIds = Stream.of(result.reasonsToMark(), result.disagreements(), result.cannotJudgeItems())
                .flatMap(List::stream)
                .map(ItemOutcome::itemId)
                .distinct()
                .toList();
        return evidenceItems.byIds(itemIds).stream()
                .collect(Collectors.toMap(EvidenceItem::getId, ItemTitleAndLinks::titleOf));
    }
}
