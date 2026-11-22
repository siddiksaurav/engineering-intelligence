package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CurrentItemJudgmentsTest extends DatabaseTest {

    @Autowired
    private CurrentItemJudgments currentJudgments;

    private long insertItem(String key) {
        long personId = jdbc.query("select id from person limit 1", rows -> rows.next() ? rows.getLong(1) : null);
        return jdbc.queryForObject("""
                insert into evidence_item (item_key, evidence_type, person_id, occurred_on, source_url,
                                           fields, content_hash, withdrawn, built_at)
                values (?, 'feature_work', ?, date '2026-07-01', 'https://example.test/1',
                        '{}'::jsonb, 'hash', false, now())
                returning id
                """, Long.class, key, personId);
    }

    private long judgment(long itemId, String kind, String tier, String reason, String value, Long supersedes) {
        return jdbc.queryForObject("""
                insert into judgment (kind, evidence_item_id, value, tier, reason, facts, confidence, judged_by, model,
                                      prompt_version, input_hash, supersedes_id, judged_at)
                values (?, ?, ?::jsonb, ?, ?, '[{"fact":"Adds a cache","link":"https://gh.test/pull/1"}]'::jsonb,
                        0.8, 'model', 'm1', 'v2', 'hash' || gen_random_uuid(), ?, now())
                returning id
                """, Long.class, kind, itemId, value, tier, reason, supersedes);
    }

    @Test
    void returnsTheCurrentJudgmentOfTheKindForEachItem() {
        jdbc.update("insert into person (display_name) values ('Ada')");
        long judged = insertItem("feature_work:1");
        long withoutJudgment = insertItem("feature_work:2");
        long first = judgment(judged, "feature_work_complexity", "T1", "Small", "{\"complexity\":\"Low\"}", null);
        judgment(judged, "feature_work_complexity", "T2", "Larger", "{\"complexity\":\"Medium\"}", first);
        judgment(judged, "item_tier", "T3", "Other kind", "{}", null);

        Map<Long, ItemJudgment> byItem =
                currentJudgments.ofItems(JudgmentKind.FEATURE_WORK_COMPLEXITY, List.of(judged, withoutJudgment));

        assertEquals(1, byItem.size());
        assertFalse(byItem.containsKey(withoutJudgment));
        ItemJudgment current = byItem.get(judged);
        assertEquals(JudgmentKind.FEATURE_WORK_COMPLEXITY, current.kind());
        assertEquals("T2", current.tier());
        assertEquals("Medium", current.value().get("complexity").stringValue());
        assertEquals("Larger", current.reason());
        assertEquals("Adds a cache", current.facts().get(0).get("fact").stringValue());
        assertEquals(
                "https://gh.test/pull/1", current.facts().get(0).get("link").stringValue());
        assertEquals(JudgedBy.MODEL, current.judgedBy());
        assertEquals("m1", current.model());
        assertEquals("v2", current.promptVersion());
        assertEquals(0.8, current.confidence());
        assertNotNull(current.judgedAt());
    }

    @Test
    void anEmptyCollectionOfItemsGivesNoJudgments() {
        assertTrue(currentJudgments.ofItems(JudgmentKind.ITEM_TIER, List.of()).isEmpty());
    }
}
