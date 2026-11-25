package com.dsinnovators.ei.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BlindCheckSampleTest extends DatabaseTest {

    @Autowired
    private BlindCheckSample sample;

    private BlindCheckFixture stored;

    @BeforeEach
    void setUp() {
        stored = new BlindCheckFixture(jdbc);
        jdbc.update("update setting set value = '3'::jsonb where key = 'blind_check.items_per_complexity'");
    }

    private void judgedItems(String complexity, int count) {
        IntStream.range(0, count).forEach(ignored -> stored.judgedItem(complexity));
    }

    private Map<String, Long> pickedPerComplexity() {
        return jdbc.queryForList("""
                        select j.value ->> 'complexity' as complexity
                        from blind_check_item b join judgment j on j.id = b.judgment_id
                        """, String.class).stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    @Test
    void picksTheSameNumberOfItemsOfEachComplexityAndKeepsThemOnASecondCall() {
        judgedItems("low", 4);
        judgedItems("medium", 5);
        judgedItems("high", 3);
        judgedItems("very_high", 2);

        List<BlindCheckItem> first = sample.pick();
        List<BlindCheckItem> second = sample.pick();

        assertEquals(Map.of("low", 3L, "medium", 3L, "high", 3L), pickedPerComplexity());
        assertEquals(9, first.size());
        assertEquals(
                first.stream().map(BlindCheckItem::getId).toList(),
                second.stream().map(BlindCheckItem::getId).toList());
        assertEquals(
                IntStream.rangeClosed(1, 9).boxed().toList(),
                first.stream().map(BlindCheckItem::getPosition).toList());
    }

    @Test
    void pinsTheCurrentJudgmentOfEachPickedItem() {
        long item = stored.judgedItem("medium");
        long superseded = jdbc.queryForObject("select max(id) from judgment", Long.class);
        long current = stored.judgment(item, "high", superseded);
        judgedItems("low", 3);
        judgedItems("medium", 3);
        judgedItems("high", 2);

        sample.pick();

        assertEquals(
                List.of(current),
                jdbc.queryForList(
                        "select judgment_id from blind_check_item where evidence_item_id = ?", Long.class, item));
    }

    @Test
    void doesNotGroupTheRowsByComplexity() {
        judgedItems("low", 3);
        judgedItems("medium", 3);
        judgedItems("high", 3);

        List<String> order = sample.pick().stream()
                .map(item -> jdbc.queryForObject(
                        "select value ->> 'complexity' from judgment where id = ?", String.class, item.getJudgmentId()))
                .toList();

        assertNotEquals(order.stream().sorted().toList(), order);
        assertNotEquals(List.of("low", "low", "low", "medium", "medium", "medium", "high", "high", "high"), order);
    }

    @Test
    void refusesTheSampleAndStoresNothingWhenAComplexityHasTooFewItems() {
        judgedItems("low", 3);
        judgedItems("medium", 2);
        judgedItems("high", 3);

        InsufficientItemsException refused = assertThrows(InsufficientItemsException.class, () -> sample.pick());

        assertTrue(refused.getMessage().contains("medium"), refused.getMessage());
        assertTrue(refused.getMessage().contains("2"), refused.getMessage());
        assertTrue(refused.getMessage().contains("3"), refused.getMessage());
        assertEquals(0, jdbc.queryForObject("select count(*) from blind_check_item", Integer.class));
    }

    @Test
    void leavesOutItemsThatAreNotCurrent() {
        judgedItems("low", 3);
        judgedItems("medium", 3);
        long withdrawnHigh = stored.judgedItem("high");
        judgedItems("high", 2);
        jdbc.update("""
                insert into evidence_item (item_key, evidence_type, person_id, occurred_on, source_url, fields,
                                           content_hash, withdrawn, supersedes_id, built_at)
                select item_key, evidence_type, person_id, occurred_on, source_url, fields, 'hash', true, id, now()
                from evidence_item where id = ?
                """, withdrawnHigh);

        assertThrows(InsufficientItemsException.class, () -> sample.pick());
    }
}
