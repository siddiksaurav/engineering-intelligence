package com.dsinnovators.ei.source.github;

import com.dsinnovators.ei.job.FailedItems;
import com.dsinnovators.ei.job.Job;
import com.dsinnovators.ei.job.JobArguments;
import com.dsinnovators.ei.job.JobCounts;
import java.util.ArrayList;
import java.util.List;
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

    private final MergedPullRequestSearch search;
    private final PullRequestImporter importer;

    GitHubImportJob(MergedPullRequestSearch search, PullRequestImporter importer) {
        this.search = search;
        this.importer = importer;
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
                importer.importPullRequest(repository, number, counts);
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
                    + FailedItems.named(
                            failed.stream().map(number -> "#" + number).toList()));
        }
    }
}
