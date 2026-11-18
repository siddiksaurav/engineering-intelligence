package com.dsinnovators.ei.setting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.Quarter;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

class SettingServiceTest extends DatabaseTest {

    private static final Quarter Q3_2026 = Quarter.parse("2026-Q3");

    @Autowired
    private SettingService settings;

    private void insert(String key, String quarter, String json) {
        jdbc.update(
                "insert into setting (key, valid_from_quarter, value, description) values (?, ?, ?::jsonb, ?)",
                key,
                quarter,
                json,
                "test");
    }

    private static Quarter quarter(String text) {
        return Quarter.parse(text);
    }

    private static void assertWrongKind(String key, Executable call) {
        SettingTypeException e = assertThrows(SettingTypeException.class, call);
        assertEquals(key, e.getKey());
        assertTrue(e.getMessage().contains(key));
    }

    @Test
    void aValueIsInForceUntilALaterQuarterReplacesIt() {
        insert("presence.threshold", "2026-Q3", "0.3333");
        insert("presence.threshold", "2027-Q1", "0.5");

        assertEquals(new BigDecimal("0.3333"), settings.getDecimal("presence.threshold", quarter("2026-Q3")));
        assertEquals(new BigDecimal("0.3333"), settings.getDecimal("presence.threshold", quarter("2026-Q4")));
        assertEquals(new BigDecimal("0.5"), settings.getDecimal("presence.threshold", quarter("2027-Q1")));
        assertEquals(new BigDecimal("0.5"), settings.getDecimal("presence.threshold", quarter("2028-Q2")));
    }

    @Test
    void aQuarterBeforeTheFirstValueHasNoSetting() {
        insert("presence.threshold", "2026-Q3", "0.3333");

        SettingNotFoundException e = assertThrows(
                SettingNotFoundException.class, () -> settings.getDecimal("presence.threshold", quarter("2026-Q2")));
        assertEquals("presence.threshold", e.getKey());
        assertEquals(quarter("2026-Q2"), e.getQuarter());
        assertTrue(e.getMessage().contains("presence.threshold"));
        assertTrue(e.getMessage().contains("2026-Q2"));
    }

    @Test
    void anUnknownKeyHasNoSetting() {
        SettingNotFoundException e =
                assertThrows(SettingNotFoundException.class, () -> settings.getInt("no.such.key", Q3_2026));
        assertEquals("no.such.key", e.getKey());
    }

    @Test
    void aWholeNumberIsReadAsAnInt() {
        insert("t3.items", "2026-Q3", "2");

        assertEquals(2, settings.getInt("t3.items", Q3_2026));
    }

    @Test
    void aNumberIsReadAsADecimal() {
        insert("t3.items", "2026-Q3", "2");

        assertEquals(new BigDecimal("2"), settings.getDecimal("t3.items", Q3_2026));
    }

    @Test
    void aTextIsReadAsText() {
        insert("label", "2026-Q3", "\"gate unverified\"");

        assertEquals("gate unverified", settings.getText("label", Q3_2026));
    }

    @Test
    void aListOfTextsIsReadInOrder() {
        insert("done.statuses", "2026-Q3", "[\"merged\",\"tested\",\"closed\"]");

        assertEquals(List.of("merged", "tested", "closed"), settings.getTextList("done.statuses", Q3_2026));
    }

    @Test
    void anObjectIsReadAsJson() {
        insert("weights", "2026-Q3", "{\"T1\":1,\"T2\":2,\"T3\":4}");

        assertEquals(4, settings.getJson("weights", Q3_2026).get("T3").intValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"text\"", "[1]", "2.5", "99999999999"})
    void aValueThatIsNotAnIntIsAnErrorThatNamesTheKey(String json) {
        insert("some.key", "2026-Q3", json);

        assertWrongKind("some.key", () -> settings.getInt("some.key", Q3_2026));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"text\"", "[1]", "{}"})
    void aValueThatIsNotANumberIsAnErrorThatNamesTheKey(String json) {
        insert("some.key", "2026-Q3", json);

        assertWrongKind("some.key", () -> settings.getDecimal("some.key", Q3_2026));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "[\"a\"]", "{}"})
    void aValueThatIsNotATextIsAnErrorThatNamesTheKey(String json) {
        insert("some.key", "2026-Q3", json);

        assertWrongKind("some.key", () -> settings.getText("some.key", Q3_2026));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"text\"", "[1,\"a\"]", "{}"})
    void aValueThatIsNotAListOfTextsIsAnErrorThatNamesTheKey(String json) {
        insert("some.key", "2026-Q3", json);

        assertWrongKind("some.key", () -> settings.getTextList("some.key", Q3_2026));
    }

    @Test
    void aMissingKeyOrQuarterIsRejectedByName() {
        assertEquals(
                "key",
                assertThrows(NullPointerException.class, () -> settings.getInt(null, Q3_2026))
                        .getMessage());
        assertEquals(
                "quarter",
                assertThrows(NullPointerException.class, () -> settings.getInt("some.key", null))
                        .getMessage());
    }

    @Test
    void aValueChangedInTheDatabaseIsUsedByTheNextCall() {
        insert("gate.pass", "2026-Q3", "10");
        assertEquals(10, settings.getInt("gate.pass", Q3_2026));

        jdbc.update("update setting set value = '12'::jsonb where key = 'gate.pass'");

        assertEquals(12, settings.getInt("gate.pass", Q3_2026));
    }
}
