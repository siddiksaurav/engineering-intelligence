package com.dsinnovators.ei.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.identity.Persons;
import com.dsinnovators.ei.judgment.ReviewCommentToJudge;
import com.dsinnovators.ei.source.RawRecordStore;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReviewCommentItemsForJudgmentTest extends DatabaseTest {

    private static final Quarter BOT_LIST_QUARTER = Quarter.parse("2026-Q3");

    @Autowired
    private ReviewCommentItemsForJudgment reviewCommentItems;

    @Autowired
    private ReviewCommentBuilder builder;

    @Autowired
    private EvidenceItemStore itemStore;

    @Autowired
    private EvidenceItemRepository items;

    @Autowired
    private RawRecordStore rawRecords;

    @Autowired
    private Persons persons;

    private StoredRecordsFixture records;
    private long ada;

    @BeforeEach
    void storeThePullRequestAndItsAuthorAndReviewer() {
        records = new StoredRecordsFixture(rawRecords, jdbc, persons);
        ada = records.person(1, "ada");
        records.person(2, "grace");
        records.pullRequest(7, "ada", "User", 1, "2026-07-02T08:00:00Z");
    }

    private long recordId(String recordKind, String externalId) {
        return jdbc.queryForObject(
                "select max(id) from raw_record where record_kind = ? and external_id = ?",
                Long.class,
                recordKind,
                externalId);
    }

    private void buildAndWriteTheItems() {
        builder.build(BOT_LIST_QUARTER).items().forEach(itemStore::write);
    }

    private long itemRowId(long commentDatabaseId) {
        return items.findCurrentByKey("review_comment_written:" + commentDatabaseId)
                .orElseThrow()
                .getId();
    }

    @Test
    void handsOverEveryCurrentReviewCommentItem() {
        records.reviewComment(900, 7, "grace", "User", 2, false);
        records.reviewComment(901, 7, "grace", "User", 2, true);
        buildAndWriteTheItems();

        List<ReviewCommentToJudge> handedOver = reviewCommentItems.current();

        long pullRequestRecord = recordId("pull_request", "opf/openproject#7");
        assertEquals(
                List.of(
                        new ReviewCommentToJudge(
                                itemRowId(900),
                                "review_comment_written:900",
                                recordId("review_comment", "opf/openproject#7/review_comment/900"),
                                pullRequestRecord,
                                "opf/openproject#7"),
                        new ReviewCommentToJudge(
                                itemRowId(901),
                                "review_comment_written:901",
                                recordId("review_comment", "opf/openproject#7/review_comment/901"),
                                pullRequestRecord,
                                "opf/openproject#7")),
                handedOver);
    }

    @Test
    void leavesOutTheItemsOfOtherTypesTheWithdrawnAndTheSupersededRows() {
        records.reviewComment(900, 7, "grace", "User", 2, false);
        records.reviewComment(901, 7, "grace", "User", 2, false);
        buildAndWriteTheItems();
        buildAndWriteTheItems();
        itemStore.withdraw("review_comment_written:901");
        long pullRequestRecord = recordId("pull_request", "opf/openproject#7");
        itemStore.write(new BuiltItem(
                "bug_fix:pull_request:opf/openproject#7",
                EvidenceType.BUG_FIX,
                ada,
                null,
                LocalDate.parse("2026-07-02"),
                "https://github.com/opf/openproject/pull/7",
                CanonicalJson.parse("{\"noWorkItem\":true,\"pullRequests\":[]}"),
                List.of(new EvidenceItemSource(pullRequestRecord, SourceRole.PULL_REQUEST))));

        List<ReviewCommentToJudge> handedOver = reviewCommentItems.current();

        assertEquals(1, handedOver.size());
        assertEquals("review_comment_written:900", handedOver.getFirst().key());
        assertEquals(itemRowId(900), handedOver.getFirst().itemRowId());
    }
}
