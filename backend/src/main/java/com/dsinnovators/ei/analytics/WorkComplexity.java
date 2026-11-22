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
 * every current Feature work item of the repository, the person's included (decisions 194, 195).
 * An item without a judgment is counted as not judged yet (decision 198). Complexity gives the tier,
 * so the split by complexity is the split by tier.
 */
@Component
public class WorkComplexity {

    private static final List<String> BARS = List.of("low", "medium", "high", "very_high", Distribution.NOT_JUDGED_YET);

    private static final String QUERY = AnalyticsSql.with(List.of(AnalyticsSql.CURRENT_ITEMS_AND_JUDGMENTS), """
            select (item.person_id = :personId) as own,
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

    private final AnalyticsQuery analyticsQuery;

    WorkComplexity(AnalyticsQuery analyticsQuery) {
        this.analyticsQuery = analyticsQuery;
    }

    /**
     * The two sets of bars of Work complexity.
     *
     * @param person the person's Feature work items
     * @param repository every Feature work item of the repository, the person's included
     */
    public record Result(Distribution person, Distribution repository) {}

    @Transactional(readOnly = true)
    public Result of(long personId, Quarter quarter) {
        Map<String, Count> person = emptyBars();
        Map<String, Count> repository = emptyBars();
        List<Row> rows = analyticsQuery
                .statement(QUERY, quarter)
                .param("personId", personId)
                .query(WorkComplexity::rowOf)
                .list();
        for (Row row : rows) {
            repository.merge(row.complexity(), row.count(), Count::plus);
            if (row.own()) {
                person.merge(row.complexity(), row.count(), Count::plus);
            }
        }
        return new Result(Distribution.of(person), Distribution.of(repository));
    }

    private record Row(boolean own, String complexity, Count count) {}

    private static Row rowOf(ResultSet row, int rowNumber) throws SQLException {
        return new Row(row.getBoolean("own"), row.getString("complexity"), AnalyticsQuery.countOf(row));
    }

    private static Map<String, Count> emptyBars() {
        Map<String, Count> bars = new LinkedHashMap<>();
        BARS.forEach(bar -> bars.put(bar, Count.none()));
        return bars;
    }
}
