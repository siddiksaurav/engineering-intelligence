package com.dsinnovators.ei.analytics;

import org.springframework.jdbc.core.JdbcTemplate;

/** Inserts the rows the analytics read, with the columns the tests do not care about filled in. */
public final class AnalyticsRows {

    public static final String REPOSITORY = "opf/openproject";

    private final JdbcTemplate jdbc;
    private int inserted;

    public AnalyticsRows(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private int nextNumber() {
        inserted++;
        return inserted;
    }

    /** A person whose GitHub account has this database id. */
    public long person(long accountId, String login) {
        long personId =
                jdbc.queryForObject("insert into person (display_name) values (?) returning id", Long.class, login);
        jdbc.update("""
                insert into person_identity (person_id, data_source_id, external_id, username)
                values (?, (select id from data_source where key = 'github'), ?, ?)
                """, personId, String.valueOf(accountId), login);
        return personId;
    }

    /** One stored version of a pull request written by a user account. */
    public void pullRequest(int number, int version, long accountId, String login, String mergedAt) {
        pullRequestOfType(number, version, accountId, login, "User", mergedAt);
    }

    /** One stored version of a pull request whose author account GitHub types as given. */
    public void pullRequestOfType(
            int number, int version, long accountId, String login, String typename, String mergedAt) {
        storePullRequest(number, version, mergedAt, author(accountId, login, typename));
    }

    public void botPullRequest(int number, long accountId, String login, String mergedAt) {
        storePullRequest(number, 1, mergedAt, author(accountId, login, "Bot"));
    }

    private static String author(long accountId, String login, String typename) {
        return "{\"login\":\"" + login + "\",\"__typename\":\"" + typename + "\",\"databaseId\":" + accountId + "}";
    }

    private void storePullRequest(int number, int version, String mergedAt, String author) {
        inserted++;
        jdbc.update(
                """
                insert into raw_record (data_source_id, record_kind, external_id, version, url, content_hash, payload, fetched_at)
                values ((select id from data_source where key = 'github'), 'pull_request', ?, ?, ?, ?, ?::jsonb, now())
                """,
                REPOSITORY + "#" + number,
                version,
                "https://github.com/" + REPOSITORY + "/pull/" + number,
                "hash-" + inserted,
                "{\"number\":" + number + ",\"mergedAt\":\"" + mergedAt + "\",\"author\":" + author + "}");
    }

    public void link(int pullRequestNumber, String state) {
        int reference = nextNumber();
        Integer workPackageId = state.equals("resolved") ? reference : null;
        jdbc.update("""
                insert into pull_request_work_item (pull_request_external_id, work_package_id, reference, found_in, state)
                values (?, ?, ?, 'pull_request_body', ?)
                """, REPOSITORY + "#" + pullRequestNumber, workPackageId, "ref-" + reference, state);
    }

    /** A digest on the latest stored version of the pull request; returns the judgment id. */
    public long digest(int pullRequestNumber, String workType, Long supersedesId) {
        long recordId = jdbc.queryForObject(
                "select max(id) from raw_record where record_kind = 'pull_request' and external_id = ?",
                Long.class,
                REPOSITORY + "#" + pullRequestNumber);
        return judgment(
                "pull_request_digest",
                "raw_record_id",
                recordId,
                "{\"workType\":\"" + workType + "\"}",
                null,
                supersedesId);
    }

    /** A current item built from the given pull requests; returns its id. */
    public long item(
            String type, long personId, String occurredOn, boolean withdrawn, Long supersedesId, int... pullRequests) {
        return itemWithFields(type, null, personId, occurredOn, "{}", withdrawn, supersedesId, pullRequests);
    }

    /** A current Test change item of the pull request, keyed as the evidence builder keys it. */
    public long testChange(long personId, int pullRequestNumber, String occurredOn) {
        return itemWithFields(
                "test_change",
                "test_change:" + REPOSITORY + "#" + pullRequestNumber,
                personId,
                occurredOn,
                "{}",
                false,
                null,
                pullRequestNumber);
    }

    /** A current Review comment written item on the pull request; the author is null for a bot's pull request. */
    public long reviewComment(long reviewerPersonId, int pullRequestNumber, Long authorPersonId, String occurredOn) {
        String fields = "{\"pullRequest\":{\"externalId\":\"" + REPOSITORY + "#" + pullRequestNumber + "\",\"number\":"
                + pullRequestNumber + "},\"pullRequestAuthorPersonId\":" + authorPersonId + "}";
        return itemWithFields(
                "review_comment_written", null, reviewerPersonId, occurredOn, fields, false, null, pullRequestNumber);
    }

    private long itemWithFields(
            String type,
            String itemKey,
            long personId,
            String occurredOn,
            String fields,
            boolean withdrawn,
            Long supersedesId,
            int... pullRequests) {
        inserted++;
        long itemId = jdbc.queryForObject(
                """
                insert into evidence_item (item_key, evidence_type, person_id, occurred_on, source_url, fields,
                                           content_hash, withdrawn, supersedes_id, built_at)
                values (?, ?, ?, ?::date, 'https://example.org', ?::jsonb, ?, ?, ?, now())
                returning id
                """,
                Long.class,
                itemKey == null ? type + ":" + inserted : itemKey,
                type,
                personId,
                occurredOn,
                fields,
                "item-hash-" + inserted,
                withdrawn,
                supersedesId);
        for (int number : pullRequests) {
            jdbc.update("""
                    insert into evidence_item_source (evidence_item_id, raw_record_id, role)
                    values (?, (select max(id) from raw_record where record_kind = 'pull_request' and external_id = ?),
                            'pull_request')
                    """, itemId, REPOSITORY + "#" + number);
        }
        return itemId;
    }

    /** One stored review of a pull request by a user account. */
    public void review(int pullRequestNumber, long accountId, String login, String state) {
        inserted++;
        jdbc.update(
                """
                insert into raw_record (data_source_id, record_kind, external_id, version, url, content_hash, payload, fetched_at)
                values ((select id from data_source where key = 'github'), 'review', ?, 1, ?, ?, ?::jsonb, now())
                """,
                "review-" + inserted,
                "https://github.com/" + REPOSITORY + "/pull/" + pullRequestNumber + "#review-" + inserted,
                "hash-" + inserted,
                "{\"state\":\"" + state + "\",\"author\":" + author(accountId, login, "User")
                        + ",\"pullRequest\":{\"number\":" + pullRequestNumber
                        + ",\"repository\":{\"nameWithOwner\":\"" + REPOSITORY + "\"}}}");
    }

    /** A Review comment judgment of an item; the tier is null when the comment is not substantive. */
    public long commentJudgment(long itemId, String commentType, boolean substantive, String tier, Long supersedesId) {
        return judgment(
                "review_comment",
                "evidence_item_id",
                itemId,
                "{\"commentType\":\"" + commentType + "\",\"substantive\":" + substantive + "}",
                tier,
                supersedesId);
    }

    /** The tier of a Test change item; returns the judgment id. */
    public long itemTier(long itemId, String tier) {
        return judgment("item_tier", "evidence_item_id", itemId, "{}", tier, null);
    }

    /** A Feature work complexity judgment of an item; returns the judgment id. */
    public long complexity(long itemId, String complexity, String tier, Long supersedesId) {
        return judgment(
                "feature_work_complexity",
                "evidence_item_id",
                itemId,
                "{\"complexity\":\"" + complexity + "\"}",
                tier,
                supersedesId);
    }

    private long judgment(
            String kind, String subjectColumn, long subjectId, String value, String tier, Long supersedesId) {
        inserted++;
        return jdbc.queryForObject(
                "insert into judgment (kind, " + subjectColumn + """
                , value, tier, reason, facts, judged_by, model, prompt_version, input_hash, supersedes_id, judged_at)
                values (?, ?, ?::jsonb, ?, 'A reason', '[{"fact":"A fact","url":"https://example.org"}]'::jsonb,
                        'model', 'fake-model', 'prompt/v1', ?, ?, now())
                returning id
                """,
                Long.class,
                kind,
                subjectId,
                value,
                tier,
                "input-" + inserted,
                supersedesId);
    }
}
