package com.dsinnovators.ei.analytics;

import com.dsinnovators.ei.common.Quarter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * How hard a person's work was: their current Feature work items dated in the quarter, counted by
 * the complexity of the current {@code feature_work_complexity} judgment, next to the same count for
 * the Feature work items of every other person, so the person is compared with the rest of the
 * repository and not with a total that holds them (decisions 194, 195, 284).
 * An item without a judgment is counted as not judged yet (decision 198). Complexity gives the tier,
 * so the split by complexity is the split by tier.
 */
@Component
public class WorkComplexity {

    private static final List<String> BARS = List.of("low", "medium", "high", "very_high", Distribution.NOT_JUDGED_YET);

    private static final String QUERY_WITH_OWNER =
            AnalyticsSql.with(List.of(AnalyticsSql.CURRENT_ITEMS_AND_JUDGMENTS), """
            select (%s) as own,
                   coalesce(judgment.value ->> 'complexity', 'not_judged_yet') as complexity,
                   count(distinct item.id) as count,
                   array_agg(distinct item.id) as item_ids,
                   array_agg(distinct pull_request.number) filter (where pull_request.number is not null)
                       as pull_request_numbers
            from current_item item
            left join current_judgment judgment
                   on judgment.evidence_item_id = item.id and judgment.kind = 'feature_work_complexity'
            left join (select source.evidence_item_id, (record.payload ->> 'number')::int as number
                       from evidence_item_source source
                       join raw_record record on record.id = source.raw_record_id
                       where source.role = 'pull_request') pull_request
                   on pull_request.evidence_item_id = item.id
            where item.evidence_type = 'feature_work'
              and item.occurred_on between :firstDay and :lastDay
            group by own, complexity
            """);

    private static final String QUERY_OF_PERSON = QUERY_WITH_OWNER.formatted("item.person_id = :personId");

    // Every item is the team's, so that its numbers equal the sum of the persons' (there is no item without a person).
    private static final String QUERY_OF_TEAM = QUERY_WITH_OWNER.formatted("true");

    private final AnalyticsQuery analyticsQuery;

    WorkComplexity(AnalyticsQuery analyticsQuery) {
        this.analyticsQuery = analyticsQuery;
    }

    /**
     * The two sets of bars of Work complexity.
     *
     * @param person the person's Feature work items
     * @param restOfRepository the Feature work items of every other person
     * @param coverage the person's Feature work items that have a complexity, of all of them
     */
    public record Result(Distribution person, Distribution restOfRepository, Coverage coverage) {}

    /**
     * The bars of Work complexity of every person together.
     *
     * @param distribution the Feature work items of the quarter by complexity
     * @param coverage the Feature work items that have a complexity, of all of them
     */
    public record TeamResult(Distribution distribution, Coverage coverage) {}

    @Transactional(readOnly = true)
    public Result of(long personId, Quarter quarter) {
        List<Row> rows = analyticsQuery
                .statement(QUERY_OF_PERSON, quarter)
                .param("personId", personId)
                .query(WorkComplexity::rowOf)
                .list();
        Distribution personBars = barsOf(rows, true);
        return new Result(personBars, barsOf(rows, false), coverageOf(personBars));
    }

    @Transactional(readOnly = true)
    public TeamResult ofTeam(Quarter quarter) {
        List<Row> rows = analyticsQuery
                .statement(QUERY_OF_TEAM, quarter)
                .query(WorkComplexity::rowOf)
                .list();
        Distribution bars = barsOf(rows, true);
        return new TeamResult(bars, coverageOf(bars));
    }

    private static Distribution barsOf(List<Row> rows, boolean own) {
        Map<String, Count> bars = new LinkedHashMap<>();
        BARS.forEach(bar -> bars.put(bar, Count.none()));
        rows.stream()
                .filter(row -> row.own() == own)
                .forEach(row -> bars.merge(row.complexity(), row.count(), Count::plus));
        return Distribution.of(bars);
    }

    private static Coverage coverageOf(Distribution bars) {
        long notJudgedYet = bars.slices().stream()
                .filter(slice -> slice.name().equals(Distribution.NOT_JUDGED_YET))
                .mapToLong(slice -> slice.count().count())
                .sum();
        return new Coverage(bars.total() - notJudgedYet, bars.total());
    }

    private record Row(boolean own, String complexity, Count count) {}

    private static Row rowOf(ResultSet row, int rowNumber) throws SQLException {
        return new Row(row.getBoolean("own"), row.getString("complexity"), AnalyticsQuery.countOf(row));
    }
}
