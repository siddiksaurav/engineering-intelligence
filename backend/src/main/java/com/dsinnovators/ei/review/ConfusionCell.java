package com.dsinnovators.ei.review;

/** How many items the user labeled {@code label} and the AI judged {@code ai}. */
public record ConfusionCell(String label, String ai, int count) {}
