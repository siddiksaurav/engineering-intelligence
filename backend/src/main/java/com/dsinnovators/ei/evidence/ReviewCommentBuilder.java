package com.dsinnovators.ei.evidence;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.identity.BotAccounts;
import com.dsinnovators.ei.identity.PersonDirectory;
import com.dsinnovators.ei.source.RawRecord;
import com.dsinnovators.ei.source.github.GitHubAuthor;
import com.dsinnovators.ei.source.github.PullRequestFacts;
import com.dsinnovators.ei.source.github.ReviewCommentFacts;
import com.dsinnovators.ei.source.github.StoredPullRequests;
import com.dsinnovators.ei.source.github.StoredReviewComments;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Builds the Review comment written items by rule: one per stored inline review comment of a human
 * who is not the pull request's author, replies included, on any pull request (decisions 111, 119,
 * 120). The item is dated by the merge day of the pull request (decision 121).
 */
@Component
class ReviewCommentBuilder {

    private static final String KEY_PREFIX = "review_comment_written:";

    private final StoredReviewComments storedReviewComments;
    private final StoredPullRequests storedPullRequests;
    private final BotAccounts botAccounts;
    private final PersonDirectory personDirectory;

    ReviewCommentBuilder(
            StoredReviewComments storedReviewComments,
            StoredPullRequests storedPullRequests,
            BotAccounts botAccounts,
            PersonDirectory personDirectory) {
        this.storedReviewComments = storedReviewComments;
        this.storedPullRequests = storedPullRequests;
        this.botAccounts = botAccounts;
        this.personDirectory = personDirectory;
    }

    /** @param botListQuarter the quarter whose bot list applies */
    BuildResult build(Quarter botListQuarter) {
        Map<String, Optional<RawRecord>> pullRequests = new HashMap<>();
        List<BuiltItem> items = new ArrayList<>();
        List<BuilderFailure> failures = new ArrayList<>();
        int botRecordsSkipped = 0;
        int recordsWithoutAuthor = 0;
        for (RawRecord record : storedReviewComments.all()) {
            ReviewCommentFacts comment = ReviewCommentFacts.of(record);
            Optional<GitHubAuthor> maybeAuthor = comment.author();
            if (maybeAuthor.isEmpty()) {
                recordsWithoutAuthor++;
                continue;
            }
            GitHubAuthor author = maybeAuthor.get();
            if (botAccounts.isBot(author.login(), author.typename(), botListQuarter)) {
                botRecordsSkipped++;
                continue;
            }
            Optional<RawRecord> pullRequestRecord = pullRequests.computeIfAbsent(
                    comment.pullRequestExternalId(),
                    externalId -> storedPullRequests.find(comment.repository(), comment.pullRequestNumber()));
            if (pullRequestRecord.isEmpty()) {
                failures.add(new BuilderFailure(
                        key(comment), "pull request " + comment.pullRequestExternalId() + " is not stored"));
                continue;
            }
            PullRequestFacts pullRequest = PullRequestFacts.of(pullRequestRecord.get());
            if (isByPullRequestAuthor(author, pullRequest)) {
                continue;
            }
            Optional<Long> personId = personDirectory.personOfGitHubAccount(author.databaseId());
            if (personId.isEmpty()) {
                failures.add(new BuilderFailure(key(comment), "no person for GitHub account " + author.login()));
                continue;
            }
            items.add(item(comment, pullRequest, personId.get()));
        }
        return new BuildResult(items, failures, botRecordsSkipped, recordsWithoutAuthor);
    }

    private static boolean isByPullRequestAuthor(GitHubAuthor commentAuthor, PullRequestFacts pullRequest) {
        return pullRequest
                .author()
                .filter(author -> author.databaseId() == commentAuthor.databaseId())
                .isPresent();
    }

    private BuiltItem item(ReviewCommentFacts comment, PullRequestFacts pullRequest, long personId) {
        ObjectNode commentNode = JsonNodeFactory.instance.objectNode();
        commentNode.put("databaseId", comment.databaseId());
        commentNode.put("url", comment.url());
        commentNode.put("path", comment.path());
        commentNode.put("createdAt", comment.createdAt().toString());
        commentNode.put("isReply", comment.isReply());
        ObjectNode fields = JsonNodeFactory.instance.objectNode();
        fields.set("pullRequest", ItemFields.pullRequest(pullRequest));
        // A bot never becomes a person, so a bot author has none here.
        Optional<Long> authorPersonId =
                pullRequest.author().flatMap(author -> personDirectory.personOfGitHubAccount(author.databaseId()));
        authorPersonId.ifPresentOrElse(
                id -> fields.put("pullRequestAuthorPersonId", id), () -> fields.putNull("pullRequestAuthorPersonId"));
        fields.set("comment", commentNode);
        return new BuiltItem(
                key(comment),
                EvidenceType.REVIEW_COMMENT_WRITTEN,
                personId,
                null,
                pullRequest.mergedAt().atOffset(ZoneOffset.UTC).toLocalDate(),
                comment.url(),
                fields,
                List.of(
                        new EvidenceItemSource(comment.rawRecordId(), SourceRole.REVIEW_COMMENT),
                        new EvidenceItemSource(pullRequest.rawRecordId(), SourceRole.PULL_REQUEST)));
    }

    private static String key(ReviewCommentFacts comment) {
        return KEY_PREFIX + comment.databaseId();
    }
}
