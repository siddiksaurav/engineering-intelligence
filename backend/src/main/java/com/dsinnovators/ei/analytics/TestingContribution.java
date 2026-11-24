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
 * Whether a person writes tests together with their code: of their pull requests merged in the
 * quarter that are feature, enhancement or fix work by the digest (decision 190), the share with a
 * current Test change item (decision 182); and of the pull requests of their current Bug fix items
 * in the quarter, of both kinds (decision 183), the share with a Test change item of any kind
 * (decision 191). It is not gate G1 of F11.
 */
@Component
public class TestingContribution {

    /** The work types of the digest that count as feature and fix work; a rule of decision 190. */
    private static final List<String> FEATURE_AND_FIX_WORK_TYPES = List.of("feature", "enhancement", "fix");

    private static final String TESTED = "tested";
    private static final String UNTESTED = "untested";
    private static final String WITHOUT_DIGEST = "without_digest";

    private static final String TEST_CHANGE_OF_PULL_REQUEST = """
            left join test_change_item test_change on test_change.pull_request_external_id = pull_request.external_id
            left join current_judgment test_tier
                   on test_tier.evidence_item_id = test_change.id and test_tier.kind = 'item_tier'
            """;

    private static final String FEATURE_AND_FIX_QUERY = AnalyticsSql.with(
            List.of(
                    AnalyticsSql.HUMAN_PULL_REQUESTS,
                    AnalyticsSql.CURRENT_ITEMS_JUDGMENTS_AND_DIGESTS,
                    AnalyticsSql.TEST_CHANGE_ITEM),
            """
            select case when digest.value is null then 'without_digest'
                        when digest.value ->> 'workType' not in (:workTypes) then 'other_work_type'
                        when test_change.id is null then 'untested'
                        else 'tested' end as category,
                   case when test_change.id is null then null
                        else coalesce(test_tier.tier, 'not_judged_yet') end as test_change_tier,
                   count(*) as count,
                   array_agg(test_change.id order by test_change.id) filter (where test_change.id is not null)
                       as item_ids,
                   array_agg(pull_request.number order by pull_request.number) as pull_request_numbers
            from human_pull_request pull_request
            left join current_digest digest on digest.external_id = pull_request.external_id
            """ + TEST_CHANGE_OF_PULL_REQUEST + """
            where pull_request.person_id = :personId
            group by category, test_change_tier
            """);

    private static final String BUG_FIX_QUERY = AnalyticsSql.with(
            List.of(AnalyticsSql.CURRENT_ITEMS_AND_JUDGMENTS, AnalyticsSql.TEST_CHANGE_ITEM, """
            pull_request as (
                select distinct record.external_id, (record.payload ->> 'number')::int as number
                from current_item item
                join evidence_item_source source
                       on source.evidence_item_id = item.id and source.role = 'pull_request'
                join raw_record record on record.id = source.raw_record_id
                where item.evidence_type = 'bug_fix'
                  and item.person_id = :personId
                  and item.occurred_on between :firstDay and :lastDay
            )"""),
            """
            select case when test_change.id is null then 'untested' else 'tested' end as category,
                   case when test_change.id is null then null
                        else coalesce(test_tier.tier, 'not_judged_yet') end as test_change_tier,
                   count(*) as count,
                   array_agg(test_change.id order by test_change.id) filter (where test_change.id is not null)
                       as item_ids,
                   array_agg(pull_request.number order by pull_request.number) as pull_request_numbers
            from pull_request
            """ + TEST_CHANGE_OF_PULL_REQUEST + """
            group by category, test_change_tier
            """);

    private final AnalyticsQuery analyticsQuery;

    TestingContribution(AnalyticsQuery analyticsQuery) {
        this.analyticsQuery = analyticsQuery;
    }

    /**
     * The pull requests of one kind and how many of them come with a Test change item.
     *
     * @param total the pull requests of this kind
     * @param tested those with a Test change item
     * @param untested those without one
     * @param share tested divided by total; null when there is none
     * @param testedByTier the tested ones by the tier of their Test change item
     */
    public record Share(long total, Count tested, Count untested, Double share, Distribution testedByTier) {}

    /**
     * The result of Testing contribution.
     *
     * @param featureAndFixPullRequests the person's pull requests of feature, enhancement and fix work
     * @param withoutDigest the person's pull requests without a digest, which are left out of the share
     * @param bugFixPullRequests the pull requests of the person's Bug fix items
     * @param coverage the person's pull requests with a digest, of all of them; the digest decides which pull requests are feature and fix work
     */
    public record Result(
            Share featureAndFixPullRequests, Count withoutDigest, Share bugFixPullRequests, Coverage coverage) {}

    @Transactional(readOnly = true)
    public Result of(long personId, Quarter quarter) {
        List<Row> featureAndFixRows = analyticsQuery
                .statement(FEATURE_AND_FIX_QUERY, quarter)
                .param("personId", personId)
                .param("workTypes", FEATURE_AND_FIX_WORK_TYPES)
                .query(TestingContribution::rowOf)
                .list();
        List<Row> bugFixRows = analyticsQuery
                .statement(BUG_FIX_QUERY, quarter)
                .param("personId", personId)
                .query(TestingContribution::rowOf)
                .list();
        Count withoutDigest = countOf(featureAndFixRows, WITHOUT_DIGEST);
        long pullRequests =
                featureAndFixRows.stream().mapToLong(row -> row.count().count()).sum();
        return new Result(
                shareOf(featureAndFixRows),
                withoutDigest,
                shareOf(bugFixRows),
                new Coverage(pullRequests - withoutDigest.count(), pullRequests));
    }

    private record Row(String category, String tier, Count count) {}

    private static Row rowOf(ResultSet row, int rowNumber) throws SQLException {
        return new Row(row.getString("category"), row.getString("test_change_tier"), AnalyticsQuery.countOf(row));
    }

    private static Count countOf(List<Row> rows, String category) {
        return rows.stream()
                .filter(row -> row.category().equals(category))
                .map(Row::count)
                .reduce(Count.none(), Count::plus);
    }

    private static Share shareOf(List<Row> rows) {
        Count tested = countOf(rows, TESTED);
        Count untested = countOf(rows, UNTESTED);
        Map<String, Count> testedByTier = new LinkedHashMap<>();
        rows.stream()
                .filter(row -> row.category().equals(TESTED))
                .forEach(row -> testedByTier.merge(row.tier(), row.count(), Count::plus));
        long total = tested.count() + untested.count();
        return new Share(total, tested, untested, Shares.of(tested.count(), total), Distribution.of(testedByTier));
    }
}
