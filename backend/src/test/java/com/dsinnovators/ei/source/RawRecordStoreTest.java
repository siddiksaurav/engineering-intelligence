package com.dsinnovators.ei.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.source.StoreResult.Outcome;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

class RawRecordStoreTest extends DatabaseTest {

    private static final String GITHUB = "github";
    private static final String PULL_REQUEST = "pull_request";
    private static final String URL = "https://github.com/opf/openproject/pull/101";

    @Autowired
    private RawRecordStore store;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private EntityManager entityManager;

    private static JsonNode json(String text) {
        return CanonicalJson.parse(text);
    }

    private int rowCount() {
        return jdbc.queryForObject("select count(*) from raw_record", Integer.class);
    }

    @Test
    void theFirstCopyIsVersionOne() {
        Instant before = Instant.now().minusMillis(1);
        StoreResult result = store.store(GITHUB, PULL_REQUEST, "101", URL, json("{\"title\":\"A\"}"));
        Instant after = Instant.now().plusMillis(1);

        assertEquals(Outcome.CREATED, result.outcome());
        RawRecord record = result.record();
        assertEquals(1, record.getVersion());
        assertEquals(GITHUB, record.getDataSource().getKey());
        assertEquals(PULL_REQUEST, record.getRecordKind());
        assertEquals("101", record.getExternalId());
        assertEquals(URL, record.getUrl());
        assertEquals(CanonicalJson.sha256Hex(json("{\"title\":\"A\"}")), record.getContentHash());
        assertEquals(json("{\"title\":\"A\"}"), record.getPayload());
        assertFalse(record.getFetchedAt().isBefore(before));
        assertFalse(record.getFetchedAt().isAfter(after));
        assertEquals(1, rowCount());
    }

    @Test
    void theSamePayloadAgainWritesNothingWhateverTheKeyOrder() {
        store.store(GITHUB, PULL_REQUEST, "102", URL, json("{\"a\":1,\"b\":2}"));
        StoreResult again = store.store(GITHUB, PULL_REQUEST, "102", URL, json("{\"b\":2,\"a\":1}"));

        assertEquals(Outcome.UNCHANGED, again.outcome());
        assertEquals(1, again.record().getVersion());
        assertEquals(1, rowCount());
    }

    @Test
    void aChangedPayloadIsANewVersionAndTheOldRowStays() {
        store.store(GITHUB, PULL_REQUEST, "103", URL, json("{\"title\":\"A\"}"));
        StoreResult second = store.store(GITHUB, PULL_REQUEST, "103", URL, json("{\"title\":\"B\"}"));

        assertEquals(Outcome.NEW_VERSION, second.outcome());
        assertEquals(2, second.record().getVersion());
        List<RawRecord> versions = store.findVersions(GITHUB, PULL_REQUEST, "103");
        assertEquals(List.of(1, 2), versions.stream().map(RawRecord::getVersion).toList());
        assertEquals(
                List.of("A", "B"),
                versions.stream().map(v -> v.getPayload().get("title").asString()).toList());
    }

    @Test
    void contentThatChangesBackIsANewVersionAndTheCurrentOne() {
        store.store(GITHUB, PULL_REQUEST, "104", URL, json("{\"title\":\"A\"}"));
        store.store(GITHUB, PULL_REQUEST, "104", URL, json("{\"title\":\"B\"}"));
        StoreResult third = store.store(GITHUB, PULL_REQUEST, "104", URL, json("{\"title\":\"A\"}"));

        assertEquals(Outcome.NEW_VERSION, third.outcome());
        assertEquals(3, rowCount());
        RawRecord latest = store.findLatest(GITHUB, PULL_REQUEST, "104").orElseThrow();
        assertEquals(3, latest.getVersion());
        assertEquals("A", latest.getPayload().get("title").asString());
    }

    @Test
    void theSameExternalIdUnderAnotherKindOrDataSourceIsASeparateRecord() {
        store.store(GITHUB, PULL_REQUEST, "105", URL, json("{\"n\":1}"));
        StoreResult otherKind = store.store(GITHUB, "review", "105", URL, json("{\"n\":2}"));
        StoreResult otherSource = store.store("openproject", PULL_REQUEST, "105", URL, json("{\"n\":3}"));

        assertEquals(Outcome.CREATED, otherKind.outcome());
        assertEquals(1, otherKind.record().getVersion());
        assertEquals(Outcome.CREATED, otherSource.outcome());
        assertEquals(1, otherSource.record().getVersion());
        assertEquals(3, rowCount());
    }

    @Test
    void anUnknownRecordHasNoLatestVersion() {
        assertTrue(store.findLatest(GITHUB, PULL_REQUEST, "none").isEmpty());
        assertTrue(store.findVersions(GITHUB, PULL_REQUEST, "none").isEmpty());
    }

    @Test
    void latestByKindGivesTheCurrentCopyOfEachRecordOfThatKind() {
        store.store(GITHUB, PULL_REQUEST, "101", URL, json("{\"title\":\"A\"}"));
        store.store(GITHUB, PULL_REQUEST, "103", URL, json("{\"title\":\"A\"}"));
        store.store(GITHUB, PULL_REQUEST, "103", URL, json("{\"title\":\"B\"}"));
        store.store(GITHUB, "review", "900", URL, json("{}"));
        store.store("openproject", PULL_REQUEST, "101", URL, json("{}"));

        List<RawRecord> latest = store.findLatestByKind(GITHUB, PULL_REQUEST);

        assertEquals(
                List.of("101:1", "103:2"),
                latest.stream().map(r -> r.getExternalId() + ":" + r.getVersion()).sorted().toList());
    }

    @Test
    void anUnknownDataSourceIsRejectedAndNothingIsWritten() {
        assertThrows(IllegalArgumentException.class, () -> store.store("gitlab", PULL_REQUEST, "1", URL, json("{}")));
        assertEquals(0, rowCount());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void aMissingDataSourceKeyIsRejectedAndNothingIsWritten(String missing) {
        assertThrows(
                IllegalArgumentException.class, () -> store.store(missing, PULL_REQUEST, "1", URL, json("{}")));
        assertEquals(0, rowCount());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void aMissingRecordKindIsRejectedAndNothingIsWritten(String missing) {
        assertThrows(IllegalArgumentException.class, () -> store.store(GITHUB, missing, "1", URL, json("{}")));
        assertEquals(0, rowCount());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void aMissingExternalIdIsRejectedAndNothingIsWritten(String missing) {
        assertThrows(
                IllegalArgumentException.class, () -> store.store(GITHUB, PULL_REQUEST, missing, URL, json("{}")));
        assertEquals(0, rowCount());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void aMissingUrlIsRejectedAndNothingIsWritten(String missing) {
        assertThrows(
                IllegalArgumentException.class, () -> store.store(GITHUB, PULL_REQUEST, "1", missing, json("{}")));
        assertEquals(0, rowCount());
    }

    @Test
    void aMissingPayloadIsRejectedAndNothingIsWritten() {
        assertThrows(IllegalArgumentException.class, () -> store.store(GITHUB, PULL_REQUEST, "1", URL, null));
        assertEquals(0, rowCount());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void readingNeedsARecordKind(String missing) {
        assertThrows(IllegalArgumentException.class, () -> store.findLatest(GITHUB, missing, "1"));
        assertThrows(IllegalArgumentException.class, () -> store.findVersions(GITHUB, missing, "1"));
        assertThrows(IllegalArgumentException.class, () -> store.findLatestByKind(GITHUB, missing));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void readingNeedsAnExternalId(String missing) {
        assertThrows(IllegalArgumentException.class, () -> store.findLatest(GITHUB, PULL_REQUEST, missing));
        assertThrows(IllegalArgumentException.class, () -> store.findVersions(GITHUB, PULL_REQUEST, missing));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[1,2]", "\"text\"", "null"})
    void aPayloadThatIsNotAnObjectIsStoredLikeAnyOther(String text) {
        StoreResult result = store.store(GITHUB, PULL_REQUEST, "106", URL, json(text));

        assertEquals(Outcome.CREATED, result.outcome());
        assertEquals(json(text), store.findLatest(GITHUB, PULL_REQUEST, "106").orElseThrow().getPayload());
    }

    @Test
    void aChangeToALoadedRecordIsNeverWritten() {
        long id = store.store(GITHUB, PULL_REQUEST, "107", URL, json("{\"title\":\"A\"}")).record().getId();

        transaction.executeWithoutResult(status -> {
            RawRecord loaded = entityManager.find(RawRecord.class, id);
            ReflectionTestUtils.setField(loaded, "url", "changed");
            entityManager.flush();
        });

        assertEquals(URL, jdbc.queryForObject("select url from raw_record where id = ?", String.class, id));
    }

    @Test
    void aStoredRecordCannotBeDeletedThroughJpa() {
        long id = store.store(GITHUB, PULL_REQUEST, "108", URL, json("{\"title\":\"A\"}")).record().getId();

        PersistenceException refused =
                assertThrows(PersistenceException.class, () -> transaction.executeWithoutResult(status -> {
                    entityManager.remove(entityManager.find(RawRecord.class, id));
                    entityManager.flush();
                }));

        assertTrue(refused.getMessage().contains("raw_record is never updated or deleted"));

        assertEquals(1, rowCount());
    }

    @Test
    void theDatabaseRefusesAnUpdateOrDeleteOfARawRecord() {
        long id = store.store(GITHUB, PULL_REQUEST, "109", URL, json("{\"title\":\"A\"}")).record().getId();

        DataAccessException update = assertThrows(
                DataAccessException.class, () -> jdbc.update("update raw_record set url = 'changed' where id = ?", id));
        assertTrue(update.getMessage().contains("raw_record is never updated or deleted"));
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from raw_record where id = ?", id));

        assertEquals(URL, jdbc.queryForObject("select url from raw_record where id = ?", String.class, id));
    }

    @Test
    void aStoredPayloadHashesToItsContentHashAndStoresAsUnchanged() {
        JsonNode payload = json("{\"n\":1E+2,\"m\":0.0000001,\"k\":1.50,\"big\":1e21,\"i\":7}");
        RawRecord written = store.store(GITHUB, PULL_REQUEST, "110", URL, payload).record();

        RawRecord read = store.findLatest(GITHUB, PULL_REQUEST, "110").orElseThrow();
        assertEquals(written.getContentHash(), CanonicalJson.sha256Hex(read.getPayload()));
        assertEquals(Outcome.UNCHANGED, store.store(GITHUB, PULL_REQUEST, "110", URL, read.getPayload()).outcome());
        assertEquals(1, rowCount());
    }
}
