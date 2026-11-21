package com.dsinnovators.ei.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.CanonicalJson;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EvidenceItemRepositoryTest extends DatabaseTest {

    @Autowired
    private EvidenceItemRepository repository;

    private long personId;

    @BeforeEach
    void createPerson() {
        personId = jdbc.queryForObject("insert into person (display_name) values ('Ada') returning id", Long.class);
    }

    private long rawRecordId() {
        return jdbc.queryForObject("""
                insert into raw_record (data_source_id, record_kind, external_id, version, url, content_hash, payload, fetched_at)
                values ((select id from data_source where key = 'github'), 'pull_request', '1', 1,
                        'https://example.test/1', 'hash', '{}'::jsonb, now())
                returning id
                """, Long.class);
    }

    private EvidenceItem save(String key, LocalDate date, boolean withdrawn, Long supersedesId) {
        return repository.save(new EvidenceItem(
                key,
                EvidenceType.BUG_FIX,
                personId,
                null,
                date,
                "https://example.test/" + key,
                CanonicalJson.parse("{\"b\":1,\"a\":2}"),
                "hash",
                withdrawn,
                supersedesId,
                Instant.parse("2026-07-20T10:00:00Z"),
                Set.of()));
    }

    @Test
    void theCurrentRowOfAKeyIsTheOneNoRowSupersedes() {
        EvidenceItem first = save("bug_fix:1", LocalDate.parse("2026-07-01"), false, null);
        EvidenceItem second = save("bug_fix:1", LocalDate.parse("2026-07-01"), false, first.getId());

        assertEquals(
                second.getId(),
                repository.findCurrentByKey("bug_fix:1").orElseThrow().getId());
        assertEquals(
                second.getId(),
                repository.findLatestRowOfKey("bug_fix:1").orElseThrow().getId());

        EvidenceItem withdrawn = save("bug_fix:1", LocalDate.parse("2026-07-01"), true, second.getId());

        assertEquals(Optional.empty(), repository.findCurrentByKey("bug_fix:1"));
        assertEquals(
                withdrawn.getId(),
                repository.findLatestRowOfKey("bug_fix:1").orElseThrow().getId());
        assertEquals(List.of(), repository.findAllCurrent());
    }

    @Test
    void theCurrentRowsOfAPersonAreNewestFirst() {
        EvidenceItem older = save("bug_fix:1", LocalDate.parse("2026-07-01"), false, null);
        EvidenceItem newer = save("bug_fix:2", LocalDate.parse("2026-07-09"), false, null);

        assertEquals(
                List.of(newer.getId(), older.getId()),
                repository.findCurrentOfPerson(personId).stream()
                        .map(EvidenceItem::getId)
                        .toList());
        assertEquals(List.of(), repository.findCurrentOfPerson(personId + 1));
    }

    @Test
    void anItemKeepsItsFieldsAndItsSourceVersions() {
        long rawRecordId = rawRecordId();
        repository.save(new EvidenceItem(
                "bug_fix:3",
                EvidenceType.BUG_FIX,
                personId,
                null,
                LocalDate.parse("2026-07-01"),
                "https://example.test/3",
                CanonicalJson.parse("{\"b\":1,\"a\":2}"),
                "hash",
                false,
                null,
                Instant.parse("2026-07-20T10:00:00Z"),
                Set.of(new EvidenceItemSource(rawRecordId, SourceRole.PULL_REQUEST))));

        EvidenceItem read = repository.findCurrentByKey("bug_fix:3").orElseThrow();

        assertEquals(CanonicalJson.parse("{\"a\":2,\"b\":1}"), read.getFields());
        assertEquals(Set.of(new EvidenceItemSource(rawRecordId, SourceRole.PULL_REQUEST)), read.getSources());
        assertEquals("pull_request", jdbc.queryForObject("select role from evidence_item_source", String.class));
        assertEquals(Instant.parse("2026-07-20T10:00:00Z"), read.getBuiltAt());
    }
}
