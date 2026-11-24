package com.dsinnovators.ei.analytics;

import com.dsinnovators.ei.common.Quarter;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Bug fix items of a person in the quarter. A Bug is decided by the tracker type of the work
 * item, not by the model, so this count is the one of the team list and not a slice of the digest's
 * work types.
 */
@Component
public class BugsFixed {

    private static final String QUERY = AnalyticsSql.with(List.of(AnalyticsSql.CURRENT_ITEM, """
            bug_fix_item as (
                select item.id
                from current_item item
                where item.evidence_type = 'bug_fix'
                  and item.person_id = :personId
                  and item.occurred_on between :firstDay and :lastDay
            )"""), """
            select (select count(*) from bug_fix_item) as count,
                   array(select id from bug_fix_item order by id) as item_ids,
                   array(select distinct (record.payload ->> 'number')::int as number
                         from bug_fix_item item
                         join evidence_item_source source
                                on source.evidence_item_id = item.id and source.role = 'pull_request'
                         join raw_record record on record.id = source.raw_record_id
                         order by number) as pull_request_numbers
            """);

    private final AnalyticsQuery analyticsQuery;

    BugsFixed(AnalyticsQuery analyticsQuery) {
        this.analyticsQuery = analyticsQuery;
    }

    /** The person's current Bug fix items occurring in the quarter, with the pull requests they rest on. */
    @Transactional(readOnly = true)
    public Count of(long personId, Quarter quarter) {
        return analyticsQuery
                .statement(QUERY, quarter)
                .param("personId", personId)
                .query((row, rowNumber) -> AnalyticsQuery.countOf(row))
                .single();
    }
}
