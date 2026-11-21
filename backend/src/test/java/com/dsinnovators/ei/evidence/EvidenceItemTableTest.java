package com.dsinnovators.ei.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dsinnovators.ei.DatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

class EvidenceItemTableTest extends DatabaseTest {

    private long insertItem(String itemKey, Long supersedesId) {
        long personId = jdbc.queryForObject("select coalesce((select min(id) from person), -1)", Long.class);
        if (personId < 0) {
            personId = jdbc.queryForObject("insert into person (display_name) values ('Ada') returning id", Long.class);
        }
        return jdbc.queryForObject("""
                insert into evidence_item (item_key, evidence_type, person_id, occurred_on, source_url,
                                           fields, content_hash, withdrawn, supersedes_id, built_at)
                values (?, 'bug_fix', ?, date '2026-07-01', 'https://example.test/1',
                        '{}'::jsonb, 'hash', false, ?, now())
                returning id
                """, Long.class, itemKey, personId, supersedesId);
    }

    @Test
    void anUpdateOfAnEvidenceItemIsRefused() {
        long id = insertItem("bug_fix:1:1", null);

        assertThrows(
                DataAccessException.class,
                () -> jdbc.update("update evidence_item set source_url = 'x' where id = ?", id));
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from evidence_item where id = ?", id));
        assertEquals(1, jdbc.queryForObject("select count(*) from evidence_item", Integer.class));
    }

    @Test
    void aSecondRowCannotSupersedeTheSameRow() {
        long first = insertItem("bug_fix:2:1", null);
        insertItem("bug_fix:2:1", first);

        assertThrows(DataAccessException.class, () -> insertItem("bug_fix:2:1", first));
    }

    @Test
    void anUnknownEvidenceTypeIsRefused() {
        long id = insertItem("bug_fix:3:1", null);

        assertThrows(DataAccessException.class, () -> jdbc.update("""
                        insert into evidence_item (item_key, evidence_type, person_id, occurred_on, source_url,
                                                   fields, content_hash, withdrawn, built_at)
                        select 'k', 'refactor', person_id, occurred_on, 'u', '{}'::jsonb, 'h', false, now()
                        from evidence_item where id = ?
                        """, id));
    }
}
