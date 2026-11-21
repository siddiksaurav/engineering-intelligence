package com.dsinnovators.ei.source.github;

import com.dsinnovators.ei.source.RawRecord;
import com.dsinnovators.ei.source.RawRecordStore;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Reads the inline {@code review_comment} records that the GitHub import stored; the latest version of each. */
@Component
public class StoredReviewComments {

    private final RawRecordStore rawRecordStore;

    StoredReviewComments(RawRecordStore rawRecordStore) {
        this.rawRecordStore = rawRecordStore;
    }

    /** Every stored inline review comment, oldest record first. */
    @Transactional(readOnly = true)
    public List<RawRecord> all() {
        return rawRecordStore.findLatestByKind(
                PullRequestRecorder.DATA_SOURCE, GitHubRecordKind.REVIEW_COMMENT.recordKind());
    }

    /**
     * The text of every stored inline review comment, oldest record first. A reply finds the comment it
     * answers here, whoever wrote it.
     */
    @Transactional(readOnly = true)
    public List<ReviewCommentText> texts() {
        return all().stream().map(ReviewCommentText::of).toList();
    }
}
