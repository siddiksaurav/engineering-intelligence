package com.dsinnovators.ei.judgment;

import com.dsinnovators.ei.source.github.PullRequestDiff;
import java.util.List;

/**
 * Gives the facts of a digest their links. The system makes every link, never the model (decision
 * 136): a fact about a changed file links to the files page of the pull request, any other fact to
 * the pull request.
 */
final class DigestFacts {

    private DigestFacts() {}

    static List<JudgmentFact> of(List<PullRequestDigest.Fact> facts, String pullRequestUrl, PullRequestDiff diff) {
        return facts.stream().map(fact -> linked(fact, pullRequestUrl, diff)).toList();
    }

    // A path the pull request does not have is a mistake of the model: the fact keeps the pull request link.
    private static JudgmentFact linked(PullRequestDigest.Fact fact, String pullRequestUrl, PullRequestDiff diff) {
        boolean namesAChangedFile =
                diff.files().stream().anyMatch(file -> file.path().equals(fact.filePath()));
        if (namesAChangedFile) {
            return new JudgmentFact(fact.text() + " (" + fact.filePath() + ")", pullRequestUrl + "/files");
        }
        return new JudgmentFact(fact.text(), pullRequestUrl);
    }
}
