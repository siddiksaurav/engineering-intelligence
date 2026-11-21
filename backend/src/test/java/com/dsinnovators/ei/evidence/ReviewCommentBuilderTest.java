package com.dsinnovators.ei.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.identity.Persons;
import com.dsinnovators.ei.source.RawRecordStore;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReviewCommentBuilderTest extends DatabaseTest {

    private static final Quarter BOT_LIST_QUARTER = Quarter.parse("2026-Q3");

    @Autowired
    private ReviewCommentBuilder builder;

    @Autowired
    private RawRecordStore rawRecords;

    @Autowired
    private Persons persons;

    private StoredRecordsFixture records;
    private long ada;
    private long grace;

    @BeforeEach
    void storeTheAuthorAndTheReviewer() {
        records = new StoredRecordsFixture(rawRecords, jdbc, persons);
        ada = records.person(1, "ada");
        grace = records.person(2, "grace");
    }

    private long rawRecordId(String recordKind, String externalId) {
        return jdbc.queryForObject(
                "select max(id) from raw_record where record_kind = ? and external_id = ?",
                Long.class,
                recordKind,
                externalId);
    }

    private static String canonical(String json) {
        return CanonicalJson.canonicalize(CanonicalJson.parse(json));
    }

    @Test
    void aHumanReviewersCommentIsAnItemDatedByTheMergeDay() {
        records.pullRequest(7, "ada", "User", 1, "2026-07-02T23:30:00Z");
        records.reviewComment(900, 7, "grace", "User", 2, false);

        BuildResult result = builder.build(BOT_LIST_QUARTER);

        assertEquals(List.of(), result.failures());
        assertEquals(1, result.items().size());
        BuiltItem item = result.items().get(0);
        assertEquals("review_comment_written:900", item.key());
        assertEquals(EvidenceType.REVIEW_COMMENT_WRITTEN, item.type());
        assertEquals(grace, item.personId());
        assertEquals(null, item.projectId());
        assertEquals(LocalDate.of(2026, 7, 2), item.occurredOn());
        assertEquals("https://github.com/opf/openproject/pull/7#discussion_r900", item.sourceUrl());
        assertEquals(canonical("""
                        {"pullRequest":{"externalId":"opf/openproject#7","number":7,"title":"PR 7",
                          "url":"https://github.com/opf/openproject/pull/7","mergedAt":"2026-07-02T23:30:00Z"},
                         "pullRequestAuthorPersonId":%d,
                         "comment":{"databaseId":900,"url":"https://github.com/opf/openproject/pull/7#discussion_r900",
                          "path":"app/models/work_package.rb","createdAt":"2026-06-20T10:00:00Z","isReply":false}}
                        """.formatted(ada)), CanonicalJson.canonicalize(item.fields()));
        assertEquals(
                List.of(
                        new EvidenceItemSource(
                                rawRecordId("review_comment", "opf/openproject#7/review_comment/900"),
                                SourceRole.REVIEW_COMMENT),
                        new EvidenceItemSource(
                                rawRecordId("pull_request", "opf/openproject#7"), SourceRole.PULL_REQUEST)),
                item.sources());
    }

    @Test
    void aBotsCommentAndTheAuthorsOwnCommentGiveNothingAndTheBotIsCounted() {
        records.pullRequest(7, "ada", "User", 1, "2026-07-02T08:00:00Z");
        records.reviewComment(900, 7, "copilot-pull-request-reviewer", "Bot", 99, false);
        records.reviewComment(901, 7, "ada", "User", 1, true);

        BuildResult result = builder.build(BOT_LIST_QUARTER);

        assertEquals(List.of(), result.items());
        assertEquals(List.of(), result.failures());
        assertEquals(1, result.botRecordsSkipped());
    }

    @Test
    void aReviewersReplyIsAnItemMarkedAsReply() {
        records.pullRequest(7, "ada", "User", 1, "2026-07-02T08:00:00Z");
        records.reviewComment(900, 7, "grace", "User", 2, true);

        BuiltItem item = builder.build(BOT_LIST_QUARTER).items().get(0);

        assertTrue(item.fields().path("comment").path("isReply").asBoolean());
    }

    @Test
    void aCommentOnABotsPullRequestIsAnItemWithoutAPullRequestAuthorPerson() {
        records.pullRequest(8, "dependabot[bot]", "Bot", 77, "2026-07-02T08:00:00Z");
        records.reviewComment(910, 8, "grace", "User", 2, false);

        BuiltItem item = builder.build(BOT_LIST_QUARTER).items().get(0);

        assertTrue(item.fields().path("pullRequestAuthorPersonId").isNull());
        assertEquals(grace, item.personId());
    }

    @Test
    void aHumanCommentAuthorWithNoPersonIsAFailureOfThatItem() {
        records.pullRequest(7, "ada", "User", 1, "2026-07-02T08:00:00Z");
        records.reviewComment(900, 7, "stranger", "User", 55, false);
        records.reviewComment(901, 7, "grace", "User", 2, false);

        BuildResult result = builder.build(BOT_LIST_QUARTER);

        assertEquals(
                List.of("review_comment_written:901"),
                result.items().stream().map(BuiltItem::key).toList());
        assertEquals(1, result.failures().size());
        assertTrue(
                result.failures().get(0).startsWith("review_comment_written:900"),
                result.failures().get(0));
    }

    @Test
    void aCommentWhosePullRequestIsNotStoredIsAFailureNamingTheComment() {
        records.reviewComment(900, 7, "grace", "User", 2, false);

        BuildResult result = builder.build(BOT_LIST_QUARTER);

        assertEquals(List.of(), result.items());
        assertEquals(1, result.failures().size());
        assertTrue(
                result.failures().get(0).startsWith("review_comment_written:900"),
                result.failures().get(0));
    }
}
