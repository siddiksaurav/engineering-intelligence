package com.dsinnovators.ei.analytics;

import com.dsinnovators.ei.common.Quarter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The hygiene number of the repository: of the human-authored pull requests merged in the quarter,
 * the share with at least one resolved link to a work item (decisions 104, 193). It is reported on
 * the project and never read as the performance of a person (spec 10.3).
 */
@Component
public class RepositoryHygiene {

    private static final String LINKED = "linked";

    private static final String QUERY = AnalyticsSql.with(List.of(AnalyticsSql.HUMAN_PULL_REQUESTS), """
            select linked,
                   count(*) as count,
                   null::bigint[] as item_ids,
                   array_agg(number order by number) as pull_request_numbers
            from (select pull_request.number,
                         exists (select 1 from pull_request_work_item link
                                 where link.pull_request_external_id = pull_request.external_id
                                   and link.state = 'resolved') as linked
                  from human_pull_request pull_request) classified
            group by linked
            """);

    private final AnalyticsQuery analyticsQuery;

    RepositoryHygiene(AnalyticsQuery analyticsQuery) {
        this.analyticsQuery = analyticsQuery;
    }

    /**
     * The result of the hygiene number.
     *
     * @param total the human-authored pull requests merged in the quarter
     * @param linked those with a resolved work item link
     * @param notLinked those without one
     * @param share linked divided by total; null when there is no such pull request
     */
    public record Result(long total, Count linked, Count notLinked, Double share) {}

    @Transactional(readOnly = true)
    public Result of(Quarter quarter) {
        Count linked = Count.none();
        Count notLinked = Count.none();
        for (Row row : analyticsQuery
                .statement(QUERY, quarter)
                .query(RepositoryHygiene::rowOf)
                .list()) {
            if (row.linked()) {
                linked = row.count();
            } else {
                notLinked = row.count();
            }
        }
        long total = linked.count() + notLinked.count();
        return new Result(total, linked, notLinked, Shares.of(linked.count(), total));
    }

    private record Row(boolean linked, Count count) {}

    private static Row rowOf(ResultSet row, int rowNumber) throws SQLException {
        return new Row(row.getBoolean(LINKED), AnalyticsQuery.countOf(row));
    }
}
