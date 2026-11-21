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
}
