package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dsinnovators.ei.DatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

class JudgmentTableTest extends DatabaseTest {

    private long rawRecordId;
    private long evidenceItemId;

    @BeforeEach
    void insertSubjects() {
        rawRecordId = jdbc.queryForObject("""
                insert into raw_record (data_source_id, record_kind, external_id, version, url, content_hash, payload, fetched_at)
                values ((select id from data_source where key = 'github'), 'pull_request', 'opf/openproject#1', 1,
                        'https://example.test/1', 'hash', '{}'::jsonb, now())
                returning id
                """, Long.class);
        long personId =
                jdbc.queryForObject("insert into person (display_name) values ('Ada') returning id", Long.class);
        evidenceItemId = jdbc.queryForObject("""
                insert into evidence_item (item_key, evidence_type, person_id, occurred_on, source_url,
                                           fields, content_hash, withdrawn, built_at)
                values ('test_change:opf/openproject#1', 'test_change', ?, date '2026-07-01', 'https://example.test/1',
                        '{}'::jsonb, 'hash', false, now())
                returning id
                """, Long.class, personId);
    }

    private long insertJudgment(
            String kind,
            Long rawRecord,
            Long evidenceItem,
            String judgedBy,
            String model,
            String inputHash,
            Long supersedesId) {
        return jdbc.queryForObject(
                """
                insert into judgment (kind, raw_record_id, evidence_item_id, value, tier, reason, facts,
                                      judged_by, model, prompt_version, input_hash, supersedes_id, judged_at)
                values (?, ?, ?, '{}'::jsonb, null, 'because', '[{"fact":"f","link":"https://example.test"}]'::jsonb,
                        ?, ?, 'pull-request-digest/v1', ?, ?, now())
                returning id
                """, Long.class, kind, rawRecord, evidenceItem, judgedBy, model, inputHash, supersedesId);
    }

    private long digestOfTheRecord(String inputHash) {
        return insertJudgment("pull_request_digest", rawRecordId, null, "model", "m", inputHash, null);
    }

    @Test
    void refusesARowWithBothSubjects() {
        assertThrows(
                DataAccessException.class,
                () -> insertJudgment("pull_request_digest", rawRecordId, evidenceItemId, "model", "m", "h", null));
    }

    @Test
    void refusesARowWithoutASubject() {
        assertThrows(
                DataAccessException.class,
                () -> insertJudgment("pull_request_digest", null, null, "model", "m", "h", null));
    }

    @Test
    void refusesAKindOutsideTheKnownKinds() {
        assertThrows(
                DataAccessException.class, () -> insertJudgment("mood", rawRecordId, null, "model", "m", "h", null));
    }

    @Test
    void refusesAModelJudgmentWithoutAModel() {
        assertThrows(
                DataAccessException.class,
                () -> insertJudgment("pull_request_digest", rawRecordId, null, "model", null, "h", null));
    }

    @Test
    void refusesATierOutsideT1ToT4() {
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                        insert into judgment (kind, evidence_item_id, value, tier, reason, facts, judged_by, model,
                                              prompt_version, input_hash, judged_at)
                        values ('item_tier', ?, '{}'::jsonb, 'T5', 'r', '[{"fact":"f","link":"l"}]'::jsonb,
                                'model', 'm', 'v', 'h', now())
                        """, evidenceItemId));
    }

    @Test
    void refusesABlankReason() {
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                        insert into judgment (kind, raw_record_id, value, reason, facts, judged_by, model,
                                              prompt_version, input_hash, judged_at)
                        values ('pull_request_digest', ?, '{}'::jsonb, '  ', '[{"fact":"f","link":"l"}]'::jsonb,
                                'model', 'm', 'v', 'h', now())
                        """, rawRecordId));
    }

    @Test
    void refusesAnEmptyFactList() {
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                        insert into judgment (kind, raw_record_id, value, reason, facts, judged_by, model,
                                              prompt_version, input_hash, judged_at)
                        values ('pull_request_digest', ?, '{}'::jsonb, 'r', '[]'::jsonb,
                                'model', 'm', 'v', 'h', now())
                        """, rawRecordId));
    }

    @Test
    void refusesASecondModelJudgmentOfTheSameKindAndInputHash() {
        digestOfTheRecord("same");

        assertThrows(DataAccessException.class, () -> digestOfTheRecord("same"));
    }

    @Test
    void acceptsTheSameInputHashInAnotherKind() {
        digestOfTheRecord("same");

        insertJudgment("item_tier", null, evidenceItemId, "model", "m", "same", null);

        assertEquals(2, jdbc.queryForObject("select count(*) from judgment", Integer.class));
    }

    @Test
    void aSecondRowCannotSupersedeTheSameRow() {
        long first = digestOfTheRecord("one");
        insertJudgment("pull_request_digest", rawRecordId, null, "model", "m", "two", first);

        assertThrows(
                DataAccessException.class,
                () -> insertJudgment("pull_request_digest", rawRecordId, null, "model", "m", "three", first));
    }

    @Test
    void anUpdateOrDeleteOfAJudgmentIsRefused() {
        long id = digestOfTheRecord("one");

        assertThrows(DataAccessException.class, () -> jdbc.update("update judgment set reason = 'x' where id = ?", id));
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from judgment where id = ?", id));
        assertEquals(1, jdbc.queryForObject("select count(*) from judgment", Integer.class));
    }

    @Test
    void theNewEvidenceTypesAndTheDiffSourceRoleAreAccepted() {
        for (String type :
                new String[] {"test_change", "refactor_performance", "security", "infra_release", "documentation"}) {
            jdbc.update("""
                    insert into evidence_item (item_key, evidence_type, person_id, occurred_on, source_url,
                                               fields, content_hash, withdrawn, built_at)
                    select ?, ?, person_id, occurred_on, 'u', '{}'::jsonb, 'h', false, now()
                    from evidence_item where id = ?
                    """, type + ":1", type, evidenceItemId);
        }
        jdbc.update(
                "insert into evidence_item_source (evidence_item_id, raw_record_id, role) values (?, ?, 'pull_request_diff')",
                evidenceItemId,
                rawRecordId);

        assertEquals(6, jdbc.queryForObject("select count(*) from evidence_item", Integer.class));
    }

    @Test
    void theThreeNewSettingsAreSeededFromQuarter2026Q2() {
        for (String key :
                new String[] {"judgment.diff_excluded_paths", "evidence.test_paths", "evidence.test_kind_tiers"}) {
            assertEquals(
                    "2026-Q2",
                    jdbc.queryForObject("select valid_from_quarter from setting where key = ?", String.class, key));
        }
    }
}
