package com.dsinnovators.ei.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.CanonicalJson;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EvidenceItemStoreTest extends DatabaseTest {

    private static final String KEY = "bug_fix:100:1";

    @Autowired
    private EvidenceItemStore store;

    @Autowired
    private EvidenceItemRepository repository;

    private long personId;
    private long firstRawRecordId;

    @BeforeEach
    void createPersonAndRawRecord() {
        personId = jdbc.queryForObject("insert into person (display_name) values ('Ada') returning id", Long.class);
        firstRawRecordId = rawRecord(1);
    }

    private long rawRecord(int version) {
        return jdbc.queryForObject("""
                insert into raw_record (data_source_id, record_kind, external_id, version, url, content_hash, payload, fetched_at)
                values ((select id from data_source where key = 'github'), 'pull_request', '1', ?,
                        'https://example.test/1', 'hash', '{}'::jsonb, now())
                returning id
                """, Long.class, version);
    }

    private BuiltItem item(String fieldsJson, long rawRecordId) {
        return new BuiltItem(
                KEY,
                EvidenceType.BUG_FIX,
                personId,
                null,
                LocalDate.parse("2026-07-03"),
                "https://example.test/bug/100",
                CanonicalJson.parse(fieldsJson),
                List.of(new EvidenceItemSource(rawRecordId, SourceRole.PULL_REQUEST)));
    }

    private int rowCount() {
        return jdbc.queryForObject("select count(*) from evidence_item", Integer.class);
    }

    @Test
    void writingTheSameItemTwiceWritesOneRow() {
        assertEquals(WriteOutcome.CREATED, store.write(item("{\"a\":1,\"b\":2}", firstRawRecordId)));
        assertEquals(WriteOutcome.UNCHANGED, store.write(item("{\"b\":2,\"a\":1}", firstRawRecordId)));

        assertEquals(1, rowCount());
        EvidenceItem written = repository.findCurrentByKey(KEY).orElseThrow();
        assertEquals(personId, written.getPersonId());
        assertEquals(Set.of(new EvidenceItemSource(firstRawRecordId, SourceRole.PULL_REQUEST)), written.getSources());
    }

    @Test
    void aChangedItemSupersedesTheCurrentRow() {
        store.write(item("{\"a\":1}", firstRawRecordId));
        long firstRow = repository.findCurrentByKey(KEY).orElseThrow().getId();

        assertEquals(WriteOutcome.SUPERSEDED, store.write(item("{\"a\":2}", firstRawRecordId)));

        EvidenceItem current = repository.findCurrentByKey(KEY).orElseThrow();
        assertEquals(2, rowCount());
        assertEquals(firstRow, current.getSupersedesId());
        assertEquals(CanonicalJson.parse("{\"a\":2}"), current.getFields());
    }

    @Test
    void aNewSourceVersionSupersedes() {
        store.write(item("{\"a\":1}", firstRawRecordId));

        assertEquals(WriteOutcome.SUPERSEDED, store.write(item("{\"a\":1}", rawRecord(2))));

        assertEquals(2, rowCount());
    }

    @Test
    void withdrawingWritesARowThatEndsTheItem() {
        store.write(item("{\"a\":1}", firstRawRecordId));
        long firstRow = repository.findCurrentByKey(KEY).orElseThrow().getId();

        store.withdraw(KEY);

        assertEquals(2, rowCount());
        assertEquals(Optional.empty(), repository.findCurrentByKey(KEY));
        EvidenceItem ending = repository.findLatestRowOfKey(KEY).orElseThrow();
        assertTrue(ending.isWithdrawn());
        assertEquals(firstRow, ending.getSupersedesId());
        assertEquals(Set.of(), store.currentKeys());
    }

    @Test
    void anItemWrittenAfterItsWithdrawalStartsAgainFromTheWithdrawnRow() {
        store.write(item("{\"a\":1}", firstRawRecordId));
        store.withdraw(KEY);
        long withdrawnRow = repository.findLatestRowOfKey(KEY).orElseThrow().getId();

        assertEquals(WriteOutcome.SUPERSEDED, store.write(item("{\"a\":1}", firstRawRecordId)));

        EvidenceItem current = repository.findCurrentByKey(KEY).orElseThrow();
        assertEquals(withdrawnRow, current.getSupersedesId());
        assertEquals(Set.of(KEY), store.currentKeys());
    }

    @Test
    void withdrawingAKeyWithNoCurrentItemFailsNamingTheKey() {
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> store.withdraw(KEY));

        assertEquals("No current evidence item to withdraw for key " + KEY, failure.getMessage());
    }
}
