package com.dsinnovators.ei.review;

/**
 * What an accepted label file did.
 *
 * @param labeledNow rows that stored a label
 * @param alreadyLabeled rows of an item that already had this label
 * @param notLabeledYet items of the sample that still have no label
 */
public record LabelImportResult(int labeledNow, int alreadyLabeled, int notLabeledYet) {}
