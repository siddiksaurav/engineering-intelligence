package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dsinnovators.ei.source.github.PullRequestDiff;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DigestFactsTest {

    private static final String PULL_REQUEST_URL = "https://github.com/opf/openproject/pull/7";

    private static final PullRequestDiff DIFF = new PullRequestDiff(
            9, List.of(new PullRequestDiff.File("app/models/meeting.rb", "modified", Optional.of("@@ @@"))));

    @Test
    void linksAFactAboutAChangedFileToTheFilesPageOfThePullRequest() {
        List<JudgmentFact> facts = DigestFacts.of(
                List.of(new PullRequestDigest.Fact("Guards the empty date", "app/models/meeting.rb")),
                PULL_REQUEST_URL,
                DIFF);

        assertEquals(
                List.of(new JudgmentFact("Guards the empty date (app/models/meeting.rb)", PULL_REQUEST_URL + "/files")),
                facts);
    }

    @Test
    void linksAFactWithoutAPathToThePullRequest() {
        List<JudgmentFact> facts = DigestFacts.of(
                List.of(new PullRequestDigest.Fact("The title names a crash", "")), PULL_REQUEST_URL, DIFF);

        assertEquals(List.of(new JudgmentFact("The title names a crash", PULL_REQUEST_URL)), facts);
    }

    @Test
    void keepsOnlyThePullRequestLinkForAPathThatIsNotInThePullRequest() {
        List<JudgmentFact> facts = DigestFacts.of(
                List.of(new PullRequestDigest.Fact("Touches the billing model", "app/models/billing.rb")),
                PULL_REQUEST_URL,
                DIFF);

        assertEquals(List.of(new JudgmentFact("Touches the billing model", PULL_REQUEST_URL)), facts);
    }
}
