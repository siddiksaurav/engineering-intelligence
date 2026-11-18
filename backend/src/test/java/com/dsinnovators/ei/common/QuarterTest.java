package com.dsinnovators.ei.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class QuarterTest {

    @Test
    void parsesTheTextForm() {
        assertEquals(new Quarter(2026, 3), Quarter.parse("2026-Q3"));
    }

    @Test
    void printsTheTextForm() {
        assertEquals("2026-Q3", new Quarter(2026, 3).toString());
    }

    @Test
    void knowsItsFirstAndLastDay() {
        assertEquals(LocalDate.of(2026, 7, 1), Quarter.parse("2026-Q3").firstDay());
        assertEquals(LocalDate.of(2026, 9, 30), Quarter.parse("2026-Q3").lastDay());
        assertEquals(LocalDate.of(2026, 12, 31), Quarter.parse("2026-Q4").lastDay());
    }

    @Test
    void containsItsFirstAndLastDayAndNothingOutside() {
        Quarter q3 = Quarter.parse("2026-Q3");
        assertTrue(q3.contains(LocalDate.of(2026, 7, 1)));
        assertTrue(q3.contains(LocalDate.of(2026, 9, 30)));
        assertFalse(q3.contains(LocalDate.of(2026, 6, 30)));
        assertFalse(q3.contains(LocalDate.of(2026, 10, 1)));
    }

    @Test
    void isFoundFromADate() {
        assertEquals(Quarter.parse("2026-Q4"), Quarter.of(LocalDate.of(2026, 10, 1)));
    }

    @Test
    void previousAndNextCrossTheYear() {
        assertEquals(Quarter.parse("2025-Q4"), Quarter.parse("2026-Q1").previous());
        assertEquals(Quarter.parse("2027-Q1"), Quarter.parse("2026-Q4").next());
    }

    @Test
    void ordersByTime() {
        assertTrue(Quarter.parse("2026-Q3").compareTo(Quarter.parse("2026-Q4")) < 0);
        assertTrue(Quarter.parse("2026-Q4").compareTo(Quarter.parse("2027-Q1")) < 0);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "2026Q3", "2026-q3", "2026-Q5", "2026-Q0", " 2026-Q3"})
    void rejectsAnyOtherTextForm(String text) {
        assertThrows(IllegalArgumentException.class, () -> Quarter.parse(text));
    }

    @Test
    void aMissingDateIsRejectedByName() {
        Quarter quarter = Quarter.parse("2026-Q3");

        assertEquals("date", assertThrows(NullPointerException.class, () -> Quarter.of(null)).getMessage());
        assertEquals("date", assertThrows(NullPointerException.class, () -> quarter.contains(null)).getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 5})
    void rejectsANumberOutsideOneToFour(int number) {
        assertThrows(IllegalArgumentException.class, () -> new Quarter(2026, number));
    }
}
