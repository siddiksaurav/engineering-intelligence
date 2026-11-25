package com.dsinnovators.ei.judgment;

import static com.dsinnovators.ei.judgment.FeatureWorkAnswer.Complexity.HIGH;
import static com.dsinnovators.ei.judgment.FeatureWorkAnswer.Complexity.LOW;
import static com.dsinnovators.ei.judgment.FeatureWorkAnswer.Complexity.MEDIUM;
import static com.dsinnovators.ei.judgment.FeatureWorkAnswer.Complexity.VERY_HIGH;
import static com.dsinnovators.ei.judgment.FeatureWorkAnswer.Depth.ANSWER_EXISTS;
import static com.dsinnovators.ei.judgment.FeatureWorkAnswer.Depth.NO_READY_ANSWER;
import static com.dsinnovators.ei.judgment.FeatureWorkAnswer.Depth.ROUTINE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ComplexityTableTest {

    private static FeatureWorkAnswer.Complexity complexityOf(
            List<FeatureWorkAnswer.Depth> depths,
            boolean problemsAffectEachOther,
            boolean mistakesStayHidden,
            boolean reachesAcrossProjects) {
        return ComplexityTable.complexityOf(depths, problemsAffectEachOther, mistakesStayHidden, reachesAcrossProjects);
    }

    @Test
    void theFiveExamplesOfTheSpecGiveTheirComplexity() {
        assertEquals(LOW, complexityOf(List.of(ROUTINE, ROUTINE, ROUTINE, ROUTINE), false, false, false));
        assertEquals(MEDIUM, complexityOf(List.of(ANSWER_EXISTS, ANSWER_EXISTS), false, false, false));
        assertEquals(HIGH, complexityOf(List.of(ANSWER_EXISTS, ANSWER_EXISTS, ANSWER_EXISTS), true, true, false));
        assertEquals(
                HIGH,
                complexityOf(List.of(ANSWER_EXISTS, ANSWER_EXISTS, ANSWER_EXISTS, ANSWER_EXISTS), true, true, false));
        assertEquals(HIGH, complexityOf(List.of(NO_READY_ANSWER, ANSWER_EXISTS, ROUTINE), false, false, false));
    }

    @Test
    void allRoutineProblemsGiveLow() {
        assertEquals(LOW, complexityOf(List.of(ROUTINE), false, false, false));
    }

    @Test
    void aDeepestProblemThatHasAnAnswerGivesMedium() {
        assertEquals(MEDIUM, complexityOf(List.of(ROUTINE, ANSWER_EXISTS), false, false, false));
    }

    @Test
    void aProblemWithNoReadyAnswerGivesHigh() {
        assertEquals(HIGH, complexityOf(List.of(NO_READY_ANSWER), false, false, false));
    }

    @Test
    void twoProblemsWithAnAnswerThatAffectEachOtherGiveHigh() {
        assertEquals(HIGH, complexityOf(List.of(ANSWER_EXISTS, ANSWER_EXISTS), true, false, false));
    }

    @Test
    void oneProblemWithAnAnswerStaysMediumEvenWhenTheProblemsAreSaidToAffectEachOther() {
        assertEquals(MEDIUM, complexityOf(List.of(ROUTINE, ANSWER_EXISTS), true, false, false));
    }

    @Test
    void aDesignWhoseMistakesStayHiddenGivesHigh() {
        assertEquals(HIGH, complexityOf(List.of(ROUTINE), false, true, false));
    }

    @Test
    void problemsThatReachAcrossProjectsGiveVeryHigh() {
        assertEquals(VERY_HIGH, complexityOf(List.of(ROUTINE), false, false, true));
    }
}
