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
import org.springframework.beans.factory.annotation.Autowired;

class SettingServiceTest extends DatabaseTest {

    private static final Quarter Q3 = Quarter.parse("2026-Q3");

    @Autowired
    private SettingService settings;

    private void insert(String key, String quarter, String json) {
        jdbc.update(
                "insert into setting (key, valid_from_quarter, value, description) values (?, ?, ?::jsonb, ?)",
                key, quarter, json, "test");
    }

    private static Quarter q(String text) {
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

        assertEquals(new BigDecimal("0.3333"), settings.getDecimal("presence.threshold", q("2026-Q3")));
        assertEquals(new BigDecimal("0.3333"), settings.getDecimal("presence.threshold", q("2026-Q4")));
        assertEquals(new BigDecimal("0.5"), settings.getDecimal("presence.threshold", q("2027-Q1")));
        assertEquals(new BigDecimal("0.5"), settings.getDecimal("presence.threshold", q("2028-Q2")));
    }

    @Test
    void aQuarterBeforeTheFirstValueHasNoSetting() {
        insert("presence.threshold", "2026-Q3", "0.3333");

        SettingNotFoundException e = assertThrows(
                SettingNotFoundException.class, () -> settings.getDecimal("presence.threshold", q("2026-Q2")));
        assertEquals("presence.threshold", e.getKey());
        assertEquals(q("2026-Q2"), e.getQuarter());
        assertTrue(e.getMessage().contains("presence.threshold"));
        assertTrue(e.getMessage().contains("2026-Q2"));
    }

    @Test
    void anUnknownKeyHasNoSetting() {
        SettingNotFoundException e =
                assertThrows(SettingNotFoundException.class, () -> settings.getInt("no.such.key", Q3));
        assertEquals("no.such.key", e.getKey());
    }

    @Test
    void eachKindOfValueHasItsReader() {
        insert("t3.items", "2026-Q3", "2");
        insert("label", "2026-Q3", "\"gate unverified\"");
        insert("done.statuses", "2026-Q3", "[\"merged\",\"tested\",\"closed\"]");
        insert("weights", "2026-Q3", "{\"T1\":1,\"T2\":2,\"T3\":4}");

        assertEquals(2, settings.getInt("t3.items", Q3));
        assertEquals(new BigDecimal("2"), settings.getDecimal("t3.items", Q3));
        assertEquals("gate unverified", settings.getText("label", Q3));
        assertEquals(List.of("merged", "tested", "closed"), settings.getTextList("done.statuses", Q3));
        assertEquals(4, settings.getJson("weights", Q3).get("T3").intValue());
    }

    @Test
    void aValueOfTheWrongKindIsAnErrorThatNamesTheKey() {
        insert("t3.items", "2026-Q3", "2");
        insert("label", "2026-Q3", "\"gate unverified\"");
        insert("done.statuses", "2026-Q3", "[\"merged\",\"tested\",\"closed\"]");
        insert("mixed.list", "2026-Q3", "[1,\"a\"]");
        insert("half", "2026-Q3", "2.5");
        insert("huge", "2026-Q3", "99999999999");

        assertWrongKind("done.statuses", () -> settings.getInt("done.statuses", Q3));
        assertWrongKind("label", () -> settings.getInt("label", Q3));
        assertWrongKind("label", () -> settings.getDecimal("label", Q3));
        assertWrongKind("t3.items", () -> settings.getText("t3.items", Q3));
        assertWrongKind("label", () -> settings.getTextList("label", Q3));
        assertWrongKind("mixed.list", () -> settings.getTextList("mixed.list", Q3));
        assertWrongKind("half", () -> settings.getInt("half", Q3));
        assertWrongKind("huge", () -> settings.getInt("huge", Q3));
    }

    @Test
    void aValueChangedInTheDatabaseIsUsedByTheNextCall() {
        insert("gate.pass", "2026-Q3", "10");
        assertEquals(10, settings.getInt("gate.pass", Q3));

        jdbc.update("update setting set value = '12'::jsonb where key = 'gate.pass'");

        assertEquals(12, settings.getInt("gate.pass", Q3));
    }
}
