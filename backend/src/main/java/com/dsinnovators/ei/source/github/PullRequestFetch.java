package com.dsinnovators.ei.source.github;

/** What fetching a pull request by number found. */
sealed interface PullRequestFetch {

    record Found(FetchedPullRequest pullRequest) implements PullRequestFetch {}

    /** The repository has no pull request with that number. */
    record NotFound() implements PullRequestFetch {}

    /** The pull request exists but was not merged. */
    record NotMerged() implements PullRequestFetch {}
}
