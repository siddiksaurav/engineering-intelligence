package com.dsinnovators.ei.judgment;

import org.springframework.jdbc.core.JdbcTemplate;

/** Inserts the rows a judgment is about, for tests that need a real subject. */
final class JudgmentSubjectRows {

    private JudgmentSubjectRows() {}

    static long insertPullRequestRecord(JdbcTemplate jdbc, int version) {
        return jdbc.queryForObject("""
                insert into raw_record (data_source_id, record_kind, external_id, version, url, content_hash, payload, fetched_at)
                values ((select id from data_source where key = 'github'), 'pull_request', 'opf/openproject#1', ?,
                        'https://example.test/1', 'hash' || ?, '{}'::jsonb, now())
                returning id
                """, Long.class, version, version);
    }

    static long insertEvidenceItem(JdbcTemplate jdbc) {
        long personId =
                jdbc.queryForObject("insert into person (display_name) values ('Ada') returning id", Long.class);
        return jdbc.queryForObject("""
                insert into evidence_item (item_key, evidence_type, person_id, occurred_on, source_url,
                                           fields, content_hash, withdrawn, built_at)
                values ('test_change:opf/openproject#1', 'test_change', ?, date '2026-07-01', 'https://example.test/1',
                        '{}'::jsonb, 'hash', false, now())
                returning id
                """, Long.class, personId);
    }
}
