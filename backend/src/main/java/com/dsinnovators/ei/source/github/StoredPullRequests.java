package com.dsinnovators.ei.source.github;

import com.dsinnovators.ei.source.RawRecord;
import com.dsinnovators.ei.source.RawRecordStore;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Reads the {@code pull_request} records that the GitHub import stored; the latest version of each. */
@Component
public class StoredPullRequests {

    private final MergedPullRequestRecords mergedPullRequestRecords;
    private final RawRecordStore rawRecordStore;

    StoredPullRequests(MergedPullRequestRecords mergedPullRequestRecords, RawRecordStore rawRecordStore) {
        this.mergedPullRequestRecords = mergedPullRequestRecords;
        this.rawRecordStore = rawRecordStore;
    }

    /**
     * The pull requests of one repository merged from {@code from} to {@code to}, both days
     * included and read as UTC, oldest merge first.
     */
    @Transactional(readOnly = true)
    public List<RawRecord> mergedBetween(String repository, LocalDate from, LocalDate to) {
        return mergedPullRequestRecords.findLatestMergedBetween(
                PullRequestRecorder.DATA_SOURCE,
                GitHubRecordKind.PULL_REQUEST.recordKind(),
                repository + "#",
                from.atStartOfDay().toInstant(ZoneOffset.UTC),
                to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    /** The pull request with this number in this repository, if it was stored. */
    public Optional<RawRecord> find(String repository, int number) {
        return rawRecordStore.findLatest(
                PullRequestRecorder.DATA_SOURCE, GitHubRecordKind.PULL_REQUEST.recordKind(), repository + "#" + number);
    }
}
