package com.dsinnovators.ei.analytics;

import com.dsinnovators.ei.common.Quarter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** The analytics of every developer together, for the overview of one quarter (decision 300). */
@Service
public class TeamAnalytics {

    private final WorkClassification workClassification;
    private final WorkComplexity workComplexity;
    private final RepositoryHygiene repositoryHygiene;

    TeamAnalytics(
            WorkClassification workClassification, WorkComplexity workComplexity, RepositoryHygiene repositoryHygiene) {
        this.workClassification = workClassification;
        this.workComplexity = workComplexity;
        this.repositoryHygiene = repositoryHygiene;
    }

    /**
     * The numbers of the team.
     *
     * @param workClassification the merged pull requests by work type
     * @param workComplexity the Feature work items by complexity
     * @param hygiene how many pull requests of the repository are linked to a work item
     */
    public record Result(
            WorkClassification.Result workClassification,
            WorkComplexity.TeamResult workComplexity,
            RepositoryHygiene.Result hygiene) {}

    /** One read-only repeatable-read transaction, so that the numbers describe one snapshot, as {@link DeveloperAnalytics} does. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result of(Quarter quarter) {
        return new Result(
                workClassification.ofTeam(quarter), workComplexity.ofTeam(quarter), repositoryHygiene.of(quarter));
    }
}
