package com.dsinnovators.ei.source.openproject;

/**
 * A pull request that OpenProject lists on a work package.
 *
 * @param repository {@code owner/name}
 * @param number the number in that repository
 * @param merged whether OpenProject knows it as merged
 */
public record ListedPullRequest(String repository, int number, boolean merged) {}
