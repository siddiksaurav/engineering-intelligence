package com.dsinnovators.ei.common;

import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A calendar quarter. Its text form is {@code YYYY-Qn}, for example {@code 2026-Q3}; text forms
 * sort in time order. Dates are plain dates: a caller that starts from a timestamp converts it in
 * UTC.
 */
public record Quarter(int year, int number) implements Comparable<Quarter> {

    private static final Pattern TEXT_FORM = Pattern.compile("([0-9]{4})-Q([1-4])");

    public Quarter {
        if (number < 1 || number > 4) {
            throw new IllegalArgumentException("Quarter number must be 1 to 4, got " + number);
        }
        if (year < 1000 || year > 9999) {
            throw new IllegalArgumentException("Year must have four digits, got " + year);
        }
    }

    public static Quarter parse(String text) {
        Matcher matcher = text == null ? null : TEXT_FORM.matcher(text);
        if (matcher == null || !matcher.matches()) {
            throw new IllegalArgumentException("Not a quarter in the form YYYY-Qn: " + text);
        }
        return new Quarter(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }

    public static Quarter of(LocalDate date) {
        Objects.requireNonNull(date, "date");
        return new Quarter(date.getYear(), (date.getMonthValue() - 1) / 3 + 1);
    }

    public LocalDate firstDay() {
        return LocalDate.of(year, (number - 1) * 3 + 1, 1);
    }

    public LocalDate lastDay() {
        return firstDay().plusMonths(3).minusDays(1);
    }

    public boolean contains(LocalDate date) {
        Objects.requireNonNull(date, "date");
        return !date.isBefore(firstDay()) && !date.isAfter(lastDay());
    }

    public Quarter previous() {
        return number == 1 ? new Quarter(year - 1, 4) : new Quarter(year, number - 1);
    }

    public Quarter next() {
        return number == 4 ? new Quarter(year + 1, 1) : new Quarter(year, number + 1);
    }

    @Override
    public int compareTo(Quarter other) {
        return year != other.year ? Integer.compare(year, other.year) : Integer.compare(number, other.number);
    }

    @Override
    public String toString() {
        return year + "-Q" + number;
    }
}
