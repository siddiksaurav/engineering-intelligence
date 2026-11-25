package com.dsinnovators.ei.review;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads the label and the reason mark of a sample item, so that the rest of the system never sees their rows. */
@Service
public class BlindCheckReader {

    private final BlindCheckItemRepository sampleItems;
    private final HumanLabelRepository labels;
    private final ReasonMarkRepository marks;

    BlindCheckReader(BlindCheckItemRepository sampleItems, HumanLabelRepository labels, ReasonMarkRepository marks) {
        this.sampleItems = sampleItems;
        this.labels = labels;
        this.marks = marks;
    }

    /** The blind check state of the evidence item row, or nothing when the item is not in the sample. */
    @Transactional(readOnly = true)
    public Optional<ItemBlindCheck> ofItem(long evidenceItemId) {
        return sampleItems.findByEvidenceItemId(evidenceItemId).map(sampled -> {
            ItemBlindCheck.Label label = labels.findByBlindCheckItemIdIn(List.of(sampled.getId())).stream()
                    .findFirst()
                    .map(found -> new ItemBlindCheck.Label(
                            found.getComplexity(), found.isCannotJudge(), found.getNote(), found.getLabeledAt()))
                    .orElse(null);
            ItemBlindCheck.Mark mark = marks.findCurrentOfItem(sampled.getId())
                    .map(found -> new ItemBlindCheck.Mark(
                            found.isReasonRight(), found.getProblemMarks(), found.getNote(), found.getMarkedAt()))
                    .orElse(null);
            return new ItemBlindCheck(label, mark);
        });
    }
}
