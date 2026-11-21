package com.dsinnovators.ei.judgment;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.job.FailedItems;
import com.dsinnovators.ei.job.Job;
import com.dsinnovators.ei.job.JobArguments;
import com.dsinnovators.ei.job.JobCounts;
import com.dsinnovators.ei.source.RawRecord;
import com.dsinnovators.ei.source.github.ReviewCommentText;
import com.dsinnovators.ei.source.github.StoredPullRequests;
import com.dsinnovators.ei.source.github.StoredReviewComments;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stage 2: the comment type of every current Review comment written item (F07 design, "Main flows"
 * 2), one call per chunk of the comments of a pull request. A chunk whose input was judged before is
 * skipped without a call. Run it after {@code evidence-build}. When every provider has reached its
 * limit the run stops and succeeds; the next run goes on with the chunks not yet judged (decision 152).
 */
@Component
class ReviewCommentJudgmentJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(ReviewCommentJudgmentJob.class);

    private final ReviewCommentItems reviewCommentItems;
    private final StoredReviewComments storedReviewComments;
    private final StoredPullRequests storedPullRequests;
    private final ReviewCommentJudge judge;
    private final ProviderChain providerChain;
    private final ModelJobGuard modelJobGuard;
    private final Clock clock;
    private final ReviewCommentLimits limits;

    ReviewCommentJudgmentJob(
            ReviewCommentItems reviewCommentItems,
            StoredReviewComments storedReviewComments,
            StoredPullRequests storedPullRequests,
            ReviewCommentJudge judge,
            ProviderChain providerChain,
            ModelJobGuard modelJobGuard,
            Clock clock,
            JudgmentProperties properties) {
        this.reviewCommentItems = reviewCommentItems;
        this.storedReviewComments = storedReviewComments;
        this.storedPullRequests = storedPullRequests;
        this.judge = judge;
        this.providerChain = providerChain;
        this.modelJobGuard = modelJobGuard;
        this.clock = clock;
        this.limits = properties.reviewCommentLimits();
    }

    @Override
    public String name() {
        return "review-comment-judgment";
    }

    @Override
    public void checkArguments(JobArguments arguments) {
        if (!arguments.names().isEmpty()) {
            throw new IllegalArgumentException(
                    "The job " + name() + " takes no arguments, was given " + arguments.names());
        }
    }

    /**
     * Fails at once when no provider has a key or another model job runs. A chunk that fails on its
     * own, with an answer that breaks the schema twice or a comment or pull request that is not
     * stored, is counted and the run goes on; the run then fails at the end, naming the first twenty. A
     * pull request whose comments cannot be read counts as one failed chunk, named by the pull request.
     */
    @Override
    public void run(JobArguments arguments, JobCounts counts) {
        ModelJobGuard.Run hold = modelJobGuard.enter(name());
        try {
            runGuarded(counts);
        } finally {
            hold.close();
        }
    }

    private void runGuarded(JobCounts counts) {
        providerChain.startRunRequiringAProvider();
        Quarter quarter = Quarter.of(LocalDate.now(clock));
        List<ReviewCommentToJudge> items = reviewCommentItems.current();
        counts.add("items_read", items.size());
        Map<String, List<ReviewCommentToJudge>> itemsByPullRequest = items.stream()
                .collect(Collectors.groupingBy(
                        ReviewCommentToJudge::pullRequestExternalId, LinkedHashMap::new, Collectors.toList()));
        counts.add("pull_requests_read", itemsByPullRequest.size());
        List<String> failed = new ArrayList<>();
        List<ReviewCommentChunks.Chunk> chunks = chunksOf(itemsByPullRequest, failed);
        counts.add("chunks", chunks.size());
        try {
            judgeAll(chunks, quarter, counts, failed);
        } finally {
            providerChain.reportUseTo(counts);
        }
        if (!failed.isEmpty()) {
            counts.add("chunks_failed", failed.size());
            throw new JudgmentJobFailedException(
                    failed.size() + " review comment chunks failed: " + FailedItems.named(failed));
        }
    }

    private List<ReviewCommentChunks.Chunk> chunksOf(
            Map<String, List<ReviewCommentToJudge>> itemsByPullRequest, List<String> failed) {
        List<ReviewCommentChunks.Chunk> chunks = new ArrayList<>();
        itemsByPullRequest.forEach((externalId, items) -> {
            try {
                chunks.addAll(chunksOfPullRequest(externalId, items));
            } catch (RuntimeException failure) {
                LOG.error("The review comments of {} could not be read", externalId, failure);
                failed.add(externalId);
            }
        });
        return chunks;
    }

    private List<ReviewCommentChunks.Chunk> chunksOfPullRequest(String externalId, List<ReviewCommentToJudge> items) {
        RawRecord pullRequest = storedPullRequests
                .findByExternalId(externalId)
                .orElseThrow(() -> new IllegalStateException("Pull request " + externalId + " is not stored"));
        List<ReviewCommentChunks.CommentPart> comments = new ArrayList<>();
        for (ReviewCommentToJudge item : items) {
            // The version the item was built from, so an edit made since changes nothing here until
            // evidence-build gives the comment a new item row.
            ReviewCommentText text = storedReviewComments.textOfRecord(item.commentRecordId());
            Optional<String> repliedBody = text.repliedToDatabaseId().stream()
                    .mapToObj(storedReviewComments::latestTextOf)
                    .flatMap(Optional::stream)
                    .map(ReviewCommentText::body)
                    .findFirst();
            comments.add(new ReviewCommentChunks.CommentPart(item, text, repliedBody));
        }
        return ReviewCommentChunks.of(
                new ReviewCommentChunks.PullRequestPart(
                        externalId, pullRequest.getPayload().path("title").asString(), PullRequestBody.of(pullRequest)),
                comments,
                limits,
                ReviewCommentJudge.PROMPT_VERSION);
    }

    private void judgeAll(
            List<ReviewCommentChunks.Chunk> chunks, Quarter quarter, JobCounts counts, List<String> failed) {
        for (int index = 0; index < chunks.size(); index++) {
            ReviewCommentChunks.Chunk chunk = chunks.get(index);
            try {
                judge.judge(chunk, quarter, counts);
            } catch (ModelLimitReachedException limit) {
                LOG.warn("{}; the next run goes on with the comments not yet judged", limit.getMessage());
                counts.add("items_left_for_later", commentsFrom(chunks, index));
                return;
            } catch (RuntimeException failure) {
                LOG.error("The review comments of {} could not be judged", chunk.describe(), failure);
                failed.add(chunk.describe());
            }
        }
    }

    private static long commentsFrom(List<ReviewCommentChunks.Chunk> chunks, int index) {
        return chunks.subList(index, chunks.size()).stream()
                .mapToLong(chunk -> chunk.comments().size())
                .sum();
    }
}
