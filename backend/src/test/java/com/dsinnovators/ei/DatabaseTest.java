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
 * run. Every test starts from empty tables, except the rows the migrations seed: {@code data_source}
 * and the seeded {@code setting} rows. The profile {@code test} keeps the developer's {@code .env}
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

    @BeforeEach
    void emptyTables() {
        if (seededSettings == null) {
            seededSettings =
                    jdbc.queryForList("select key, valid_from_quarter, value::text as value, description from setting");
        }
        jdbc.execute(
                "truncate raw_record, setting, identity_proposal, person_identity, person, project, job_run, pull_request_work_item, evidence_item, evidence_item_source, evidence_item_link restart identity cascade");
        for (Map<String, Object> setting : seededSettings) {
            jdbc.update(
                    "insert into setting (key, valid_from_quarter, value, description) values (?, ?, ?::jsonb, ?)",
                    setting.get("key"),
                    setting.get("valid_from_quarter"),
                    setting.get("value"),
                    setting.get("description"));
        }
    }
}
