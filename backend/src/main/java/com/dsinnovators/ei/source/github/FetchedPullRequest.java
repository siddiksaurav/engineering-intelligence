package com.dsinnovators.ei.source.github;

import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * A merged pull request with every part read from GitHub, each as GitHub returned it, without the
 * paging fields (F02 design 6.2).
 *
 * @param repository {@code owner/name} as GitHub spells it
 * @param payload the pull request's own fields, with all its {@code labels} and {@code files} as lists
 * @param reviewComments the inline comments of every review thread
 * @param pullRequestComments the comments in the conversation
 * @param diffFiles the REST list of files with their patches
 * @param diffTruncated true when GitHub's list of files stops before the pull request's last file
 */
record FetchedPullRequest(
        String repository,
        int number,
        String url,
        JsonNode payload,
        List<JsonNode> reviews,
        List<JsonNode> reviewComments,
        List<JsonNode> pullRequestComments,
        List<JsonNode> diffFiles,
        boolean diffTruncated) {}
