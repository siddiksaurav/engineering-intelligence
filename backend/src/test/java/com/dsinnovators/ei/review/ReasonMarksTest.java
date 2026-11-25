package com.dsinnovators.ei.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReasonMarksTest extends DatabaseTest {

    private static final List<ProblemMark> MATCHING_MARKS = List.of(new ProblemMark("Cache invalidation", "right"));

    @Autowired
    private BlindCheckSample sample;

    @Autowired
    private LabelImport labelImport;

    @Autowired
    private ReasonMarks reasonMarks;

    @Autowired
    private BlindCheckReader reader;

    private long lowItem;
    private long mediumItem;
    private long outsideTheSample;

    @BeforeEach
    void pickASampleOfThree() {
        BlindCheckFixture stored = new BlindCheckFixture(jdbc);
        jdbc.update("update setting set value = '1'::jsonb where key = 'blind_check.items_per_complexity'");
        lowItem = stored.judgedItem("low");
        mediumItem = stored.judgedItem("medium");
        stored.judgedItem("high");
        sample.pick();
        outsideTheSample = stored.judgedItem("low");
    }

    private void label(long itemId, String complexity) {
        labelImport.importLabels("item_id,complexity,note\n" + itemId + "," + complexity + ",\n");
    }

    @Test
    void aMarkNeedsALabelAndASecondMarkSupersedesTheFirst() {
        assertThrows(ItemNotInSampleException.class, () -> reasonMarks.mark(outsideTheSample, true, List.of(), null));
        assertThrows(ReasonMarkNotAllowedException.class, () -> reasonMarks.mark(lowItem, true, MATCHING_MARKS, null));
        label(lowItem, "low");

        reasonMarks.mark(lowItem, false, List.of(new ProblemMark("Cache invalidation", "should_be_routine")), "first");
        ItemBlindCheck.Mark second = reasonMarks.mark(lowItem, true, MATCHING_MARKS, null);

        assertEquals(2, jdbc.queryForObject("select count(*) from reason_mark", Integer.class));
        assertEquals(
                jdbc.queryForObject("select id from reason_mark order by id limit 1", Long.class),
                jdbc.queryForObject("select supersedes_id from reason_mark order by id desc limit 1", Long.class));
        ItemBlindCheck.Mark current = reader.ofItem(lowItem).orElseThrow().reasonMark();
        assertEquals(second, current);
        assertTrue(current.reasonRight());
        assertEquals("right", current.problemMarks().get(0).get("verdict").stringValue());
    }

    @Test
    void anItemTheUserCouldNotJudgeGetsNoMark() {
        label(mediumItem, "cannot_judge");

        ReasonMarkNotAllowedException refused = assertThrows(
                ReasonMarkNotAllowedException.class, () -> reasonMarks.mark(mediumItem, true, MATCHING_MARKS, null));

        assertTrue(refused.getMessage().contains("cannot_judge"), refused.getMessage());
    }

    @Test
    void refusesProblemMarksThatDoNotMatchTheProblemsOfTheJudgment() {
        label(lowItem, "low");

        InvalidProblemMarksException tooFew = assertThrows(
                InvalidProblemMarksException.class, () -> reasonMarks.mark(lowItem, true, List.of(), null));
        InvalidProblemMarksException otherName = assertThrows(
                InvalidProblemMarksException.class,
                () -> reasonMarks.mark(lowItem, true, List.of(new ProblemMark("Something else", "right")), null));
        InvalidProblemMarksException otherVerdict = assertThrows(
                InvalidProblemMarksException.class,
                () -> reasonMarks.mark(lowItem, true, List.of(new ProblemMark("Cache invalidation", "fine")), null));

        assertTrue(tooFew.getMessage().contains("1"), tooFew.getMessage());
        assertTrue(otherName.getMessage().contains("Something else"), otherName.getMessage());
        assertTrue(otherVerdict.getMessage().contains("fine"), otherVerdict.getMessage());
        assertEquals(0, jdbc.queryForObject("select count(*) from reason_mark", Integer.class));
    }
}
