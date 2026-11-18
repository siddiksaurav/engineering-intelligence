package com.dsinnovators.ei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

class SchemaTest extends DatabaseTest {

    private static final String INSERT_RAW_RECORD =
            "insert into raw_record (data_source_id, record_kind, external_id, version, url, content_hash, payload, fetched_at)"
                    + " values (?, 'pull_request', '1', ?, 'https://example.org/1', 'hash', '{}'::jsonb, now())";

    private static final String INSERT_SETTING =
            "insert into setting (key, valid_from_quarter, value, description) values (?, ?, '1'::jsonb, 'test')";

    private long githubId() {
        return jdbc.queryForObject("select id from data_source where key = 'github'", Long.class);
    }

    @Test
    void theSixBaseTablesExist() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'"
                        + " and table_name <> 'flyway_schema_history' order by table_name",
                String.class);
        assertEquals(List.of("data_source", "person", "person_identity", "project", "raw_record", "setting"), tables);
    }

    @Test
    void theTwoDataSourcesAreSeeded() {
        List<Map<String, Object>> rows = jdbc.queryForList("select key, name, base_url from data_source order by key");
        assertEquals(
                List.of(
                        Map.of("key", "github", "name", "GitHub", "base_url", "https://github.com"),
                        Map.of(
                                "key",
                                "openproject",
                                "name",
                                "OpenProject",
                                "base_url",
                                "https://community.openproject.org")),
                rows);
    }

    @Test
    void aRawRecordVersionIsUnique() {
        jdbc.update(INSERT_RAW_RECORD, githubId(), 1);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(INSERT_RAW_RECORD, githubId(), 1));
    }

    @Test
    void aRawRecordNeedsAnExistingDataSource() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(INSERT_RAW_RECORD, 999_999L, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026Q3", "2026-Q5", "2026-q3", "26-Q3"})
    void aSettingQuarterInAnotherFormIsRefused(String quarter) {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(INSERT_SETTING, "a", quarter));
    }

    @Test
    void aSettingQuarterInTheFormYearDashQuarterIsAccepted() {
        assertEquals(1, jdbc.update(INSERT_SETTING, "a", "2026-Q3"));
    }

    @Test
    void aSettingKeyAndQuarterAreUnique() {
        jdbc.update(INSERT_SETTING, "a", "2026-Q3");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(INSERT_SETTING, "a", "2026-Q3"));
    }

    @Test
    void aPersonLevelOutsideTheThreeLevelsIsRefused() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update("insert into person (display_name, level) values ('A', 'L4')"));
    }

    @Test
    void aPersonMayHaveNoLevelAndGetsACreationTime() {
        jdbc.update("insert into person (display_name) values ('B')");

        Map<String, Object> row = jdbc.queryForMap("select level, created_at from person where display_name = 'B'");
        assertNull(row.get("level"));
        assertNotNull(row.get("created_at"));
    }

    @Test
    void anOutsideIdentityBelongsToOnePerson() {
        jdbc.update("insert into person (display_name) values ('A'), ('B')");
        String insert = "insert into person_identity (person_id, data_source_id, external_id, username)"
                + " values ((select id from person where display_name = ?), ?, 'u1', 'login')";
        jdbc.update(insert, "A", githubId());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(insert, "B", githubId()));
    }

    @Test
    void flywayAppliedVersionsOneAndTwo() {
        assertEquals(
                List.of("1", "2"),
                jdbc.queryForList("select version from flyway_schema_history order by installed_rank", String.class));
    }
}
