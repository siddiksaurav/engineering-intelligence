package com.dsinnovators.ei.scoring;

import static com.dsinnovators.ei.scoring.ScoringFixtures.settings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class GateCheckTest {

    private static final int REPOSITORY_TOTAL = 10_000;
    private static final int REPOSITORY_TESTED = 5_000;

    private final GateCheck gateCheck = new GateCheck(settings());

    @Test
    void exactlyTenPointsLowerPasses() {
        assertEquals(GateOutcome.PASS, outcomeWithPersonShare(4_000));
    }

    @Test
    void justUnderTenPointsLowerPasses() {
        assertEquals(GateOutcome.PASS, outcomeWithPersonShare(4_001));
    }

    @Test
    void justOverTenPointsLowerIsACloseMiss() {
        assertEquals(GateOutcome.CLOSE_MISS, outcomeWithPersonShare(3_999));
    }

    @Test
    void exactlyTwentyFivePointsLowerIsACloseMiss() {
        assertEquals(GateOutcome.CLOSE_MISS, outcomeWithPersonShare(2_500));
    }

    @Test
    void justOverTwentyFivePointsLowerIsAFarMiss() {
        assertEquals(GateOutcome.FAR_MISS, outcomeWithPersonShare(2_499));
    }

    @Test
    void aPersonAboveTheRepositoryPasses() {
        GateResult result = gateCheck.check(Gate.G1, numbers(9, 10, 1, 2, 3));

        assertEquals(GateOutcome.PASS, result.outcome());
        assertNull(result.note());
        assertEquals(-40.0, result.differencePoints(), 0.0001);
    }

    @Test
    void theResultCarriesTheSharesAndTheDifferenceInPoints() {
        GateResult result = gateCheck.check(Gate.G2, numbers(1, 10, 5, 10, 3));

        assertEquals(10.0, result.personSharePercent(), 0.0001);
        assertEquals(50.0, result.repositorySharePercent(), 0.0001);
        assertEquals(40.0, result.differencePoints(), 0.0001);
        assertEquals("gate: G2 far", result.note());
    }

    @Test
    void twoDevelopersAreTooFewToCompare() {
        GateResult result = gateCheck.check(Gate.G1, numbers(0, 10, 5, 10, 2));

        assertEquals(GateOutcome.UNVERIFIED, result.outcome());
        assertEquals(UnverifiedReason.TOO_FEW_DEVELOPERS, result.unverifiedReason());
        assertEquals("gate unverified: G1", result.note());
    }

    @Test
    void aPersonWithoutPullRequestsOfTheKindIsUnverified() {
        GateResult result = gateCheck.check(Gate.G2, numbers(0, 0, 5, 10, 3));

        assertEquals(GateOutcome.UNVERIFIED, result.outcome());
        assertEquals(UnverifiedReason.NO_PULL_REQUEST_OF_THE_KIND, result.unverifiedReason());
        assertNull(result.personSharePercent());
        assertNull(result.differencePoints());
    }

    private GateOutcome outcomeWithPersonShare(int personTested) {
        return gateCheck
                .check(Gate.G1, numbers(personTested, REPOSITORY_TOTAL, REPOSITORY_TESTED, REPOSITORY_TOTAL, 3))
                .outcome();
    }

    private static GateNumbers numbers(
            int personTested, int personTotal, long repositoryTested, long repositoryTotal, int developers) {
        return new GateNumbers(
                numbersUpTo(personTested),
                numbersUpTo(personTotal),
                numbersUpTo((int) repositoryTested),
                numbersUpTo((int) repositoryTotal),
                LongStream.range(0, developers).boxed().toList(),
                List.of());
    }

    private static List<Integer> numbersUpTo(int count) {
        return IntStream.range(0, count).boxed().toList();
    }
}
