package com.dsinnovators.ei.analytics;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Review comments split the way Review effectiveness and Review findings received both show them:
 * the substantive ones by comment type and by tier, and the ones without a judgment counted apart
 * (decisions 196, 198). A comment that is not substantive is left out (spec 7.2).
 *
 * @param substantive the substantive comments
 * @param byCommentType the substantive comments by comment type
 * @param byTier the substantive comments by tier; the total is the same as for the comment types
 * @param notJudgedYet the comments without a current judgment
 */
public record ReviewCommentBreakdown(
        Count substantive, Distribution byCommentType, Distribution byTier, Count notJudgedYet) {

    /**
     * The select that counts the items of a CTE {@code comment_item(id, pull_request_number)}; the query
     * must also define {@code current_judgment}.
     */
    static final String SELECT = """
            select coalesce(judgment.value ->> 'commentType', 'not_judged_yet') as comment_type,
                   judgment.tier as tier,
                   count(*) as count,
                   array_agg(comment.id order by comment.id) as item_ids,
                   array_agg(distinct comment.pull_request_number order by comment.pull_request_number)
                       filter (where comment.pull_request_number is not null) as pull_request_numbers
            from comment_item comment
            left join current_judgment judgment
                   on judgment.evidence_item_id = comment.id and judgment.kind = 'review_comment'
            where judgment.id is null or (judgment.value ->> 'substantive')::boolean
            group by comment_type, judgment.tier
            order by comment_type, judgment.tier
            """;

    static ReviewCommentBreakdown of(JdbcClient.StatementSpec statement) {
        Map<String, Count> byCommentType = new LinkedHashMap<>();
        Map<String, Count> byTier = new TreeMap<>();
        Count notJudgedYet = Count.none();
        for (Row row : statement.query(ReviewCommentBreakdown::rowOf).list()) {
            if (row.commentType().equals(Distribution.NOT_JUDGED_YET)) {
                notJudgedYet = notJudgedYet.plus(row.count());
            } else {
                byCommentType.merge(row.commentType(), row.count(), Count::plus);
                byTier.merge(row.tier(), row.count(), Count::plus);
            }
        }
        Distribution typeDistribution = Distribution.of(byCommentType);
        Count substantive = typeDistribution.slices().stream().map(Slice::count).reduce(Count.none(), Count::plus);
        return new ReviewCommentBreakdown(substantive, typeDistribution, Distribution.of(byTier), notJudgedYet);
    }

    private record Row(String commentType, String tier, Count count) {}

    private static Row rowOf(ResultSet row, int rowNumber) throws SQLException {
        return new Row(row.getString("comment_type"), row.getString("tier"), AnalyticsQuery.countOf(row));
    }
}
