import type { Analytics, ReviewCommentBreakdown, TestShare } from "@/lib/profile";
import { BarRow, DistributionBars, shareOf } from "./Bars";
import { Drill } from "./Drill";
import type { SummariesById } from "./ItemReferences";
import styles from "./profile.module.css";

type WithSummaries = { summariesById: SummariesById };

/** The five analytics and the hygiene number as bars, each number opening to its items and pull requests. */
export function AnalyticsSection({ analytics, summariesById }: { analytics: Analytics } & WithSummaries) {
  const { reviewParticipation, reviewFindingsReceived } = analytics;
  return (
    <section className={styles.analytics} aria-label="Analytics">
      <article className={styles.panel}>
        <h2 className={styles.panelTitle}>Work classification</h2>
        <DistributionBars
          title="Work types"
          distribution={analytics.workClassification}
          summariesById={summariesById}
        />
      </article>
      <article className={styles.panel}>
        <h2 className={styles.panelTitle}>Work complexity</h2>
        <div className={styles.sideBySide}>
          <DistributionBars
            title="Person"
            distribution={analytics.workComplexity.person}
            summariesById={summariesById}
          />
          <DistributionBars
            title="Repository"
            distribution={analytics.workComplexity.repository}
            summariesById={summariesById}
          />
        </div>
      </article>
      <article className={styles.panel}>
        <h2 className={styles.panelTitle}>Review participation</h2>
        <Drill label="PRs reviewed" count={reviewParticipation.pullRequestsReviewed} summariesById={summariesById} />
        <Drill label="Authors reviewed" count={reviewParticipation.authorsReviewed} summariesById={summariesById} />
        <PerPullRequest
          label="Substantive comments per PR reviewed"
          value={reviewParticipation.substantivePerPullRequestReviewed}
        />
        <CommentBreakdown breakdown={reviewParticipation.comments} summariesById={summariesById} />
      </article>
      <article className={styles.panel}>
        <h2 className={styles.panelTitle}>Review findings received</h2>
        <Drill label="Own PRs merged" count={reviewFindingsReceived.pullRequests} summariesById={summariesById} />
        <PerPullRequest label="Substantive comments per PR" value={reviewFindingsReceived.substantivePerPullRequest} />
        <CommentBreakdown breakdown={reviewFindingsReceived.comments} summariesById={summariesById} />
      </article>
      <article className={styles.panel}>
        <h2 className={styles.panelTitle}>Testing contribution</h2>
        <TestShareBars
          title="Feature and fix PRs"
          testedLabel="Tested"
          share={analytics.testingContribution.featureAndFixPullRequests}
          summariesById={summariesById}
        />
        <Drill
          label="PRs without digest"
          count={analytics.testingContribution.withoutDigest}
          summariesById={summariesById}
        />
        <TestShareBars
          title="Bug fix PRs"
          testedLabel="Bug fix PRs tested"
          share={analytics.testingContribution.bugFixPullRequests}
          summariesById={summariesById}
        />
      </article>
      <article className={styles.panel}>
        <h2 className={styles.panelTitle}>Hygiene</h2>
        <BarRow
          label="Linked to a work item"
          count={analytics.hygiene.linked}
          share={analytics.hygiene.share}
          summariesById={summariesById}
        />
        <BarRow
          label="Not linked"
          count={analytics.hygiene.notLinked}
          share={shareOf(analytics.hygiene.notLinked, analytics.hygiene.total)}
          color="var(--not-judged)"
          summariesById={summariesById}
        />
      </article>
    </section>
  );
}

function PerPullRequest({ label, value }: { label: string; value: number | null }) {
  return (
    <p className={styles.perPullRequest}>
      {label}: <strong>{value === null ? "no PRs" : Number(value.toFixed(2))}</strong>
    </p>
  );
}

function CommentBreakdown({ breakdown, summariesById }: { breakdown: ReviewCommentBreakdown } & WithSummaries) {
  return (
    <>
      <Drill label="Substantive comments" count={breakdown.substantive} summariesById={summariesById} />
      <DistributionBars title="By comment type" distribution={breakdown.byCommentType} summariesById={summariesById} />
      <DistributionBars title="By tier" distribution={breakdown.byTier} summariesById={summariesById} />
      <Drill label="Not judged yet" count={breakdown.notJudgedYet} summariesById={summariesById} />
    </>
  );
}

function TestShareBars({
  title,
  testedLabel,
  share,
  summariesById,
}: { title: string; testedLabel: string; share: TestShare } & WithSummaries) {
  return (
    <div className={styles.distribution}>
      <h4>{title}</h4>
      <BarRow label={testedLabel} count={share.tested} share={share.share} summariesById={summariesById} />
      <Drill label="Not tested" count={share.untested} summariesById={summariesById} />
      <DistributionBars title="Tested by tier" distribution={share.testedByTier} summariesById={summariesById} />
    </div>
  );
}
