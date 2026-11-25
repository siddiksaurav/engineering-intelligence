package com.dsinnovators.ei.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.analytics.AnalyticsRows;
import com.dsinnovators.ei.common.Quarter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class GateNumbersQueryTest extends DatabaseTest {

    private static final Quarter QUARTER = Quarter.parse("2026-Q3");

    @Autowired
    private GateNumbersQuery gateNumbersQuery;

    private AnalyticsRows rows;
    private long ada;
    private long grace;
    private long linus;

    @BeforeEach
    void storePersons() {
        rows = new AnalyticsRows(jdbc);
        ada = rows.person(11, "ada");
        grace = rows.person(12, "grace");
        linus = rows.person(13, "linus");
    }

    @Test
    void countsFeatureAndEnhancementPullRequestsWithATestChange() {
        for (int number = 1; number <= 5; number++) {
            rows.pullRequest(number, 1, 11, "ada", "2026-07-0" + number + "T10:00:00Z");
        }
        rows.digest(1, "feature", null);
        rows.testChange(ada, 1, "2026-07-01");
        rows.digest(2, "enhancement", null);
        rows.digest(3, "fix", null);
        rows.testChange(ada, 3, "2026-07-03");
        rows.digest(4, "docs", null);
        rows.pullRequest(6, 1, 12, "grace", "2026-07-06T10:00:00Z");
        rows.digest(6, "feature", null);
        rows.testChange(grace, 6, "2026-07-06");
        rows.pullRequest(7, 1, 12, "grace", "2026-07-07T10:00:00Z");
        rows.digest(7, "feature", null);
        rows.pullRequest(8, 1, 13, "linus", "2026-07-08T10:00:00Z");
        rows.digest(8, "enhancement", null);
        rows.pullRequest(9, 1, 13, "linus", "2026-06-30T10:00:00Z");
        rows.digest(9, "feature", null);
        rows.botPullRequest(10, 15, "dependabot[bot]", "2026-07-09T10:00:00Z");
        rows.digest(10, "feature", null);

        Map<Long, Map<Gate, GateNumbers>> numbers =
                gateNumbersQuery.of(QUARTER, ScoringFixtures.settings(), List.of(ada, grace, linus));

        GateNumbers adaG1 = numbers.get(ada).get(Gate.G1);
        assertEquals(List.of(1), adaG1.personTestedPullRequests());
        assertEquals(List.of(1, 2), adaG1.personPullRequests());
        assertEquals(List.of(5), adaG1.pullRequestsWithoutDigest());
        assertEquals(List.of(1, 6), adaG1.repositoryTestedPullRequests());
        assertEquals(List.of(1, 2, 6, 7, 8), adaG1.repositoryPullRequests());
        assertEquals(List.of(ada, grace, linus), adaG1.developersInGroup());
        GateNumbers graceG1 = numbers.get(grace).get(Gate.G1);
        assertEquals(List.of(6), graceG1.personTestedPullRequests());
        assertEquals(List.of(6, 7), graceG1.personPullRequests());
        assertEquals(List.of(), graceG1.pullRequestsWithoutDigest());
    }

    @Test
    void countsThePullRequestsOfBugFixItemsOfBothKindsWithATestChange() {
        for (int number = 1; number <= 5; number++) {
            rows.pullRequest(number, 1, 11, "ada", "2026-07-0" + number + "T10:00:00Z");
        }
        rows.item("bug_fix", ada, "2026-07-01", false, null, 1);
        rows.testChange(ada, 1, "2026-07-01");
        rows.item("bug_fix", ada, "2026-07-02", false, null, 2, 3);
        rows.item("bug_fix", ada, "2026-07-03", false, null, 3);
        long superseded = rows.item("bug_fix", ada, "2026-07-04", false, null, 4);
        rows.item("bug_fix", ada, "2026-07-04", true, superseded, 4);
        rows.item("bug_fix", ada, "2026-10-01", false, null, 5);
        rows.item("bug_fix", grace, "2026-07-06", false, null, 5);
        rows.testChange(grace, 5, "2026-07-06");
        rows.item("security", grace, "2026-07-07", false, null, 5);

        Map<Long, Map<Gate, GateNumbers>> numbers =
                gateNumbersQuery.of(QUARTER, ScoringFixtures.settings(), List.of(ada, grace, linus));

        GateNumbers adaG2 = numbers.get(ada).get(Gate.G2);
        assertEquals(List.of(1), adaG2.personTestedPullRequests());
        assertEquals(List.of(1, 2, 3), adaG2.personPullRequests());
        assertEquals(List.of(), adaG2.pullRequestsWithoutDigest());
        assertEquals(List.of(1, 5), adaG2.repositoryTestedPullRequests());
        assertEquals(List.of(1, 2, 3, 5), adaG2.repositoryPullRequests());
        assertEquals(List.of(ada, grace), adaG2.developersInGroup());
        GateNumbers linusG2 = numbers.get(linus).get(Gate.G2);
        assertEquals(List.of(), linusG2.personPullRequests());
        assertEquals(List.of(), linusG2.personTestedPullRequests());
    }

    @Test
    void countsADeveloperInTheGroupWithEnoughPullRequestsOfTheKind() {
        for (int number = 1; number <= 3; number++) {
            rows.pullRequest(number, 1, 11, "ada", "2026-07-0" + number + "T10:00:00Z");
            rows.digest(number, "feature", null);
        }
        rows.pullRequest(4, 1, 12, "grace", "2026-07-04T10:00:00Z");
        rows.digest(4, "feature", null);
        ScoringSettings settings = ScoringFixtures.settings();
        ScoringSettings strict = new ScoringSettings(
                settings.presenceThreshold(),
                settings.featureWorkWeights(),
                settings.frequentEvidenceTypes(),
                settings.t3ItemsNeededAtLevel3(),
                settings.gateLimits(),
                settings.gateScored(),
                settings.gateMinimumDevelopers(),
                2);

        Map<Long, Map<Gate, GateNumbers>> numbers = gateNumbersQuery.of(QUARTER, strict, List.of(ada, grace));

        assertEquals(List.of(ada), numbers.get(ada).get(Gate.G1).developersInGroup());
        assertEquals(4, numbers.get(grace).get(Gate.G1).repositoryTotal());
        assertEquals(List.of(1, 2, 3, 4), numbers.get(grace).get(Gate.G1).repositoryPullRequests());
    }
}
