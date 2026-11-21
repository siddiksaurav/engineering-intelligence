package com.dsinnovators.ei.judgment;

/**
 * How much of a pull request's review comments goes into one Stage 2 call.
 *
 * @param maxDescriptionCharacters where the pull request body is cut
 * @param maxHunkCharacters how much of the tail of a diff hunk is sent
 * @param maxCommentCharacters where a comment body is cut
 * @param maxRepliedCommentCharacters where the comment that a reply answers is cut
 * @param maxReviewCharacters the budget of one call (decision 169)
 */
record ReviewCommentLimits(
        int maxDescriptionCharacters,
        int maxHunkCharacters,
        int maxCommentCharacters,
        int maxRepliedCommentCharacters,
        int maxReviewCharacters) {

    ReviewCommentLimits {
        requireMoreThanZero(maxDescriptionCharacters, maxHunkCharacters, maxCommentCharacters);
        requireMoreThanZero(maxRepliedCommentCharacters, maxReviewCharacters);
        long largestSingleComment = (long) maxDescriptionCharacters
                + maxHunkCharacters
                + maxCommentCharacters
                + maxRepliedCommentCharacters;
        if (largestSingleComment > maxReviewCharacters) {
            throw new IllegalArgumentException("One comment with its pull request part can take "
                    + largestSingleComment + " characters, more than the " + maxReviewCharacters
                    + " of one review call");
        }
    }

    private static void requireMoreThanZero(int... limits) {
        for (int limit : limits) {
            if (limit <= 0) {
                throw new IllegalArgumentException("Review comment limits must be more than zero, was " + limit);
            }
        }
    }
}
