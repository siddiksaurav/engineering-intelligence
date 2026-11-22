package com.dsinnovators.ei.analytics;

import org.springframework.jdbc.core.JdbcTemplate;

/** Inserts the rows the analytics read, with the columns the tests do not care about filled in. */
final class AnalyticsRows {

    static final String REPOSITORY = "opf/openproject";

    private final JdbcTemplate jdbc;
    private int inserted;

    AnalyticsRows(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private int nextNumber() {
        inserted++;
        return inserted;
    }

    /** A person whose GitHub account has this database id. */
    long person(long accountId, String login) {
        long personId =
                jdbc.queryForObject("insert into person (display_name) values (?) returning id", Long.class, login);
        jdbc.update("""
                insert into person_identity (person_id, data_source_id, external_id, username)
                values (?, (select id from data_source where key = 'github'), ?, ?)
                """, personId, String.valueOf(accountId), login);
        return personId;
    }

    /** One stored version of a pull request written by a user account. */
    void pullRequest(int number, int version, long accountId, String login, String mergedAt) {
        storePullRequest(number, version, mergedAt, author(accountId, login, "User"));
    }

    void botPullRequest(int number, long accountId, String login, String mergedAt) {
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

    void link(int pullRequestNumber, String state) {
        int reference = nextNumber();
        Integer workPackageId = state.equals("resolved") ? reference : null;
        jdbc.update("""
                insert into pull_request_work_item (pull_request_external_id, work_package_id, reference, found_in, state)
                values (?, ?, ?, 'pull_request_body', ?)
                """, REPOSITORY + "#" + pullRequestNumber, workPackageId, "ref-" + reference, state);
    }

    /** A digest on the latest stored version of the pull request; returns the judgment id. */
    long digest(int pullRequestNumber, String workType, Long supersedesId) {
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
    long item(
            String type, long personId, String occurredOn, boolean withdrawn, Long supersedesId, int... pullRequests) {
        inserted++;
        long itemId = jdbc.queryForObject(
                """
                insert into evidence_item (item_key, evidence_type, person_id, occurred_on, source_url, fields,
                                           content_hash, withdrawn, supersedes_id, built_at)
                values (?, ?, ?, ?::date, 'https://example.org', '{}'::jsonb, ?, ?, ?, now())
                returning id
                """,
                Long.class,
                type + ":" + inserted,
                type,
                personId,
                occurredOn,
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

    /** A Feature work complexity judgment of an item; returns the judgment id. */
    long complexity(long itemId, String complexity, String tier, Long supersedesId) {
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
