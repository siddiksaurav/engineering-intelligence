package com.dsinnovators.ei.analytics;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.scoring.PresenceThreshold;
import com.dsinnovators.ei.scoring.ScoringSettings;
import com.dsinnovators.ei.scoring.ScoringSettingsReader;
import com.dsinnovators.ei.scoring.Tier;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * How high the person's Feature work sits: the weighted share of their judged Feature work items of
 * the quarter at T2 or above and at T3, next to the same shares for every other person, and the
 * presence threshold as the line to read them against (decisions 280, 285). The weights come from
 * {@code scoring.feature_work_weights}, the line from {@code scoring.presence_threshold}, and the tier
 * is the one the current complexity judgment stores. Items without a judgment are left out, as
 * scoring leaves them out, so the person's numbers equal the Feature work presence on the Coding and
 * code quality trail.
 */
@Component
public class WorkLevel {

    private static final String QUERY = AnalyticsSql.with(List.of(AnalyticsSql.CURRENT_ITEMS_AND_JUDGMENTS), """
            select (item.person_id = :personId) as own,
                   judgment.tier as tier,
                   count(*) as count
            from current_item item
            left join current_judgment judgment
                   on judgment.evidence_item_id = item.id and judgment.kind = 'feature_work_complexity'
            where item.evidence_type = 'feature_work'
              and item.occurred_on between :firstDay and :lastDay
            group by own, tier
            """);

    private final AnalyticsQuery analyticsQuery;
    private final ScoringSettingsReader settingsReader;

    WorkLevel(AnalyticsQuery analyticsQuery, ScoringSettingsReader settingsReader) {
        this.analyticsQuery = analyticsQuery;
        this.settingsReader = settingsReader;
    }

    /**
     * The weighted totals and shares of one group of Feature work items.
     *
     * @param weightedTotal the weight of all judged items
     * @param weightedAtT2OrAbove the weight of the judged items at tier T2 or T3
     * @param shareAtT2OrAbove the share of the total at T2 or above; null when the total is 0
     * @param weightedAtT3 the weight of the judged items at tier T3
     * @param shareAtT3 the share of the total at T3; null when the total is 0
     */
    public record Weighted(
            long weightedTotal,
            long weightedAtT2OrAbove,
            Double shareAtT2OrAbove,
            long weightedAtT3,
            Double shareAtT3) {}

    /**
     * The Work level analytic.
     *
     * @param person the person's Feature work items
     * @param restOfRepository the Feature work items of every other person
     * @param line the presence threshold as a share, the line a share is read against
     * @param coverage the person's Feature work items that have a judgment, of all of them
     */
    public record Result(Weighted person, Weighted restOfRepository, double line, Coverage coverage) {}

    @Transactional(readOnly = true)
    public Result of(long personId, Quarter quarter) {
        ScoringSettings settings = settingsReader.in(quarter);
        List<Row> rows = analyticsQuery
                .statement(QUERY, quarter)
                .param("personId", personId)
                .query((row, rowNumber) ->
                        new Row(row.getBoolean("own"), tierOf(row.getString("tier")), row.getLong("count")))
                .list();
        PresenceThreshold threshold = settings.presenceThreshold();
        return new Result(
                weighted(rows, true, settings.featureWorkWeights()),
                weighted(rows, false, settings.featureWorkWeights()),
                (double) threshold.numerator() / threshold.denominator(),
                coverageOf(rows));
    }

    /** A null tier is an item without a judgment. */
    private record Row(boolean own, Tier tier, long count) {}

    private static Tier tierOf(String text) {
        return text == null ? null : Tier.fromDatabase(text);
    }

    private static Coverage coverageOf(List<Row> rows) {
        long total = rows.stream().filter(Row::own).mapToLong(Row::count).sum();
        long judged = rows.stream()
                .filter(row -> row.own() && row.tier() != null)
                .mapToLong(Row::count)
                .sum();
        return new Coverage(judged, total);
    }

    private static Weighted weighted(List<Row> rows, boolean own, Map<Tier, Integer> weights) {
        long total = weightOf(rows, own, Tier.T1, weights);
        long atT2OrAbove = weightOf(rows, own, Tier.T2, weights);
        long atT3 = weightOf(rows, own, Tier.T3, weights);
        return new Weighted(total, atT2OrAbove, Shares.of(atT2OrAbove, total), atT3, Shares.of(atT3, total));
    }

    /** The weight of the group's items at {@code lowest} or a higher tier. */
    private static long weightOf(List<Row> rows, boolean own, Tier lowest, Map<Tier, Integer> weights) {
        return rows.stream()
                .filter(row ->
                        row.own() == own && row.tier() != null && row.tier().isAtLeast(lowest))
                .mapToLong(row -> row.count() * weights.get(row.tier()))
                .sum();
    }
}
