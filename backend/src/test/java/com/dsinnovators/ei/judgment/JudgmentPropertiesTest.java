package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class JudgmentPropertiesTest {

    private static final JudgmentProvider GROQ =
            new JudgmentProvider("groq", "https://api.groq.com/openai/v1", "gsk_secret", "openai/gpt-oss-120b", "low");
    private static final JudgmentProvider GEMINI = new JudgmentProvider(
            "gemini",
            "https://generativelanguage.googleapis.com/v1beta/openai",
            "gemini-secret",
            "gemini-2.5-flash",
            "");

    private static JudgmentProperties propertiesOf(List<JudgmentProvider> providers) {
        return new JudgmentProperties(
                providers,
                Duration.ofSeconds(120),
                3,
                Duration.ofSeconds(2),
                Duration.ofSeconds(60),
                12_000,
                4_000,
                300);
    }

    @Test
    void aProviderWithoutAKeyIsLeftOut() {
        JudgmentProvider withoutKey = new JudgmentProvider("gemini", GEMINI.baseUrl(), " ", "", "");

        JudgmentProperties properties = propertiesOf(List.of(GROQ, withoutKey));

        assertEquals(List.of(GROQ), properties.providers());
    }

    @Test
    void noProviderAtAllIsAllowedSoTheApplicationStartsWithoutAKey() {
        assertEquals(List.of(), propertiesOf(List.of()).providers());
        assertEquals(List.of(), propertiesOf(null).providers());
    }

    @Test
    void aProviderWithAKeyNeedsANameABaseUrlAndAModel() {
        assertThrows(
                IllegalArgumentException.class,
                () -> propertiesOf(List.of(new JudgmentProvider(" ", GROQ.baseUrl(), "k", "m", null))));
        assertThrows(
                IllegalArgumentException.class,
                () -> propertiesOf(List.of(new JudgmentProvider("a", "", "k", "m", null))));
        assertThrows(
                IllegalArgumentException.class,
                () -> propertiesOf(List.of(new JudgmentProvider("a", GROQ.baseUrl(), "k", "", null))));
    }

    @Test
    void twoProvidersMayNotShareAName() {
        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> propertiesOf(List.of(GROQ, GROQ)));

        assertTrue(thrown.getMessage().contains("groq"));
    }

    @Test
    void theOperatingNumbersAreChecked() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JudgmentProperties(
                        List.of(),
                        Duration.ZERO,
                        3,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(60),
                        12_000,
                        4_000,
                        300));
        assertThrows(
                IllegalArgumentException.class,
                () -> new JudgmentProperties(
                        List.of(),
                        Duration.ofSeconds(1),
                        -1,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(60),
                        12_000,
                        4_000,
                        300));
        assertThrows(
                IllegalArgumentException.class,
                () -> new JudgmentProperties(
                        List.of(),
                        Duration.ofSeconds(1),
                        3,
                        Duration.ofSeconds(-2),
                        Duration.ofSeconds(60),
                        12_000,
                        4_000,
                        300));
        assertThrows(
                IllegalArgumentException.class,
                () -> new JudgmentProperties(
                        List.of(),
                        Duration.ofSeconds(1),
                        3,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(-1),
                        12_000,
                        4_000,
                        300));
        assertThrows(
                IllegalArgumentException.class,
                () -> new JudgmentProperties(
                        List.of(),
                        Duration.ofSeconds(1),
                        3,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(60),
                        0,
                        4_000,
                        300));
        assertThrows(
                IllegalArgumentException.class,
                () -> new JudgmentProperties(
                        List.of(),
                        Duration.ofSeconds(1),
                        3,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(60),
                        12_000,
                        0,
                        300));
        assertThrows(
                IllegalArgumentException.class,
                () -> new JudgmentProperties(
                        List.of(),
                        Duration.ofSeconds(1),
                        3,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(60),
                        12_000,
                        4_000,
                        0));
    }

    @Test
    void theCallPolicyCarriesTheRetrySettings() {
        CallPolicy policy = propertiesOf(List.of()).callPolicy();

        assertEquals(new CallPolicy(3, Duration.ofSeconds(2), Duration.ofSeconds(60), 300), policy);
    }

    @Test
    void aKeyIsNeverShownInToString() {
        assertFalse(GROQ.toString().contains("gsk_secret"));
        assertFalse(propertiesOf(List.of(GROQ)).toString().contains("gsk_secret"));
    }

    @Test
    void bindsTheProviderListFromTheNamesOfApplicationYml() {
        Map<String, String> source = Map.ofEntries(
                Map.entry("ei.judgment.providers[0].name", "groq"),
                Map.entry("ei.judgment.providers[0].base-url", "https://api.groq.com/openai/v1"),
                Map.entry("ei.judgment.providers[0].api-key", "gsk_secret"),
                Map.entry("ei.judgment.providers[0].model", "openai/gpt-oss-120b"),
                Map.entry("ei.judgment.providers[0].reasoning-effort", "low"),
                Map.entry("ei.judgment.providers[1].name", "gemini"),
                Map.entry(
                        "ei.judgment.providers[1].base-url", "https://generativelanguage.googleapis.com/v1beta/openai"),
                Map.entry("ei.judgment.providers[1].api-key", ""),
                Map.entry("ei.judgment.providers[1].model", ""),
                Map.entry("ei.judgment.timeout", "2m"),
                Map.entry("ei.judgment.retries", "3"),
                Map.entry("ei.judgment.retry-wait", "2s"),
                Map.entry("ei.judgment.max-rate-limit-wait", "60s"),
                Map.entry("ei.judgment.max-diff-characters", "12000"),
                Map.entry("ei.judgment.max-body-characters", "4000"),
                Map.entry("ei.judgment.max-error-characters", "300"));

        JudgmentProperties properties = new Binder(new MapConfigurationPropertySource(source))
                .bind("ei.judgment", JudgmentProperties.class)
                .get();

        assertEquals(List.of(GROQ), properties.providers());
        assertEquals(Duration.ofMinutes(2), properties.timeout());
        assertEquals(12_000, properties.maxDiffCharacters());
        assertEquals(4_000, properties.maxBodyCharacters());
    }
}
