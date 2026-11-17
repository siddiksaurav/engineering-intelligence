package com.dsinnovators.ei;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for tests that need the database. A subclass runs as a Spring Boot test against a
 * PostgreSQL 18 container with all Flyway migrations applied. One container serves the whole test
 * run. Every test starts from empty tables, except the seeded {@code data_source} rows.
 */
@SpringBootTest
public abstract class DatabaseTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void emptyTables() {
        jdbc.execute("truncate raw_record, setting, person_identity, person, project restart identity cascade");
    }
}
