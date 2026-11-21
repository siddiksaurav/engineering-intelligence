package com.dsinnovators.ei.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * The database itself refuses to change a row of {@code raw_record}, {@code evidence_item} and
 * {@code judgment}, so the rule does not rest on the Java code alone (decision 33). The behaviour of
 * the {@code raw_record} trigger is tested in {@code RawRecordStoreTest}; this test checks that every
 * such table has a trigger that fires before each update and each delete.
 */
class AppendOnlyTablesTest extends DatabaseTest {

    @Test
    void rawRecordHasATriggerBeforeEveryUpdateAndDelete() {
        hasTriggerBeforeEveryUpdateAndDelete("raw_record");
    }

    @Test
    void evidenceItemHasATriggerBeforeEveryUpdateAndDelete() {
        hasTriggerBeforeEveryUpdateAndDelete("evidence_item");
    }

    @Test
    @Disabled("F06 creates judgment; enable this test in F06")
    void judgmentHasATriggerBeforeEveryUpdateAndDelete() {
        hasTriggerBeforeEveryUpdateAndDelete("judgment");
    }

    private void hasTriggerBeforeEveryUpdateAndDelete(String table) {
        List<String> events = jdbc.queryForList("""
                select event_manipulation from information_schema.triggers
                where event_object_schema = 'public' and event_object_table = ?
                  and action_timing = 'BEFORE' and action_orientation = 'ROW'
                """, String.class, table);

        assertTrue(
                events.containsAll(List.of("UPDATE", "DELETE")),
                () -> "Table " + table + " needs a row trigger before update and delete; found " + events);
    }
}
