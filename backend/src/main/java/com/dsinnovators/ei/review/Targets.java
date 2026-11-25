package com.dsinnovators.ei.review;

/** The shares the blind check measures the AI against, from the setting {@code blind_check.targets}. */
public record Targets(double exactMatch, double withinOneStep, double reasonRight) {}
