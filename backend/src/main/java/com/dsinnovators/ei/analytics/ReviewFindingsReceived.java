package com.dsinnovators.ei.analytics;

import com.dsinnovators.ei.common.Quarter;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * How many real problems reviewers find in a person's code before it is merged: the substantive
 * Review comment written items on the person's human-authored pull requests merged in the quarter,
 * per pull request, by comment type and by tier. The pull requests without a comment are in the
 * denominator too (decision 185). Style and acknowledgement comments are left out.
 */
@Component
public class ReviewFindingsReceived {

    private static final String PULL_REQUESTS_QUERY = AnalyticsSql.with(List.of(AnalyticsSql.HUMAN_PULL_REQUESTS), """
            select number
            from human_pull_request
            where person_id = :personId
            order by number
            """);

    private static final String COMMENTS_QUERY =
            AnalyticsSql.with(List.of(AnalyticsSql.CURRENT_ITEMS_AND_JUDGMENTS, """
            comment_item as (
                select item.id, (item.fields #>> '{pullRequest,number}')::int as pull_request_number
                from current_item item
                where item.evidence_type = 'review_comment_written'
                  and (item.fields ->> 'pullRequestAuthorPersonId')::bigint = :personId
                  and item.occurred_on between :firstDay and :lastDay
            )"""), ReviewCommentBreakdown.SELECT);

    private final AnalyticsQuery analyticsQuery;

    ReviewFindingsReceived(AnalyticsQuery analyticsQuery) {
        this.analyticsQuery = analyticsQuery;
    }

    /**
     * The result of Review findings received.
     *
     * @param pullRequests the person's human-authored pull requests merged in the quarter
     * @param comments the comments received split by comment type and tier
     * @param substantivePerPullRequest substantive comments divided by pull requests; null when there are none
     */
    public record Result(Count pullRequests, ReviewCommentBreakdown comments, Double substantivePerPullRequest) {}

    @Transactional(readOnly = true)
    public Result of(long personId, Quarter quarter) {
        Count pullRequests = Count.ofPullRequests(analyticsQuery
                .statement(PULL_REQUESTS_QUERY, quarter)
                .param("personId", personId)
                .query((row, rowNumber) -> row.getInt("number"))
                .list());
        ReviewCommentBreakdown comments = ReviewCommentBreakdown.of(
                analyticsQuery.statement(COMMENTS_QUERY, quarter).param("personId", personId));
        return new Result(
                pullRequests, comments, Shares.of(comments.substantive().count(), pullRequests.count()));
    }
}
