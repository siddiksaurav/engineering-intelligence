package com.dsinnovators.ei.review;

import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.setting.SettingService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * The numbers of the blind check (F08 design, flow 7). Computed on every call from the sample, the
 * labels, the marks and the judgments the sample fixed; nothing is stored (decision 337).
 */
@Component
public class BlindCheckNumbers {

    static final String TARGETS_SETTING = "blind_check.targets";

    /** From the lowest to the highest; a step is one place in this list (decision 335). */
    private static final List<String> COMPLEXITIES = List.of("low", "medium", "high", "very_high");

    // One row per sample item. Labels are given once, so a label is current; a mark is current when no mark supersedes
    // it.
    private static final String OUTCOMES_SQL = """
            select b.evidence_item_id, p.display_name,
                   l.id is not null as labeled, l.complexity as label_complexity,
                   coalesce(l.cannot_judge, false) as cannot_judge, l.note as label_note,
                   j.value ->> 'complexity' as ai_complexity, j.value ->> 'complexityByTable' as complexity_by_table,
                   j.reason as ai_reason, coalesce((j.value ->> 'thinEvidence')::boolean, false) as thin_evidence,
                   m.reason_right, m.problem_marks::text as problem_marks, m.note as mark_note
            from blind_check_item b
            join evidence_item e on e.id = b.evidence_item_id
            join person p on p.id = e.person_id
            join judgment j on j.id = b.judgment_id
            left join human_label l on l.blind_check_item_id = b.id
            left join reason_mark m on m.blind_check_item_id = b.id
                 and not exists (select 1 from reason_mark newer where newer.supersedes_id = m.id)
            order by b.position
            """;

    private final JdbcClient jdbc;
    private final SettingService settings;
    private final Clock clock;

    BlindCheckNumbers(JdbcClient jdbc, SettingService settings, Clock clock) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.clock = clock;
    }

    /**
     * Counts the sample against the labels and the marks as they are now.
     *
     * @throws com.dsinnovators.ei.setting.SettingNotFoundException when the targets setting has no value in force
     */
    @Transactional(readOnly = true)
    public BlindCheckResult compute() {
        Targets targets = targets(settings.getJson(TARGETS_SETTING, Quarter.of(LocalDate.now(clock))));
        List<ItemOutcome> outcomes =
                jdbc.sql(OUTCOMES_SQL).query((row, rowNumber) -> outcomeOf(row)).list();
        List<ItemOutcome> comparable = filter(outcomes, outcome -> outcome.labelComplexity() != null);
        List<ItemOutcome> marked = filter(outcomes, outcome -> outcome.reasonRight() != null);
        BlindCheckSummary summary = new BlindCheckSummary(
                outcomes.size(),
                filter(outcomes, ItemOutcome::labeled).size(),
                filter(outcomes, ItemOutcome::cannotJudge).size(),
                marked.size(),
                exactMatch(comparable, ItemOutcome::aiComplexity),
                withinOneStep(comparable),
                exactMatch(comparable, ItemOutcome::complexityByTable),
                reasonRight(marked),
                confusion(comparable),
                problemVerdicts(marked),
                thinEvidence(outcomes),
                targets);
        return new BlindCheckResult(
                summary,
                filter(comparable, outcome -> outcome.reasonRight() == null),
                filter(outcomes, BlindCheckNumbers::disagrees),
                filter(outcomes, ItemOutcome::cannotJudge));
    }

    private static boolean disagrees(ItemOutcome outcome) {
        boolean complexityDiffers =
                outcome.labelComplexity() != null && !outcome.labelComplexity().equals(outcome.aiComplexity());
        return complexityDiffers || Boolean.FALSE.equals(outcome.reasonRight());
    }

    private static List<ItemOutcome> filter(List<ItemOutcome> outcomes, Predicate<ItemOutcome> condition) {
        return outcomes.stream().filter(condition).toList();
    }

    private static Rate exactMatch(List<ItemOutcome> comparable, Function<ItemOutcome, String> ai) {
        return Rate.of(
                filter(comparable, outcome -> outcome.labelComplexity().equals(ai.apply(outcome)))
                        .size(),
                comparable.size());
    }

    private static Rate withinOneStep(List<ItemOutcome> comparable) {
        return Rate.of(
                filter(
                                comparable,
                                outcome ->
                                        Math.abs(rank(outcome.labelComplexity()) - rank(outcome.aiComplexity())) <= 1)
                        .size(),
                comparable.size());
    }

    private static int rank(String complexity) {
        int rank = COMPLEXITIES.indexOf(complexity);
        if (rank < 0) {
            throw new IllegalStateException("Unknown complexity '" + complexity + "' in the blind check");
        }
        return rank;
    }

    private static Rate reasonRight(List<ItemOutcome> marked) {
        return Rate.of(filter(marked, ItemOutcome::reasonRight).size(), marked.size());
    }

    private static List<ConfusionCell> confusion(List<ItemOutcome> comparable) {
        Map<List<String>, Long> counts = comparable.stream()
                .collect(Collectors.groupingBy(
                        outcome -> List.of(outcome.labelComplexity(), outcome.aiComplexity()), Collectors.counting()));
        return counts.entrySet().stream()
                .map(entry -> new ConfusionCell(
                        entry.getKey().get(0),
                        entry.getKey().get(1),
                        entry.getValue().intValue()))
                .sorted(Comparator.comparingInt((ConfusionCell cell) -> rank(cell.label()))
                        .thenComparingInt(cell -> rank(cell.ai())))
                .toList();
    }

    private static Map<String, Integer> problemVerdicts(List<ItemOutcome> marked) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        ProblemMark.VERDICTS.forEach(verdict -> counts.put(verdict, 0));
        marked.forEach(outcome -> outcome.problemMarks()
                .forEach(problem -> counts.merge(problem.path("verdict").asString(), 1, Integer::sum)));
        return counts;
    }

    private static ThinEvidence thinEvidence(List<ItemOutcome> outcomes) {
        List<ItemOutcome> thin = filter(outcomes, ItemOutcome::thinEvidence);
        return new ThinEvidence(
                thin.size(),
                exactMatch(filter(thin, outcome -> outcome.labelComplexity() != null), ItemOutcome::aiComplexity),
                reasonRight(filter(thin, outcome -> outcome.reasonRight() != null)));
    }

    private static Targets targets(JsonNode setting) {
        return new Targets(
                setting.required("exactMatch").doubleValue(),
                setting.required("withinOneStep").doubleValue(),
                setting.required("reasonRight").doubleValue());
    }

    private static ItemOutcome outcomeOf(ResultSet row) throws SQLException {
        String problemMarks = row.getString("problem_marks");
        boolean hasMark = problemMarks != null;
        return new ItemOutcome(
                row.getLong("evidence_item_id"),
                row.getString("display_name"),
                row.getBoolean("labeled"),
                row.getString("label_complexity"),
                row.getBoolean("cannot_judge"),
                row.getString("label_note"),
                row.getString("ai_complexity"),
                row.getString("complexity_by_table"),
                row.getString("ai_reason"),
                row.getBoolean("thin_evidence"),
                hasMark ? row.getBoolean("reason_right") : null,
                hasMark ? CanonicalJson.parse(problemMarks) : null,
                row.getString("mark_note"));
    }
}
