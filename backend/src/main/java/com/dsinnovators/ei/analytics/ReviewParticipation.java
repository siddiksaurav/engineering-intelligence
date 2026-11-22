package com.dsinnovators.ei.analytics;

import com.dsinnovators.ei.common.Quarter;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * How much a person reviews other people's code and whether the reviews find real problems: the
 * pull requests of others merged in the quarter on which the person left a stored review of any
 * state or a current Review comment written item (decisions 111, 192), the different authors
 * reviewed (persons, so a bot's pull request adds none, decision 184) and the substantive comments
 * per pull request reviewed. An approval without a comment counts as zero comments (spec 7.2).
 */
@Component
public class ReviewParticipation {

    private static final String REVIEWED_PULL_REQUESTS_QUERY = AnalyticsSql.with(
            List.of(AnalyticsSql.MERGED_PULL_REQUESTS, AnalyticsSql.CURRENT_ITEMS_AND_JUDGMENTS, """
            latest_review as (
                select distinct on (record.external_id) record.payload
                from raw_record record
                join data_source source on source.id = record.data_source_id
                where source.key = 'github' and record.record_kind = 'review'
                order by record.external_id, record.version desc
            )""", """
            reviewed as (
                select (review.payload #>> '{pullRequest,repository,nameWithOwner}')
                           || '#' || (review.payload #>> '{pullRequest,number}') as pull_request_external_id
                from latest_review review
                join person_identity identity
                       on identity.data_source_id = (select id from data_source where key = 'github')
                      and identity.external_id = review.payload #>> '{author,databaseId}'
                where identity.person_id = :personId
                union
                select item.fields #>> '{pullRequest,externalId}'
                from current_item item
                where item.evidence_type = 'review_comment_written' and item.person_id = :personId
            )"""), """
            select pull_request.number, pull_request.person_id as author_person_id
            from merged_pull_request pull_request
            join reviewed on reviewed.pull_request_external_id = pull_request.external_id
            where pull_request.person_id is distinct from :personId
            order by pull_request.number
            """);

    private static final String COMMENTS_QUERY =
            AnalyticsSql.with(List.of(AnalyticsSql.CURRENT_ITEMS_AND_JUDGMENTS, """
            comment_item as (
                select item.id, (item.fields #>> '{pullRequest,number}')::int as pull_request_number
                from current_item item
                where item.evidence_type = 'review_comment_written'
                  and item.person_id = :personId
                  and item.occurred_on between :firstDay and :lastDay
            )"""), ReviewCommentBreakdown.SELECT);

    private final AnalyticsQuery analyticsQuery;

    ReviewParticipation(AnalyticsQuery analyticsQuery) {
        this.analyticsQuery = analyticsQuery;
    }

    /**
     * The result of Review participation and Review effectiveness.
     *
     * @param pullRequestsReviewed the pull requests of others reviewed in the quarter
     * @param authorsReviewed the different persons whose pull requests were reviewed, with the numbers of those pull requests
     * @param comments the Review comment written items split by comment type and tier
     * @param substantivePerPullRequestReviewed substantive comments divided by pull requests reviewed; null when there are none
     */
    public record Result(
            Count pullRequestsReviewed,
            Count authorsReviewed,
            ReviewCommentBreakdown comments,
            Double substantivePerPullRequestReviewed) {}

    @Transactional(readOnly = true)
    public Result of(long personId, Quarter quarter) {
        List<ReviewedPullRequest> reviewed = analyticsQuery
                .statement(REVIEWED_PULL_REQUESTS_QUERY, quarter)
                .param("personId", personId)
                .query((row, rowNumber) ->
                        new ReviewedPullRequest(row.getInt("number"), (Long) row.getObject("author_person_id")))
                .list();
        ReviewCommentBreakdown comments = ReviewCommentBreakdown.of(
                analyticsQuery.statement(COMMENTS_QUERY, quarter).param("personId", personId));
        Count pullRequestsReviewed = Count.ofPullRequests(
                reviewed.stream().map(ReviewedPullRequest::number).toList());
        return new Result(
                pullRequestsReviewed,
                authorsOf(reviewed),
                comments,
                Shares.of(comments.substantive().count(), pullRequestsReviewed.count()));
    }

    private record ReviewedPullRequest(int number, Long authorPersonId) {}

    private static Count authorsOf(List<ReviewedPullRequest> reviewed) {
        Set<Long> authors = new TreeSet<>();
        TreeSet<Integer> numbers = new TreeSet<>();
        for (ReviewedPullRequest pullRequest : reviewed) {
            if (pullRequest.authorPersonId() != null) {
                authors.add(pullRequest.authorPersonId());
                numbers.add(pullRequest.number());
            }
        }
        return new Count(authors.size(), List.of(), List.copyOf(numbers));
    }
}
