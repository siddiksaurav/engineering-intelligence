package com.dsinnovators.ei.judgment;

/**
 * One current Review comment written item as the judgment needs it (F07 design, "Main flows" 2).
 *
 * @param itemRowId the evidence item row; the judgment is about this exact row
 * @param key the stable name of the item, such as {@code review_comment_written:1234}
 * @param commentRecordId the exact raw record version of the comment the item was built from
 * @param pullRequestRecordId the exact raw record version of the pull request the item was built from
 * @param pullRequestExternalId the pull request of the comment, such as {@code opf/openproject#24204}
 */
public record ReviewCommentToJudge(
        long itemRowId, String key, long commentRecordId, long pullRequestRecordId, String pullRequestExternalId) {}
