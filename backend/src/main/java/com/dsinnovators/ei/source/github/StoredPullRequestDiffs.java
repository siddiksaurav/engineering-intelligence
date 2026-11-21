package com.dsinnovators.ei.source.github;

import com.dsinnovators.ei.source.RawRecordStore;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Reads the {@code pull_request_diff} records that the GitHub import stored; the latest version of each. */
@Component
public class StoredPullRequestDiffs {

    private final RawRecordStore rawRecordStore;

    StoredPullRequestDiffs(RawRecordStore rawRecordStore) {
        this.rawRecordStore = rawRecordStore;
    }

    /**
     * The latest diff of the pull request with this external id, such as {@code opf/openproject#24204};
     * empty when none was stored.
     */
    @Transactional(readOnly = true)
    public Optional<PullRequestDiff> latestOf(String pullRequestExternalId) {
        return rawRecordStore
                .findLatest(
                        PullRequestRecorder.DATA_SOURCE,
                        GitHubRecordKind.PULL_REQUEST_DIFF.recordKind(),
                        pullRequestExternalId)
                .map(PullRequestDiff::of);
    }
}
