package com.dsinnovators.ei.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.source.StoreResult.Outcome;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

class RawRecordStoreTest extends DatabaseTest {

    private static final String GH = "github";
    private static final String PR = "pull_request";
    private static final String URL = "https://github.com/opf/openproject/pull/101";

    @Autowired
    private RawRecordStore store;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private EntityManager entityManager;

    private static JsonNode p(String json) {
        return CanonicalJson.parse(json);
    }

    private int rowCount() {
        return jdbc.queryForObject("select count(*) from raw_record", Integer.class);
    }

    @Test
    void theFirstCopyIsVersionOne() {
        Instant before = Instant.now().minusMillis(1);
        StoreResult result = store.store(GH, PR, "101", URL, p("{\"title\":\"A\"}"));
        Instant after = Instant.now().plusMillis(1);

        assertEquals(Outcome.CREATED, result.outcome());
        RawRecord record = result.record();
        assertEquals(1, record.getVersion());
        assertEquals(GH, record.getDataSource().getKey());
        assertEquals(PR, record.getRecordKind());
        assertEquals("101", record.getExternalId());
        assertEquals(URL, record.getUrl());
        assertEquals(CanonicalJson.sha256Hex(p("{\"title\":\"A\"}")), record.getContentHash());
        assertEquals(p("{\"title\":\"A\"}"), record.getPayload());
        assertFalse(record.getFetchedAt().isBefore(before));
        assertFalse(record.getFetchedAt().isAfter(after));
        assertEquals(1, rowCount());
    }

    @Test
    void theSamePayloadAgainWritesNothingWhateverTheKeyOrder() {
        store.store(GH, PR, "102", URL, p("{\"a\":1,\"b\":2}"));
        StoreResult again = store.store(GH, PR, "102", URL, p("{\"b\":2,\"a\":1}"));

        assertEquals(Outcome.UNCHANGED, again.outcome());
        assertEquals(1, again.record().getVersion());
        assertEquals(1, rowCount());
    }

    @Test
    void aChangedPayloadIsANewVersionAndTheOldRowStays() {
        store.store(GH, PR, "103", URL, p("{\"title\":\"A\"}"));
        StoreResult second = store.store(GH, PR, "103", URL, p("{\"title\":\"B\"}"));

        assertEquals(Outcome.NEW_VERSION, second.outcome());
        assertEquals(2, second.record().getVersion());
        List<RawRecord> versions = store.findVersions(GH, PR, "103");
        assertEquals(List.of(1, 2), versions.stream().map(RawRecord::getVersion).toList());
        assertEquals(
                List.of("A", "B"),
                versions.stream().map(v -> v.getPayload().get("title").asString()).toList());
    }

    @Test
    void contentThatChangesBackIsANewVersionAndTheCurrentOne() {
        store.store(GH, PR, "104", URL, p("{\"title\":\"A\"}"));
        store.store(GH, PR, "104", URL, p("{\"title\":\"B\"}"));
        StoreResult third = store.store(GH, PR, "104", URL, p("{\"title\":\"A\"}"));

        assertEquals(Outcome.NEW_VERSION, third.outcome());
        assertEquals(3, rowCount());
        RawRecord latest = store.findLatest(GH, PR, "104").orElseThrow();
        assertEquals(3, latest.getVersion());
        assertEquals("A", latest.getPayload().get("title").asString());
    }

    @Test
    void theSameExternalIdUnderAnotherKindOrDataSourceIsASeparateRecord() {
        store.store(GH, PR, "105", URL, p("{\"n\":1}"));
        StoreResult otherKind = store.store(GH, "review", "105", URL, p("{\"n\":2}"));
        StoreResult otherSource = store.store("openproject", PR, "105", URL, p("{\"n\":3}"));

        assertEquals(Outcome.CREATED, otherKind.outcome());
        assertEquals(1, otherKind.record().getVersion());
        assertEquals(Outcome.CREATED, otherSource.outcome());
        assertEquals(1, otherSource.record().getVersion());
        assertEquals(3, rowCount());
    }

    @Test
    void anUnknownRecordHasNoLatestVersion() {
        assertTrue(store.findLatest(GH, PR, "none").isEmpty());
        assertTrue(store.findVersions(GH, PR, "none").isEmpty());
    }

    @Test
    void latestByKindGivesTheCurrentCopyOfEachRecordOfThatKind() {
        store.store(GH, PR, "101", URL, p("{\"title\":\"A\"}"));
        store.store(GH, PR, "103", URL, p("{\"title\":\"A\"}"));
        store.store(GH, PR, "103", URL, p("{\"title\":\"B\"}"));
        store.store(GH, "review", "900", URL, p("{}"));
        store.store("openproject", PR, "101", URL, p("{}"));

        List<RawRecord> latest = store.findLatestByKind(GH, PR);

        assertEquals(
                List.of("101:1", "103:2"),
                latest.stream().map(r -> r.getExternalId() + ":" + r.getVersion()).sorted().toList());
    }

    @Test
    void anUnknownDataSourceIsRejectedAndNothingIsWritten() {
        assertThrows(IllegalArgumentException.class, () -> store.store("gitlab", PR, "1", URL, p("{}")));
        assertEquals(0, rowCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void blankArgumentsAreRejectedAndNothingIsWritten(String blank) {
        assertThrows(IllegalArgumentException.class, () -> store.store(GH, blank, "1", URL, p("{}")));
        assertThrows(IllegalArgumentException.class, () -> store.store(GH, PR, blank, URL, p("{}")));
        assertThrows(IllegalArgumentException.class, () -> store.store(GH, PR, "1", blank, p("{}")));
        assertEquals(0, rowCount());
    }

    @Test
    void nullArgumentsAreRejectedAndNothingIsWritten() {
        assertThrows(IllegalArgumentException.class, () -> store.store(null, PR, "1", URL, p("{}")));
        assertThrows(IllegalArgumentException.class, () -> store.store(GH, null, "1", URL, p("{}")));
        assertThrows(IllegalArgumentException.class, () -> store.store(GH, PR, null, URL, p("{}")));
        assertThrows(IllegalArgumentException.class, () -> store.store(GH, PR, "1", null, p("{}")));
        assertThrows(IllegalArgumentException.class, () -> store.store(GH, PR, "1", URL, null));
        assertEquals(0, rowCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[1,2]", "\"text\"", "null"})
    void aPayloadThatIsNotAnObjectIsStoredLikeAnyOther(String json) {
        StoreResult result = store.store(GH, PR, "106", URL, p(json));

        assertEquals(Outcome.CREATED, result.outcome());
        assertEquals(p(json), store.findLatest(GH, PR, "106").orElseThrow().getPayload());
    }

    @Test
    void aChangeToALoadedRecordIsNeverWritten() {
        long id = store.store(GH, PR, "107", URL, p("{\"title\":\"A\"}")).record().getId();

        transaction.executeWithoutResult(status -> {
            RawRecord loaded = entityManager.find(RawRecord.class, id);
            ReflectionTestUtils.setField(loaded, "url", "changed");
            entityManager.flush();
        });

        assertEquals(URL, jdbc.queryForObject("select url from raw_record where id = ?", String.class, id));
    }

    @Test
    void aStoredRecordCannotBeDeletedThroughJpa() {
        long id = store.store(GH, PR, "108", URL, p("{\"title\":\"A\"}")).record().getId();

        assertThrows(RuntimeException.class, () -> transaction.executeWithoutResult(status -> {
            entityManager.remove(entityManager.find(RawRecord.class, id));
            entityManager.flush();
        }));

        assertEquals(1, rowCount());
    }

    @Test
    void theDatabaseRefusesAnUpdateOrDeleteOfARawRecord() {
        long id = store.store(GH, PR, "109", URL, p("{\"title\":\"A\"}")).record().getId();

        DataAccessException update = assertThrows(
                DataAccessException.class, () -> jdbc.update("update raw_record set url = 'changed' where id = ?", id));
        assertTrue(update.getMessage().contains("raw_record is never updated or deleted"));
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from raw_record where id = ?", id));

        assertEquals(URL, jdbc.queryForObject("select url from raw_record where id = ?", String.class, id));
    }

    @Test
    void aStoredPayloadHashesToItsContentHashAndStoresAsUnchanged() {
        JsonNode payload = p("{\"n\":1E+2,\"m\":0.0000001,\"k\":1.50,\"big\":1e21,\"i\":7}");
        RawRecord written = store.store(GH, PR, "110", URL, payload).record();

        RawRecord read = store.findLatest(GH, PR, "110").orElseThrow();
        assertEquals(written.getContentHash(), CanonicalJson.sha256Hex(read.getPayload()));
        assertEquals(Outcome.UNCHANGED, store.store(GH, PR, "110", URL, read.getPayload()).outcome());
        assertEquals(1, rowCount());
    }
}
