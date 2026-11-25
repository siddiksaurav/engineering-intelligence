package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.source.openproject.WorkPackageText;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FeatureWorkInputTest {

    private static final String PROMPT_VERSION = "feature-work-complexity/v1";
    private static final FeatureWorkLimits ROOMY = new FeatureWorkLimits(1_500, 14_000);
    private static final String ITEM_URL = "https://community.openproject.org/wp/100";

    private static final FeatureWorkToJudge ITEM = new FeatureWorkToJudge(
            7,
            "feature_work:100:1",
            ITEM_URL,
            List.of(11L, 12L, 13L),
            Optional.of(100L),
            List.of(101L),
            List.of("opf/openproject#1", "opf/openproject#2", "opf/openproject#3"));

    private static final FeatureWorkToJudge ITEM_WITHOUT_WORK_ITEM = new FeatureWorkToJudge(
            8,
            "feature_work:pull_request:opf/openproject#1",
            "https://github.com/opf/openproject/pull/1",
            List.of(11L),
            Optional.empty(),
            List.of(),
            List.of("opf/openproject#1"));

    private static WorkPackageText workItem(long number, String type, String description) {
        return new WorkPackageText(
                number,
                "Work item " + number,
                type,
                Optional.of("High"),
                Optional.of("Admin section"),
                description,
                "https://community.openproject.org/wp/" + number);
    }

    private static PullRequestDigest digest() {
        return PullRequestDigest.fromAnswer(CanonicalJson.parse("""
                {
                  "workType": "feature",
                  "modulesTouched": ["core", "api"],
                  "schemaOrDataMigration": true,
                  "newIntegration": false,
                  "natureOfChange": "Adds a recurring meeting series.",
                  "testsIncluded": true,
                  "testKind": "unit",
                  "workTypeTier": {"tier": "not_applicable", "reason": ""},
                  "securityKind": "not_applicable",
                  "infraKind": "not_applicable",
                  "infraScope": "not_applicable",
                  "dependencyChanges": false,
                  "documentationScope": "not_applicable",
                  "reason": "The files show it.",
                  "facts": [
                    {"fact": "Adds a model", "filePath": "app/models/series.rb"},
                    {"fact": "Adds a migration", "filePath": ""}
                  ]
                }
                """), false);
    }

    private static FeatureWorkInput.PullRequestPart pullRequest(int number, String mergedOn, long digestId) {
        return new FeatureWorkInput.PullRequestPart(
                "opf/openproject#" + number,
                number,
                "Pull request " + number + " " + "x".repeat(150),
                LocalDate.parse(mergedOn),
                "https://github.com/opf/openproject/pull/" + number,
                "Body of " + number,
                digestId,
                digest());
    }

    private static List<FeatureWorkInput.PullRequestPart> threePullRequests() {
        return List.of(
                pullRequest(3, "2026-07-05", 23), pullRequest(1, "2026-07-02", 21), pullRequest(2, "2026-07-03", 22));
    }

    private static FeatureWorkInput inputOf(FeatureWorkLimits limits, String featureDescription) {
        return FeatureWorkInput.of(
                ITEM,
                Optional.of(workItem(100, "Feature", featureDescription)),
                List.of(workItem(101, "Implementation", "Task text")),
                threePullRequests(),
                limits,
                PROMPT_VERSION);
    }

    @Test
    void leavesOutTheNewestPullRequestsByNameWhenTheDigestsDoNotFit() {
        int fullLength = inputOf(ROOMY, "Feature text").text().length();

        FeatureWorkInput input = inputOf(new FeatureWorkLimits(1_500, fullLength - 1), "Feature text");

        assertTrue(input.judgedOnPartialInput());
        assertEquals(List.of("opf/openproject#3"), input.leftOutPullRequests());
        assertFalse(input.text().contains("[PR-3] "));
        assertTrue(input.text().contains("[PR-1] "));
        assertTrue(input.text().contains("[PR-2] "));
        assertTrue(input.text().contains("Left out for size: PR-3"));
        assertTrue(input.text().length() <= fullLength - 1);
    }

    @Test
    void leavesOutMorePullRequestsOneByOneNewestFirstAndKeepsTheOldest() {
        FeatureWorkInput input = inputOf(new FeatureWorkLimits(1_500, 10), "Feature text");

        assertEquals(List.of("opf/openproject#2", "opf/openproject#3"), input.leftOutPullRequests());
        assertTrue(input.text().contains("[PR-1] "));
    }

    @Test
    void putsThePartsInDesignOrderEachTaggedWithItsSourceKey() {
        FeatureWorkInput input = inputOf(ROOMY, "Feature text");

        String text = input.text();
        assertTrue(text.startsWith("This is Feature work."));
        int feature = text.indexOf("[WI-100] Work item 100");
        int task = text.indexOf("[WI-101] Work item 101");
        int firstPullRequest = text.indexOf("[PR-1] ");
        int secondPullRequest = text.indexOf("[PR-2] ");
        int thirdPullRequest = text.indexOf("[PR-3] ");
        assertTrue(0 < feature && feature < task && task < firstPullRequest);
        assertTrue(firstPullRequest < secondPullRequest && secondPullRequest < thirdPullRequest);
        assertTrue(text.contains("Type: Feature\nPriority: High\nParent: Admin section\nDescription:\nFeature text"));
        assertTrue(text.contains("Type: Implementation"));
        assertFalse(input.judgedOnPartialInput());
        assertEquals(List.of(), input.leftOutPullRequests());
    }

    @Test
    void givesTheDigestInShortFormWithoutSizesOrTheDiff() {
        String text = inputOf(ROOMY, "Feature text").text();

        assertTrue(text.contains("Merged: 2026-07-02"));
        assertTrue(text.contains("Digest: work type feature; modules core, api; schema or data migration yes; "
                + "new integration no; dependency changes no; test kind unit; code read in full yes"));
        assertTrue(text.contains("Nature of the change: Adds a recurring meeting series."));
        assertFalse(text.contains("Body of 1"));
    }

    @Test
    void theTextHoldsTheReasonAndTheFactsOfEachDigest() {
        String text = inputOf(ROOMY, "Feature text").text();

        int firstPullRequest = text.indexOf("[PR-1] ");
        int secondPullRequest = text.indexOf("[PR-2] ");
        int reason = text.indexOf("Digest reason: The files show it.");
        int facts = text.indexOf("Digest facts: Adds a model (app/models/series.rb); Adds a migration\n");
        assertTrue(firstPullRequest < reason && reason < facts && facts < secondPullRequest);
        assertFalse(text.toLowerCase(Locale.ROOT).contains("lines"));
        assertFalse(text.toLowerCase(Locale.ROOT).contains("additions"));
    }

    @Test
    void cutsADescriptionAtTheLimitAndSaysSo() {
        String text = inputOf(new FeatureWorkLimits(5, 14_000), "0123456789").text();

        assertTrue(text.contains("Description:\n01234\n[description cut at 5 characters]"));
        assertFalse(text.contains("56789"));
    }

    @Test
    void cutsTheTaskDescriptionsBeforeTheFeatureDescriptionAndBeforeLeavingPullRequestsOut() {
        String longText = "a".repeat(1_000);
        FeatureWorkInput roomy = FeatureWorkInput.of(
                ITEM,
                Optional.of(workItem(100, "Feature", "f".repeat(1_000))),
                List.of(workItem(101, "Implementation", longText)),
                threePullRequests(),
                ROOMY,
                PROMPT_VERSION);

        FeatureWorkInput tight = FeatureWorkInput.of(
                ITEM,
                Optional.of(workItem(100, "Feature", "f".repeat(1_000))),
                List.of(workItem(101, "Implementation", longText)),
                threePullRequests(),
                new FeatureWorkLimits(1_500, roomy.text().length() - 300),
                PROMPT_VERSION);

        assertFalse(tight.judgedOnPartialInput());
        assertTrue(tight.text().contains("f".repeat(1_000)));
        assertFalse(tight.text().contains(longText));
        assertTrue(tight.text().contains("[description cut at"));
    }

    @Test
    void anItemWithoutAWorkItemSaysSoAndGivesThePullRequestBody() {
        FeatureWorkInput input = FeatureWorkInput.of(
                ITEM_WITHOUT_WORK_ITEM,
                Optional.empty(),
                List.of(),
                List.of(pullRequest(1, "2026-07-02", 21)),
                ROOMY,
                PROMPT_VERSION);

        assertTrue(input.text().contains("No work item: the pull requests below are the whole item."));
        assertFalse(input.text().contains("[WI-"));
        assertTrue(input.text().contains("Description:\nBody of 1"));
    }

    @Test
    void theSourceLinksAreMadeFromTheKeyAndAnUnknownKeyGetsTheLinkOfTheItem() {
        FeatureWorkInput input = inputOf(ROOMY, "Feature text");

        assertEquals("https://community.openproject.org/wp/100", input.linkOf("WI-100"));
        assertEquals("https://community.openproject.org/wp/101", input.linkOf("WI-101"));
        assertEquals("https://github.com/opf/openproject/pull/2", input.linkOf("PR-2"));
        assertEquals("https://github.com/opf/openproject/pull/2", input.linkOf(" pr-2 "));
        assertEquals(ITEM_URL, input.linkOf("WI-999"));
        assertEquals(ITEM_URL, input.linkOf(""));
    }

    @Test
    void aPullRequestLeftOutIsNoSourceToLinkTo() {
        FeatureWorkInput input = inputOf(new FeatureWorkLimits(1_500, 10), "Feature text");

        assertEquals(ITEM_URL, input.linkOf("PR-3"));
    }

    @Test
    void theHashIsTheSameForTheSameInputAndChangesWithEachPartOfIt() {
        String hash = inputOf(ROOMY, "Feature text").hash();

        assertEquals(64, hash.length());
        assertEquals(hash, inputOf(ROOMY, "Feature text").hash());
        assertNotEquals(hash, inputOf(ROOMY, "Another text").hash());
        assertNotEquals(
                hash,
                FeatureWorkInput.of(
                                ITEM,
                                Optional.of(workItem(100, "Feature", "Feature text")),
                                List.of(workItem(101, "Implementation", "Task text")),
                                threePullRequests(),
                                ROOMY,
                                "feature-work-complexity/v2")
                        .hash());
        assertNotEquals(
                hash,
                withItem(new FeatureWorkToJudge(
                                8,
                                ITEM.key(),
                                ITEM_URL,
                                ITEM.sourceRecordIds(),
                                ITEM.featureNumber(),
                                ITEM.taskNumbers(),
                                ITEM.pullRequestExternalIds()))
                        .hash());
        assertNotEquals(
                hash,
                withItem(new FeatureWorkToJudge(
                                7,
                                ITEM.key(),
                                ITEM_URL,
                                List.of(11L, 12L, 14L),
                                ITEM.featureNumber(),
                                ITEM.taskNumbers(),
                                ITEM.pullRequestExternalIds()))
                        .hash());
    }

    @Test
    void aNewDigestOfAPullRequestGivesAnotherHash() {
        String hash = inputOf(ROOMY, "Feature text").hash();
        List<FeatureWorkInput.PullRequestPart> redigested = List.of(
                pullRequest(3, "2026-07-05", 99), pullRequest(1, "2026-07-02", 21), pullRequest(2, "2026-07-03", 22));

        FeatureWorkInput input = FeatureWorkInput.of(
                ITEM,
                Optional.of(workItem(100, "Feature", "Feature text")),
                List.of(workItem(101, "Implementation", "Task text")),
                redigested,
                ROOMY,
                PROMPT_VERSION);

        assertNotEquals(hash, input.hash());
    }

    private static FeatureWorkInput withItem(FeatureWorkToJudge item) {
        return FeatureWorkInput.of(
                item,
                Optional.of(workItem(100, "Feature", "Feature text")),
                List.of(workItem(101, "Implementation", "Task text")),
                threePullRequests(),
                ROOMY,
                PROMPT_VERSION);
    }
}
