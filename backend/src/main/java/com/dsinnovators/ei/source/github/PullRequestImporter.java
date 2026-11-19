package com.dsinnovators.ei.source.github;

import com.dsinnovators.ei.job.JobCounts;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Fetches one pull request by number, stores its records and counts each by kind and outcome. Shared
 * by every job that imports pull requests.
 */
@Service
public class PullRequestImporter {

    private static final Logger LOG = LoggerFactory.getLogger(PullRequestImporter.class);

    private final PullRequestFetcher fetcher;
    private final PullRequestRecorder recorder;

    PullRequestImporter(PullRequestFetcher fetcher, PullRequestRecorder recorder) {
        this.fetcher = fetcher;
        this.recorder = recorder;
    }

    /**
     * Counts {@code <kind>.<outcome>} for each stored record, {@code pull_request_diff.truncated},
     * {@code pull_requests_not_found} and {@code pull_requests_not_merged}.
     */
    public void importPullRequest(String repository, int number, JobCounts counts) throws InterruptedException {
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
