package com.dsinnovators.ei.analytics;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Review comments split the way Review effectiveness and Review findings received both show them:
 * the substantive ones by comment type and by tier, and the ones that are not substantive or have no
 * judgment counted apart, so that comments written = substantive + not substantive + not judged yet
 * (decisions 196, 198, 297). A comment that is not substantive has no type and no tier (spec 7.2).
 *
 * @param substantive the substantive comments
 * @param byCommentType the judged substantive comments by comment type, without a bucket for the comments not judged yet
 * @param byTier the judged substantive comments by tier; the total is the same as for the comment types
 * @param notSubstantive the judged comments that are not substantive
 * @param notJudgedYet the comments without a current judgment
 * @param coverage the judged comments (substantive or not) of all the comments written
 */
public record ReviewCommentBreakdown(
        Count substantive,
        Distribution byCommentType,
        Distribution byTier,
        Count notSubstantive,
        Count notJudgedYet,
        Coverage coverage) {

    private static final String SUBSTANTIVE = "substantive";
    private static final String NOT_SUBSTANTIVE = "not_substantive";

    /**
     * The select that counts the items of a CTE {@code comment_item(id, pull_request_number)}; the query
     * must also define {@code current_judgment}.
     */
    static final String SELECT = """
            select case when judgment.id is null then 'not_judged_yet'
                        when (judgment.value ->> 'substantive')::boolean then 'substantive'
                        else 'not_substantive' end as state,
                   judgment.value ->> 'commentType' as comment_type,
                   judgment.tier as tier,
                   count(*) as count,
                   array_agg(comment.id order by comment.id) as item_ids,
                   array_agg(distinct comment.pull_request_number order by comment.pull_request_number)
                       filter (where comment.pull_request_number is not null) as pull_request_numbers
            from comment_item comment
            left join current_judgment judgment
                   on judgment.evidence_item_id = comment.id and judgment.kind = 'review_comment'
            group by state, comment_type, judgment.tier
            order by comment_type, judgment.tier
            """;

    static ReviewCommentBreakdown of(JdbcClient.StatementSpec statement) {
        Map<String, Count> byCommentType = new LinkedHashMap<>();
        Map<String, Count> byTier = new TreeMap<>();
        Count notSubstantive = Count.none();
        Count notJudgedYet = Count.none();
        for (Row row : statement.query(ReviewCommentBreakdown::rowOf).list()) {
            switch (row.state()) {
                case NOT_SUBSTANTIVE -> notSubstantive = notSubstantive.plus(row.count());
                case SUBSTANTIVE -> {
                    byCommentType.merge(row.commentType(), row.count(), Count::plus);
                    byTier.merge(row.tier(), row.count(), Count::plus);
                }
                default -> notJudgedYet = notJudgedYet.plus(row.count());
            }
        }
        Distribution typeDistribution = Distribution.of(byCommentType);
        Count substantive = typeDistribution.slices().stream().map(Slice::count).reduce(Count.none(), Count::plus);
        long judged = substantive.count() + notSubstantive.count();
        return new ReviewCommentBreakdown(
                substantive,
                typeDistribution,
                Distribution.of(byTier),
                notSubstantive,
                notJudgedYet,
                new Coverage(judged, judged + notJudgedYet.count()));
    }

    private record Row(String state, String commentType, String tier, Count count) {}

    private static Row rowOf(ResultSet row, int rowNumber) throws SQLException {
        return new Row(
                row.getString("state"),
                row.getString("comment_type"),
                row.getString("tier"),
                AnalyticsQuery.countOf(row));
    }
}
