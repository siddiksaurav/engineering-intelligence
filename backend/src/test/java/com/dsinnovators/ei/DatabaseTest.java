package com.dsinnovators.ei;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for tests that need the database. A subclass runs as a Spring Boot test against a
 * PostgreSQL 18 container with all Flyway migrations applied. One container serves the whole test
 * run. Every test starts from empty tables, except the rows the migrations seed: {@code data_source},
 * {@code dimension} and the seeded {@code setting}, {@code dimension_evidence_type} and {@code
 * dimension_gate} rows. The profile {@code test} keeps the developer's {@code .env}
 * out of the context.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class DatabaseTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected JdbcTemplate jdbc;

    // The settings the migrations seed, read once from the freshly migrated database before any
    // test has written to it. Every test starts with exactly these.
    private static List<Map<String, Object>> seededSettings;

    private static List<Map<String, Object>> seededMapping;
    private static List<Map<String, Object>> seededGates;

    @BeforeEach
    void emptyTables() {
        if (seededSettings == null) {
            seededSettings =
                    jdbc.queryForList("select key, valid_from_quarter, value::text as value, description from setting");
            seededMapping = jdbc.queryForList(
                    "select dimension_id, evidence_type, valid_from_quarter, tiered, from_level from dimension_evidence_type");
            seededGates =
                    jdbc.queryForList("select dimension_id, gate, valid_from_quarter, applied from dimension_gate");
        }
        jdbc.execute(
                "truncate raw_record, setting, dimension_evidence_type, dimension_gate, dimension_score, identity_proposal, person_identity, person, project, job_run, pull_request_work_item, evidence_item, evidence_item_source, evidence_item_link, judgment, model_exchange, blind_check_item, human_label, reason_mark restart identity cascade");
        for (Map<String, Object> setting : seededSettings) {
            jdbc.update(
                    "insert into setting (key, valid_from_quarter, value, description) values (?, ?, ?::jsonb, ?)",
                    setting.get("key"),
                    setting.get("valid_from_quarter"),
                    setting.get("value"),
                    setting.get("description"));
        }
        for (Map<String, Object> row : seededMapping) {
            jdbc.update(
                    "insert into dimension_evidence_type (dimension_id, evidence_type, valid_from_quarter, tiered, from_level) values (?, ?, ?, ?, ?)",
                    row.get("dimension_id"),
                    row.get("evidence_type"),
                    row.get("valid_from_quarter"),
                    row.get("tiered"),
                    row.get("from_level"));
        }
        for (Map<String, Object> row : seededGates) {
            jdbc.update(
                    "insert into dimension_gate (dimension_id, gate, valid_from_quarter, applied) values (?, ?, ?, ?)",
                    row.get("dimension_id"),
                    row.get("gate"),
                    row.get("valid_from_quarter"),
                    row.get("applied"));
        }
    }
}
