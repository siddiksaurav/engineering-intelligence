package com.dsinnovators.ei.analytics;

import com.dsinnovators.ei.common.Quarter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** The five analytics of a developer and the hygiene number of the repository, for one quarter (decision 200). */
@Service
public class DeveloperAnalytics {

    private final WorkClassification workClassification;
    private final WorkComplexity workComplexity;
    private final WorkLevel workLevel;
    private final ReviewParticipation reviewParticipation;
    private final ReviewFindingsReceived reviewFindingsReceived;
    private final TestingContribution testingContribution;
    private final RepositoryHygiene repositoryHygiene;

    DeveloperAnalytics(
            WorkClassification workClassification,
            WorkComplexity workComplexity,
            WorkLevel workLevel,
            ReviewParticipation reviewParticipation,
            ReviewFindingsReceived reviewFindingsReceived,
            TestingContribution testingContribution,
            RepositoryHygiene repositoryHygiene) {
        this.workClassification = workClassification;
        this.workComplexity = workComplexity;
        this.workLevel = workLevel;
        this.reviewParticipation = reviewParticipation;
        this.reviewFindingsReceived = reviewFindingsReceived;
        this.testingContribution = testingContribution;
        this.repositoryHygiene = repositoryHygiene;
    }

    /**
     * Everything the profile shows for one developer and quarter.
     *
     * @param workClassification the merged pull requests by work type
     * @param workComplexity the Feature work items by complexity, of the person and of the repository
     * @param workLevel the share of the Feature work at T2 or above and at T3, of the person and of the rest of the repository
     * @param reviewParticipation the review work of the person
     * @param reviewFindingsReceived the review comments the person's pull requests received
     * @param testingContribution the pull requests that came with tests
     * @param hygiene how many pull requests of the repository are linked to a work item
     */
    public record Result(
            WorkClassification.Result workClassification,
            WorkComplexity.Result workComplexity,
            WorkLevel.Result workLevel,
            ReviewParticipation.Result reviewParticipation,
            ReviewFindingsReceived.Result reviewFindingsReceived,
            TestingContribution.Result testingContribution,
            RepositoryHygiene.Result hygiene) {}

    /** All the queries run in one read-only repeatable-read transaction, so that the numbers describe one snapshot of the data although model jobs write meanwhile. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result of(long personId, Quarter quarter) {
        return new Result(
                workClassification.of(personId, quarter),
                workComplexity.of(personId, quarter),
                workLevel.of(personId, quarter),
                reviewParticipation.of(personId, quarter),
                reviewFindingsReceived.of(personId, quarter),
                testingContribution.of(personId, quarter),
                repositoryHygiene.of(quarter));
    }
}
