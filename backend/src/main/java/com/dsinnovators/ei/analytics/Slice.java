package com.dsinnovators.ei.analytics;

/**
 * One part of a {@link Distribution}.
 *
 * @param name the bucket, for example a work type, a complexity or {@code not_judged_yet}
 * @param count what the bucket holds
 * @param share its count divided by the total of the distribution; null when the total is 0
 */
public record Slice(String name, Count count, Double share) {}
