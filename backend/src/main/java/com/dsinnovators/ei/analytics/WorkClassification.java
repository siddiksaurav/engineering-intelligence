package com.dsinnovators.ei.analytics;

import com.dsinnovators.ei.common.Quarter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * What kind of work a person spent the quarter on: their human-authored pull requests merged in the
 * quarter, counted by the work type of the current digest of the pull request. A pull request
 * without a digest is counted as not judged yet, so the shares add up to 1 (decisions 189, 198).
 */
@Component
public class WorkClassification {

    private static final String QUERY =
            AnalyticsSql.with(List.of(AnalyticsSql.HUMAN_PULL_REQUESTS, AnalyticsSql.CURRENT_DIGESTS), """
            select coalesce(digest.value ->> 'workType', 'not_judged_yet') as work_type,
                   count(*) as count,
                   null::bigint[] as item_ids,
                   array_agg(pull_request.number order by pull_request.number) as pull_request_numbers
            from human_pull_request pull_request
            left join current_digest digest on digest.external_id = pull_request.external_id
            where pull_request.person_id = :personId
            group by work_type
            order by work_type
            """);

    private final AnalyticsQuery analyticsQuery;

    WorkClassification(AnalyticsQuery analyticsQuery) {
        this.analyticsQuery = analyticsQuery;
    }

    /** The pull requests of the person in the quarter by work type; only the work types that occur. */
    @Transactional(readOnly = true)
    public Distribution of(long personId, Quarter quarter) {
        Map<String, Count> countsByWorkType = AnalyticsQuery.countsByBucket(
                analyticsQuery.statement(QUERY, quarter).param("personId", personId), "work_type");
        return Distribution.of(countsByWorkType);
    }
}
