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
}
