package com.dsinnovators.ei.review;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores the labels of a file the user filled in (F08 design, flow 4). All or nothing: when any row is
 * wrong nothing is stored and every wrong row is named. A label is given once, because the AI's answer
 * could be seen after it (decision 333).
 */
@Service
public class LabelImport {

    private static final String ALLOWED = "low, medium, high, very_high or cannot_judge";
    private static final String CANNOT_JUDGE = "cannot_judge";

    private final BlindCheckItemRepository sampleItems;
    private final HumanLabelRepository labels;
    private final Clock clock;

    LabelImport(BlindCheckItemRepository sampleItems, HumanLabelRepository labels, Clock clock) {
        this.sampleItems = sampleItems;
        this.labels = labels;
        this.clock = clock;
    }

    /**
     * Stores the labels of the file, or refuses the whole file.
     *
     * @param file the CSV text, with ',' or ';' as the separator
     * @throws LabelImportRefusedException when the header or any row is wrong; nothing is stored then
     */
    @Transactional
    public LabelImportResult importLabels(String file) {
        List<LabelFile.Row> rows = LabelFile.parse(file).rows().stream()
                .filter(row -> !row.complexity().isEmpty())
                .toList();
        Map<Long, BlindCheckItem> sampleByEvidenceItem = sampleItems.findAllByOrderByPositionAsc().stream()
                .collect(Collectors.toMap(BlindCheckItem::getEvidenceItemId, Function.identity()));
        Map<Long, HumanLabel> existing = labels
                .findByBlindCheckItemIdIn(sampleByEvidenceItem.values().stream()
                        .map(BlindCheckItem::getId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(HumanLabel::getBlindCheckItemId, Function.identity()));

        List<LabelProblem> problems = new ArrayList<>();
        List<HumanLabel> toStore = new ArrayList<>();
        int alreadyLabeled = 0;
        Set<Long> seen = new HashSet<>();
        Instant labeledAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        for (LabelFile.Row row : rows) {
            Optional<String> problem = problemOf(row, sampleByEvidenceItem, seen);
            if (problem.isPresent()) {
                problems.add(new LabelProblem(
                        row.line(), row.itemId().isEmpty() ? null : row.itemId(), row.complexity(), problem.get()));
                continue;
            }
            String choice = normalized(row.complexity());
            BlindCheckItem sampled = sampleByEvidenceItem.get(Long.parseLong(row.itemId()));
            HumanLabel earlier = existing.get(sampled.getId());
            if (earlier == null) {
                toStore.add(new HumanLabel(
                        sampled.getId(),
                        choice.equals(CANNOT_JUDGE) ? null : choice,
                        choice.equals(CANNOT_JUDGE),
                        row.note().isEmpty() ? null : row.note(),
                        labeledAt));
            } else if (nameOf(earlier).equals(choice)) {
                alreadyLabeled++;
            } else {
                problems.add(new LabelProblem(
                        row.line(),
                        row.itemId(),
                        row.complexity(),
                        "item " + row.itemId() + " is already labeled " + nameOf(earlier)
                                + "; a label is not changed after the AI's answer could be seen"));
            }
        }
        if (!problems.isEmpty()) {
            throw new LabelImportRefusedException(
                    problems.size() + " rows of the file are wrong; nothing was stored", problems);
        }
        toStore.forEach(labels::save);
        return new LabelImportResult(
                toStore.size(), alreadyLabeled, sampleByEvidenceItem.size() - existing.size() - toStore.size());
    }

    private static Optional<String> problemOf(
            LabelFile.Row row, Map<Long, BlindCheckItem> sampleByEvidenceItem, Set<Long> seen) {
        Optional<Long> evidenceItemId = wholeNumber(row.itemId());
        if (evidenceItemId.isEmpty()) {
            return Optional.of("item_id is not a whole number");
        }
        if (!sampleByEvidenceItem.containsKey(evidenceItemId.get())) {
            return Optional.of("item " + row.itemId() + " is not in the sample");
        }
        if (!seen.add(evidenceItemId.get())) {
            return Optional.of("item " + row.itemId() + " is in the file twice");
        }
        return isAllowed(normalized(row.complexity()))
                ? Optional.empty()
                : Optional.of("complexity must be " + ALLOWED);
    }

    private static Optional<Long> wholeNumber(String text) {
        try {
            return Optional.of(Long.parseLong(text));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    /** "Very high", " very_high " and "very-high" all name very_high. */
    private static String normalized(String complexity) {
        return complexity.strip().toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
    }

    private static boolean isAllowed(String choice) {
        return List.of("low", "medium", "high", "very_high", CANNOT_JUDGE).contains(choice);
    }

    private static String nameOf(HumanLabel label) {
        return label.isCannotJudge() ? CANNOT_JUDGE : label.getComplexity();
    }
}
