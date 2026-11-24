package com.dsinnovators.ei.analytics;

/**
 * How much of what an analytic counts the model has judged, so a reader sees "judged N of M"
 * beside every number (decision 286).
 *
 * @param judged the pull requests, work items or comments that have their judgment
 * @param total all the pull requests, work items or comments the analytic counts
 */
public record Coverage(long judged, long total) {}
