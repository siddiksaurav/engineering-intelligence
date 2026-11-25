package com.dsinnovators.ei.review;

/** The numbers of the sample items whose judgment the model flagged as made on thin evidence, apart from the rest. */
public record ThinEvidence(int items, Rate exactMatch, Rate reasonRight) {}
