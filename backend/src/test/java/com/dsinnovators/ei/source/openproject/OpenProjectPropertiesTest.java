package com.dsinnovators.ei.source.openproject;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class OpenProjectPropertiesTest {

    private static final URI URL = URI.create("https://community.openproject.org/api/v3");
    private static final Duration SECOND = Duration.ofSeconds(1);

    private static IllegalArgumentException refused(
            URI apiUrl, int pageSize, Duration pause, int retries, Duration retryWait, Duration timeout) {
        return assertThrows(
                IllegalArgumentException.class,
                () -> new OpenProjectProperties(apiUrl, pageSize, pause, retries, retryWait, timeout));
    }

    @Test
    void acceptsTheOperatingNumbers() {
        assertDoesNotThrow(() -> new OpenProjectProperties(URL, 100, Duration.ZERO, 0, Duration.ZERO, SECOND));
    }

    @Test
    void refusesAMissingApiUrl() {
        assertTrue(refused(null, 100, SECOND, 3, SECOND, SECOND).getMessage().contains("ei.openproject.api-url"));
    }

    @Test
    void refusesAPageSizeBelowOneOrAboveWhatTheServerAllows() {
        assertTrue(refused(URL, 0, SECOND, 3, SECOND, SECOND).getMessage().contains("ei.openproject.page-size"));
        assertTrue(refused(URL, 1001, SECOND, 3, SECOND, SECOND).getMessage().contains("was 1001"));
    }

    @Test
    void refusesANegativeOrMissingPauseRetryWaitOrRetries() {
        assertTrue(refused(URL, 100, SECOND.negated(), 3, SECOND, SECOND)
                .getMessage()
                .contains("pause"));
        assertTrue(refused(URL, 100, null, 3, SECOND, SECOND).getMessage().contains("pause"));
        assertTrue(refused(URL, 100, SECOND, -1, SECOND, SECOND).getMessage().contains("retries"));
        assertTrue(refused(URL, 100, SECOND, 3, SECOND.negated(), SECOND)
                .getMessage()
                .contains("retry-wait"));
    }

    @Test
    void refusesATimeoutOfZeroOrLess() {
        assertTrue(
                refused(URL, 100, SECOND, 3, SECOND, Duration.ZERO).getMessage().contains("timeout"));
        assertTrue(refused(URL, 100, SECOND, 3, SECOND, null).getMessage().contains("timeout"));
    }

    @Test
    void applicationYmlStartsWithThePublicServerAndAPoliteCallRate() throws Exception {
        PropertySource<?> yml = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .getFirst();
        OpenProjectProperties properties = new Binder(new MapConfigurationPropertySource(java.util.Map.of(
                        "ei.openproject.api-url", yml.getProperty("ei.openproject.api-url"),
                        "ei.openproject.page-size", yml.getProperty("ei.openproject.page-size"),
                        "ei.openproject.pause", yml.getProperty("ei.openproject.pause"),
                        "ei.openproject.retries", yml.getProperty("ei.openproject.retries"),
                        "ei.openproject.retry-wait", yml.getProperty("ei.openproject.retry-wait"),
                        "ei.openproject.timeout", yml.getProperty("ei.openproject.timeout"))))
                .bind("ei.openproject", OpenProjectProperties.class)
                .get();

        assertEquals(URL, properties.apiUrl());
        assertEquals(100, properties.pageSize());
        assertEquals(Duration.ofMillis(200), properties.pause());
        assertEquals(3, properties.retries());
    }
}
