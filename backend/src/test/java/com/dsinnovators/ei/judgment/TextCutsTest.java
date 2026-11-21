package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TextCutsTest {

    @Test
    void leavesATextThatFitsAsItIs() {
        assertEquals("abcd", TextCuts.cut("abcd", 4));
    }

    @Test
    void cutsALongTextAndNamesTheCut() {
        assertEquals("ab\n[description cut at 2 characters]", TextCuts.cut("abcd", 2));
    }

    @Test
    void neverSplitsASurrogatePair() {
        assertEquals("a\n[description cut at 2 characters]", TextCuts.cut("a😀b", 2));
    }

    @Test
    void aCutOfZeroCharactersKeepsOnlyTheLine() {
        assertEquals("\n[description cut at 0 characters]", TextCuts.cut("abcd", 0));
    }

    @Test
    void cutsAnotherKindOfTextUnderItsOwnName() {
        assertEquals("ab\n[comment cut at 2 characters]", TextCuts.cut("abcd", 2, "comment"));
    }

    @Test
    void keepsTheTailOfALongTextAndNamesTheCut() {
        assertEquals(
                "[diff hunk cut: only the last 2 characters are shown]\ncd", TextCuts.tail("abcd", 2, "diff hunk"));
    }

    @Test
    void leavesATailThatFitsAsItIs() {
        assertEquals("abcd", TextCuts.tail("abcd", 4, "diff hunk"));
    }

    @Test
    void theTailNeverStartsInTheMiddleOfASurrogatePair() {
        assertEquals("[diff hunk cut: only the last 2 characters are shown]\nb", TextCuts.tail("a😀b", 2, "diff hunk"));
    }
}
