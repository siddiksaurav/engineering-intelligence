package com.dsinnovators.ei.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class LabelImportTest extends DatabaseTest {

    private static final String HEADER = "item_id,developer,title,work_item,pull_requests,complexity,note\r\n";

    @Autowired
    private BlindCheckSample sample;

    @Autowired
    private LabelImport labelImport;

    private long lowItem;
    private long mediumItem;
    private long highItem;

    @BeforeEach
    void pickASampleOfThree() {
        BlindCheckFixture stored = new BlindCheckFixture(jdbc);
        jdbc.update("update setting set value = '1'::jsonb where key = 'blind_check.items_per_complexity'");
        lowItem = stored.judgedItem("low");
        mediumItem = stored.judgedItem("medium");
        highItem = stored.judgedItem("high");
        sample.pick();
    }

    private static String row(Object itemId, String complexity, String note) {
        return itemId + ",Ada,Title,,," + complexity + "," + note + "\r\n";
    }

    private int storedLabels() {
        return jdbc.queryForObject("select count(*) from human_label", Integer.class);
    }

    private String labelOf(long itemId) {
        return jdbc.queryForObject("""
                select coalesce(l.complexity, 'cannot_judge') from human_label l
                join blind_check_item b on b.id = l.blind_check_item_id where b.evidence_item_id = ?
                """, String.class, itemId);
    }

    @Test
    void oneWrongRowStoresNothingAndEveryWrongRowIsNamed() {
        String file = HEADER
                + row(lowItem, "low", "")
                + row(mediumItem, "enormous", "")
                + row("abc", "high", "")
                + row(999999, "high", "")
                + row(highItem, "high", "")
                + row(highItem, "high", "twice");

        LabelImportRefusedException refused =
                assertThrows(LabelImportRefusedException.class, () -> labelImport.importLabels(file));

        assertEquals(0, storedLabels());
        assertEquals(
                List.of(3, 4, 5, 7),
                refused.problems().stream().map(LabelProblem::line).toList());
        assertEquals("enormous", refused.problems().get(0).value());
        assertEquals("abc", refused.problems().get(1).itemId());
        assertTrue(refused.problems().get(2).problem().contains("not in the sample"));
        assertTrue(refused.problems().get(3).problem().contains("twice"));
    }

    @Test
    void storesTheLabelsAndSkipsRowsWithoutAComplexity() {
        String file =
                HEADER + row(lowItem, " Medium ", "my note") + row(mediumItem, "", "") + row(highItem, "very high", "");

        LabelImportResult result = labelImport.importLabels(file);

        assertEquals(new LabelImportResult(2, 0, 1), result);
        assertEquals("medium", labelOf(lowItem));
        assertEquals("very_high", labelOf(highItem));
        assertEquals(
                "my note",
                jdbc.queryForObject("select note from human_label where complexity = 'medium'", String.class));
    }

    @Test
    void readsAFileSavedByASpreadsheet() {
        String file = "﻿item_id;title;complexity;note\r\n" + lowItem
                + ";\"Add a, b\nand c\";High;\"say \"\"hi\"\"\"\r\n" + mediumItem + ";Title;cannot judge;no ticket\r\n";

        LabelImportResult result = labelImport.importLabels(file);

        assertEquals(2, result.labeledNow());
        assertEquals("high", labelOf(lowItem));
        assertEquals("cannot_judge", labelOf(mediumItem));
        assertEquals(
                "say \"hi\"",
                jdbc.queryForObject("select note from human_label where complexity = 'high'", String.class));
    }

    @Test
    void aLabelIsGivenOnceTheSameValueAgainIsSkippedAnotherValueIsRefused() {
        labelImport.importLabels(HEADER + row(lowItem, "low", ""));

        LabelImportResult again = labelImport.importLabels(HEADER + row(lowItem, "Low", ""));
        LabelImportRefusedException changed = assertThrows(
                LabelImportRefusedException.class, () -> labelImport.importLabels(HEADER + row(lowItem, "high", "")));

        assertEquals(new LabelImportResult(0, 1, 2), again);
        assertEquals(1, storedLabels());
        assertTrue(changed.problems().get(0).problem().contains("already labeled low"));
    }

    @Test
    void refusesAHeaderWithoutTheRequiredColumnsAndNamesTheHeaderFound() {
        LabelImportRefusedException refused = assertThrows(
                LabelImportRefusedException.class, () -> labelImport.importLabels("id|level\r\n1|low\r\n"));

        assertTrue(refused.getMessage().contains("id|level"), refused.getMessage());
        assertEquals(List.of(), refused.problems());
    }
}
