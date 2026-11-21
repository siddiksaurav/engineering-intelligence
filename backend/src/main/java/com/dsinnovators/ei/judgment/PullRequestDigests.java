package com.dsinnovators.ei.judgment;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the digests of pull requests for the packages that build on them. It is the only way the
 * rest of the system sees a digest; the model itself stays behind {@link JudgmentModel} (decision 143).
 */
@Service
public class PullRequestDigests {

    private final JudgmentStore judgmentStore;

    PullRequestDigests(JudgmentStore judgmentStore) {
        this.judgmentStore = judgmentStore;
    }

    /**
     * A digest as it is stored.
     *
     * @param judgment the row, for its id, reason, facts with links, model and prompt version
     * @param digest the answer the row holds
     */
    public record CurrentDigest(Judgment judgment, PullRequestDigest digest) {

        public long judgmentId() {
            return judgment.getId();
        }
    }

    /**
     * The digest of the pull request with this external id, such as {@code opf/openproject#24204},
     * that no other digest supersedes; empty when the pull request has none yet.
     */
    @Transactional(readOnly = true)
    public Optional<CurrentDigest> currentOf(String pullRequestExternalId) {
        return judgmentStore
                .findCurrentOfPullRequest(JudgmentKind.PULL_REQUEST_DIGEST, pullRequestExternalId)
                .map(judgment -> new CurrentDigest(
                        judgment,
                        PullRequestDigest.fromAnswer(
                                judgment.getValue(),
                                judgment.getValue().path("judgedOnPartialCode").asBoolean())));
    }
}
