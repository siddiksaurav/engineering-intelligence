package com.dsinnovators.ei.source.github;

import com.dsinnovators.ei.source.RawRecord;
import com.dsinnovators.ei.source.RawRecordStore;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Reads the inline {@code review_comment} records that the GitHub import stored. */
@Component
public class StoredReviewComments {

    private final RawRecordStore rawRecordStore;

    StoredReviewComments(RawRecordStore rawRecordStore) {
        this.rawRecordStore = rawRecordStore;
    }

    /** Every stored inline review comment, the latest version of each, oldest record first. */
    @Transactional(readOnly = true)
    public List<RawRecord> all() {
        return rawRecordStore.findLatestByKind(
                PullRequestRecorder.DATA_SOURCE, GitHubRecordKind.REVIEW_COMMENT.recordKind());
    }

    /**
     * The text of one exact version of a review comment, the one an evidence item was built from, even
     * when the comment was edited since.
     *
     * @throws IllegalStateException when no such record is stored, it is not a review comment, or its
     *     content cannot be read; the message names the record
     */
    @Transactional(readOnly = true)
    public ReviewCommentText textOfRecord(long rawRecordId) {
        RawRecord record = rawRecordStore
                .findById(rawRecordId)
                .orElseThrow(() -> new IllegalStateException("No raw record " + rawRecordId + " is stored"));
        if (!record.getRecordKind().equals(GitHubRecordKind.REVIEW_COMMENT.recordKind())) {
            throw new IllegalStateException("The raw record " + rawRecordId + " is a " + record.getRecordKind()
                    + ", not a " + GitHubRecordKind.REVIEW_COMMENT.recordKind());
        }
        return ReviewCommentText.of(record);
    }

    /**
     * The latest text of the review comment with this database id; a reply finds the comment it
     * answers here, whoever wrote it.
     *
     * @throws IllegalStateException when the stored record cannot be read; the message names it
     */
    @Transactional(readOnly = true)
    public Optional<ReviewCommentText> latestTextOf(long databaseId) {
        return rawRecordStore
                .findLatest(
                        PullRequestRecorder.DATA_SOURCE,
                        GitHubRecordKind.REVIEW_COMMENT.recordKind(),
                        String.valueOf(databaseId))
                .map(ReviewCommentText::of);
    }
}
