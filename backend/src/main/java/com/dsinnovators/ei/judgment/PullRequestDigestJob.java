package com.dsinnovators.ei.judgment;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.identity.BotAccounts;
import com.dsinnovators.ei.job.FailedItems;
import com.dsinnovators.ei.job.Job;
import com.dsinnovators.ei.job.JobArguments;
import com.dsinnovators.ei.job.JobCounts;
import com.dsinnovators.ei.source.RawRecord;
import com.dsinnovators.ei.source.github.GitHubAuthor;
import com.dsinnovators.ei.source.github.GitHubImportArguments;
import com.dsinnovators.ei.source.github.PullRequestFacts;
import com.dsinnovators.ei.source.github.StoredPullRequests;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stage 1: a digest of every stored pull request of a human author (F06 design, "Main flows" 1),
 * oldest merge first, one call at a time. Bot pull requests and pull requests without an author get
 * none. A pull request whose input was judged before is skipped without a call. Run it after {@code
 * github-import}. When every provider has reached its limit the run stops and succeeds; the next
 * run goes on with the pull requests not yet judged (decision 152).
 */
@Component
class PullRequestDigestJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(PullRequestDigestJob.class);
    private static final String PULL_REQUEST_NUMBERS = "pullRequestNumbers";

    private final StoredPullRequests storedPullRequests;
    private final BotAccounts botAccounts;
    private final PullRequestDigester digester;
    private final ProviderChain providerChain;
    private final ModelJobGuard modelJobGuard;
    private final Clock clock;

    PullRequestDigestJob(
            StoredPullRequests storedPullRequests,
            BotAccounts botAccounts,
            PullRequestDigester digester,
            ProviderChain providerChain,
            ModelJobGuard modelJobGuard,
            Clock clock) {
        this.storedPullRequests = storedPullRequests;
        this.botAccounts = botAccounts;
        this.digester = digester;
        this.providerChain = providerChain;
        this.modelJobGuard = modelJobGuard;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "pull-request-digest";
    }

    @Override
    public void checkArguments(JobArguments arguments) {
        selectedNumbers(arguments);
    }

    /**
     * Fails at once when no provider has a key or another model job runs. A pull request that fails on its own, with no diff
     * stored or an answer that breaks the schema twice, is counted and the run goes on; the run then
     * fails at the end, naming the first twenty.
     */
    @Override
    public void run(JobArguments arguments, JobCounts counts) {
        ModelJobGuard.Run hold = modelJobGuard.enter(name());
        try {
            runGuarded(arguments, counts);
        } finally {
            hold.close();
        }
    }

    private void runGuarded(JobArguments arguments, JobCounts counts) {
        providerChain.startRunRequiringAProvider();
        Optional<Set<Integer>> numbers = selectedNumbers(arguments);
        Quarter quarter = Quarter.of(LocalDate.now(clock));
        List<RawRecord> selected = storedPullRequests.all().stream()
                .filter(record -> numbers.map(wanted ->
                                wanted.contains(PullRequestFacts.of(record).number()))
                        .orElse(true))
                .toList();
        counts.add("pull_requests_read", selected.size());
        List<String> notStored = notStored(numbers, selected);
        List<String> failed = new ArrayList<>(notStored);
        List<RawRecord> humans = humansOf(selected, quarter, counts);
        try {
            digestAll(humans, quarter, counts, failed);
        } finally {
            providerChain.reportUseTo(counts);
        }
        if (!failed.isEmpty()) {
            counts.add("pull_requests_failed", failed.size());
            throw new PullRequestDigestFailedException(failed.size() + " of " + (humans.size() + notStored.size())
                    + " pull requests failed: " + FailedItems.named(failed));
        }
    }

    private void digestAll(List<RawRecord> humans, Quarter quarter, JobCounts counts, List<String> failed) {
        for (int index = 0; index < humans.size(); index++) {
            RawRecord pullRequest = humans.get(index);
            try {
                digester.digest(pullRequest, quarter, counts);
            } catch (ModelLimitReachedException limit) {
                LOG.warn("{}; the next run goes on with the pull requests not yet judged", limit.getMessage());
                counts.add("pull_requests_left_for_later", humans.size() - index);
                return;
            } catch (RuntimeException failure) {
                LOG.error("{} could not be digested", pullRequest.getExternalId(), failure);
                failed.add(pullRequest.getExternalId());
            }
        }
    }

    private List<RawRecord> humansOf(List<RawRecord> pullRequests, Quarter quarter, JobCounts counts) {
        List<RawRecord> humans = new ArrayList<>();
        for (RawRecord pullRequest : pullRequests) {
            Optional<GitHubAuthor> author = PullRequestFacts.of(pullRequest).author();
            if (author.isEmpty()) {
                counts.add("records_without_author", 1);
            } else if (botAccounts.isBot(author.get().login(), author.get().typename(), quarter)) {
                counts.add("bot_pull_requests_skipped", 1);
            } else {
                humans.add(pullRequest);
            }
        }
        return humans;
    }

    private static List<String> notStored(Optional<Set<Integer>> numbers, List<RawRecord> selected) {
        Set<Integer> stored = new HashSet<>();
        selected.forEach(record -> stored.add(PullRequestFacts.of(record).number()));
        return numbers.stream()
                .flatMap(Set::stream)
                .filter(number -> !stored.contains(number))
                .sorted()
                .map(number -> "#" + number + " (not stored)")
                .toList();
    }

    private static Optional<Set<Integer>> selectedNumbers(JobArguments arguments) {
        for (String argument : arguments.names()) {
            if (!argument.equals(PULL_REQUEST_NUMBERS)) {
                throw new IllegalArgumentException(
                        "Unknown argument '" + argument + "'. Known: " + PULL_REQUEST_NUMBERS);
            }
        }
        return arguments
                .get(PULL_REQUEST_NUMBERS)
                .map(value -> Set.copyOf(GitHubImportArguments.pullRequestNumbers(value)));
    }
}
