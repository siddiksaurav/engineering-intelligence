package com.dsinnovators.ei.review;

import com.dsinnovators.ei.judgment.ItemJudgment;
import com.dsinnovators.ei.judgment.JudgmentsById;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * Stores the user's verdict on the AI's reason (F08 design, flow 6): per item, and per problem the AI
 * listed. A mark is made after the label; a second mark of the item supersedes the first.
 */
@Service
public class ReasonMarks {

    private static final Set<String> VERDICTS = Set.of(
            "right", "should_be_routine", "should_be_answer_exists", "should_be_no_ready_answer", "not_a_problem");

    private final BlindCheckItemRepository sampleItems;
    private final HumanLabelRepository labels;
    private final ReasonMarkRepository marks;
    private final JudgmentsById judgments;
    private final Clock clock;

    ReasonMarks(
            BlindCheckItemRepository sampleItems,
            HumanLabelRepository labels,
            ReasonMarkRepository marks,
            JudgmentsById judgments,
            Clock clock) {
        this.sampleItems = sampleItems;
        this.labels = labels;
        this.marks = marks;
        this.judgments = judgments;
        this.clock = clock;
    }

    /**
     * Stores the mark and answers with it.
     *
     * @param problemMarks one entry per problem of the judgment the sample fixed, in its order
     * @param note the user's own words; blank is stored as none
     * @throws ItemNotInSampleException when the item is not in the sample
     * @throws ReasonMarkNotAllowedException when the item has no label, or the label is {@code cannot_judge}
     * @throws InvalidProblemMarksException when the problem marks do not match the judgment
     */
    @Transactional
    public ItemBlindCheck.Mark mark(
            long evidenceItemId, boolean reasonRight, List<ProblemMark> problemMarks, String note) {
        BlindCheckItem sampled = sampleItems
                .findByEvidenceItemId(evidenceItemId)
                .orElseThrow(() -> new ItemNotInSampleException(evidenceItemId));
        requireLabelWithComplexity(evidenceItemId, sampled);
        ItemJudgment pinned = judgments
                .find(sampled.getJudgmentId())
                .orElseThrow(() -> new IllegalStateException("The judgment " + sampled.getJudgmentId()
                        + " fixed for evidence item " + evidenceItemId + " does not exist"));
        requireMatching(problemMarks, pinned.value().path("problems"));
        Long supersedes =
                marks.findCurrentOfItem(sampled.getId()).map(ReasonMark::getId).orElse(null);
        ReasonMark stored = marks.save(new ReasonMark(
                sampled.getId(),
                reasonRight,
                asJson(problemMarks),
                note == null || note.isBlank() ? null : note.strip(),
                clock.instant().truncatedTo(ChronoUnit.MICROS),
                supersedes));
        return new ItemBlindCheck.Mark(
                stored.isReasonRight(), stored.getProblemMarks(), stored.getNote(), stored.getMarkedAt());
    }

    private void requireLabelWithComplexity(long evidenceItemId, BlindCheckItem sampled) {
        Optional<HumanLabel> label = labels.findByBlindCheckItemIdIn(List.of(sampled.getId())).stream()
                .findFirst();
        if (label.isEmpty()) {
            throw new ReasonMarkNotAllowedException(
                    "Evidence item " + evidenceItemId + " has no label yet; label it before marking the AI's reason");
        }
        if (label.get().isCannotJudge()) {
            throw new ReasonMarkNotAllowedException("Evidence item " + evidenceItemId
                    + " was labeled cannot_judge; the AI's reason is not marked for it");
        }
    }

    private static void requireMatching(List<ProblemMark> problemMarks, JsonNode problems) {
        if (problemMarks.size() != problems.size()) {
            throw new InvalidProblemMarksException("The judgment lists " + problems.size()
                    + " problems, so problemMarks needs " + problems.size() + " entries, got " + problemMarks.size());
        }
        for (int index = 0; index < problemMarks.size(); index++) {
            String expectedName = problems.get(index).path("name").asString();
            ProblemMark mark = problemMarks.get(index);
            if (!expectedName.equals(mark.name())) {
                throw new InvalidProblemMarksException("Problem " + (index + 1) + " of the judgment is named '"
                        + expectedName + "', got '" + mark.name() + "'");
            }
            if (mark.verdict() == null || !VERDICTS.contains(mark.verdict())) {
                throw new InvalidProblemMarksException("The verdict '" + mark.verdict() + "' of problem '" + mark.name()
                        + "' must be one of " + VERDICTS.stream().sorted().toList());
            }
        }
    }

    private static ArrayNode asJson(List<ProblemMark> problemMarks) {
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        problemMarks.forEach(mark -> array.addObject().put("name", mark.name()).put("verdict", mark.verdict()));
        return array;
    }
}
