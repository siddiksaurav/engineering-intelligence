package com.dsinnovators.ei.source.github;

import com.dsinnovators.ei.job.Job;
import com.dsinnovators.ei.job.JobArguments;
import com.dsinnovators.ei.job.JobCounts;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Imports merged pull requests of a GitHub repository into {@code raw_record} (F02 design 5.2), by
 * a range of merge dates or by a list of numbers. Running it again stores only what changed.
 */
@Component
class GitHubImportJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubImportJob.class);
    private static final int FAILED_NUMBERS_SHOWN = 20;

    private final MergedPullRequestSearch search;
    private final PullRequestFetcher fetcher;
    private final PullRequestRecorder recorder;

    GitHubImportJob(MergedPullRequestSearch search, PullRequestFetcher fetcher, PullRequestRecorder recorder) {
        this.search = search;
        this.fetcher = fetcher;
        this.recorder = recorder;
    }

    @Override
    public String name() {
        return "github-import";
    }

    @Override
    public void checkArguments(JobArguments arguments) {
        GitHubImportArguments.parse(arguments);
    }

    /**
     * Fails at once when the listing fails or the token is missing or refused. A pull request that
     * fails on its own is counted and the run goes on; the run then fails at the end, naming it.
     */
    @Override
    public void run(JobArguments arguments, JobCounts counts) throws InterruptedException {
        GitHubImportArguments importArguments = GitHubImportArguments.parse(arguments);
        String repository = importArguments.repository();
        List<Integer> numbers = importArguments.mergeRange().isPresent()
                ? search.numbers(
                        repository,
                        importArguments.mergeRange().get().from(),
                        importArguments.mergeRange().get().to())
                : importArguments.pullRequestNumbers();
        counts.add("pull_requests_listed", numbers.size());

        List<Integer> failed = new ArrayList<>();
        for (int number : numbers) {
            try {
                importOne(repository, number, counts);
            } catch (GitHubTokenMissingException | GitHubTokenRefusedException noCallCanSucceed) {
                throw noCallCanSucceed;
            } catch (RuntimeException failure) {
                LOG.error("{}#{} could not be imported", repository, number, failure);
                counts.add("pull_requests_failed", 1);
                failed.add(number);
            }
        }
        if (!failed.isEmpty()) {
            throw new GitHubImportFailedException(failed.size() + " of " + numbers.size() + " pull requests failed: "
                    + failed.stream()
                            .limit(FAILED_NUMBERS_SHOWN)
                            .map(number -> "#" + number)
                            .collect(Collectors.joining(", ")));
        }
    }

    private void importOne(String repository, int number, JobCounts counts) throws InterruptedException {
        switch (fetcher.fetch(repository, number)) {
            case PullRequestFetch.Found found -> {
                List<StoredRecord> stored = recorder.store(found.pullRequest());
                stored.forEach(record -> counts.add(
                        record.kind().recordKind() + "."
                                + record.outcome().name().toLowerCase(Locale.ROOT),
                        1));
                if (found.pullRequest().diffTruncated()) {
                    counts.add(GitHubRecordKind.PULL_REQUEST_DIFF.recordKind() + ".truncated", 1);
                }
                LOG.info("{}#{}: {} records stored or unchanged", repository, number, stored.size());
            }
            case PullRequestFetch.NotFound notFound -> {
                LOG.warn("{}#{} does not exist", repository, number);
                counts.add("pull_requests_not_found", 1);
            }
            case PullRequestFetch.NotMerged notMerged -> {
                LOG.warn("{}#{} is not merged; nothing stored", repository, number);
                counts.add("pull_requests_not_merged", 1);
            }
        }
    }
}
