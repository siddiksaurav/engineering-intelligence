package com.dsinnovators.ei.review;

/**
 * One wrong row of an imported label file.
 *
 * @param line the line of the row in the file, counted from 1 with the header as line 1
 * @param itemId the {@code item_id} cell as written, or null when it is empty
 * @param value the {@code complexity} cell as written
 * @param problem what is wrong, and what is allowed
 */
public record LabelProblem(int line, String itemId, String value, String problem) {}
