package com.dsinnovators.ei;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

/**
 * The developer's {@code .env} holds real tokens. A test that read it could call a real, paid
 * service, so the test context must never load it.
 */
class TestEnvironmentTest extends DatabaseTest {

    @Autowired
    private ConfigurableEnvironment environment;

    @Test
    void aTestContextLoadsNothingFromTheEnvFile() {
        List<String> fromEnvFile = environment.getPropertySources().stream()
                .map(PropertySource::getName)
                .filter(name -> name.contains(".env"))
                .toList();

        assertEquals(List.of(), fromEnvFile);
    }
}
