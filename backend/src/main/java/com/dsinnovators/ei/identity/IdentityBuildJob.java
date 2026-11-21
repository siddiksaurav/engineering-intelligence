package com.dsinnovators.ei.identity;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.identity.ProposalAssignment.PersonUser;
import com.dsinnovators.ei.job.FailedItems;
import com.dsinnovators.ei.job.Job;
import com.dsinnovators.ei.job.JobArguments;
import com.dsinnovators.ei.job.JobCounts;
import com.dsinnovators.ei.source.github.GitHubAccount;
import com.dsinnovators.ei.source.github.StoredGitHubAccounts;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Makes one person of every human GitHub account in the stored records and proposes the OpenProject
 * user behind each person (F04 design, "Steps"). Run it after {@code openproject-import}. Running it
 * again changes only what changed, and never touches a decision of the user.
 */
@Component
class IdentityBuildJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(IdentityBuildJob.class);

    private final StoredGitHubAccounts storedAccounts;
    private final BotAccounts botAccounts;
    private final Persons persons;
    private final OpenProjectCandidates candidates;
    private final IdentityProposalRepository proposals;
    private final ProposalWriter writer;
    private final Clock clock;

    IdentityBuildJob(
            StoredGitHubAccounts storedAccounts,
            BotAccounts botAccounts,
            Persons persons,
            OpenProjectCandidates candidates,
            IdentityProposalRepository proposals,
            ProposalWriter writer,
            Clock clock) {
        this.storedAccounts = storedAccounts;
        this.botAccounts = botAccounts;
        this.persons = persons;
        this.candidates = candidates;
        this.proposals = proposals;
        this.writer = writer;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "identity-build";
    }

    @Override
    public void checkArguments(JobArguments arguments) {
        if (!arguments.names().isEmpty()) {
            throw new IllegalArgumentException(
                    "Unknown argument '" + arguments.names().iterator().next() + "'. identity-build takes none");
        }
    }

    /**
     * An account that fails on its own is counted and the run goes on; the run then fails at the
     * end, naming the accounts.
     */
    @Override
    public void run(JobArguments arguments, JobCounts counts) {
        Quarter quarter = Quarter.of(LocalDate.now(clock));
        List<GitHubAccount> accounts = storedAccounts.accounts();
        counts.add("accounts_read", accounts.size());
        List<String> failedLogins = new ArrayList<>();
        Map<Long, GitHubAccount> accountsByPerson = new LinkedHashMap<>();
        Map<Long, List<Candidate>> candidatesByPerson = new LinkedHashMap<>();
        for (GitHubAccount account : accounts) {
            if (botAccounts.isBot(account.login(), account.typename(), quarter)) {
                counts.add("bots_skipped", 1);
                continue;
            }
            try {
                long personId = ensurePerson(account, counts);
                accountsByPerson.put(personId, account);
                candidatesByPerson.put(personId, candidates.of(storedAccounts.pullRequestsOf(account.databaseId())));
            } catch (RuntimeException failure) {
                fail(account, failure, failedLogins, counts);
            }
        }
        Set<PersonUser> confirmed = Set.copyOf(proposals.pairsIn(IdentityProposal.CONFIRMED));
        Map<Long, Candidate> winners = ProposalAssignment.assign(
                candidatesByPerson, Set.copyOf(proposals.pairsIn(IdentityProposal.REJECTED)), confirmed);
        for (Map.Entry<Long, GitHubAccount> person : accountsByPerson.entrySet()) {
            try {
                writeProposal(person.getKey(), winners, confirmed, counts);
            } catch (RuntimeException failure) {
                fail(person.getValue(), failure, failedLogins, counts);
            }
        }
        if (!failedLogins.isEmpty()) {
            throw new IdentityBuildFailedException(
                    failedLogins.size() + " accounts failed: " + FailedItems.named(failedLogins));
        }
    }

    private long ensurePerson(GitHubAccount account, JobCounts counts) {
        Persons.Result result = persons.ensure(account);
        switch (result.outcome()) {
            case CREATED -> counts.add("persons_created", 1);
            case LOGIN_UPDATED -> counts.add("logins_updated", 1);
            case UNCHANGED -> {
                // Nothing to count: the person was there already.
            }
        }
        return result.personId();
    }

    private void writeProposal(
            long personId, Map<Long, Candidate> winners, Set<PersonUser> confirmed, JobCounts counts) {
        Optional<Candidate> winner = Optional.ofNullable(winners.get(personId));
        switch (writer.write(personId, winner)) {
            case WRITTEN -> counts.add("proposals_written", 1);
            case UNCHANGED -> {
                if (winner.isPresent()) {
                    counts.add("proposals_unchanged", 1);
                }
            }
            case REMOVED -> counts.add("proposals_removed", 1);
        }
        boolean hasConfirmedUser = confirmed.stream().anyMatch(pair -> pair.personId() == personId);
        if (winner.isEmpty() && !hasConfirmedUser) {
            counts.add("persons_without_proposal", 1);
        }
    }

    private static void fail(
            GitHubAccount account, RuntimeException failure, List<String> failedLogins, JobCounts counts) {
        LOG.error("Account {} could not be turned into a person", account.login(), failure);
        failedLogins.add(account.login());
        counts.add("accounts_failed", 1);
    }
}
