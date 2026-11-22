import { RingMeter } from "@/components/charts/RingMeter";
import { Panel } from "@/components/Panel";
import { NOT_JUDGED_COLOR, SINGLE_HUE_COLOR } from "@/lib/palette";
import type { Analytics, ReviewCommentBreakdown, TestShare } from "@/lib/profile";
import { DistributionChart, ShareRow, shareOf } from "./Bars";
import { Drill } from "./Drill";
import type { SummariesById } from "./ItemReferences";
import styles from "./profile.module.css";

type WithSummaries = { summariesById: SummariesById };

/** The five analytics and the hygiene number as charts, each number opening to its items and pull requests. */
export function AnalyticsSection({ analytics, summariesById }: { analytics: Analytics } & WithSummaries) {
  const { reviewParticipation, reviewFindingsReceived, testingContribution, hygiene } = analytics;
  return (
    <section className={styles.analytics} aria-label="Analytics">
      <Panel title="Work classification" note="The person's merged pull requests by work type.">
        <DistributionChart
          title="Work types"
          distribution={analytics.workClassification}
          form="ranked"
          summariesById={summariesById}
        />
      </Panel>
      <Panel title="Work complexity" note="Feature work by complexity, next to the whole repository.">
        <div className={styles.sideBySide}>
          <DistributionChart
            title="Person"
            distribution={analytics.workComplexity.person}
            form="segmented"
            summariesById={summariesById}
          />
          <DistributionChart
            title="Repository"
            distribution={analytics.workComplexity.repository}
            form="segmented"
            summariesById={summariesById}
          />
        </div>
      </Panel>
      <Panel title="Review participation" note="Reviews the person gave on pull requests of others.">
        <div className={styles.facts}>
          <Drill label="PRs reviewed" count={reviewParticipation.pullRequestsReviewed} summariesById={summariesById} />
          <Drill label="Authors reviewed" count={reviewParticipation.authorsReviewed} summariesById={summariesById} />
          <PerPullRequest
            label="Substantive comments per PR reviewed"
            value={reviewParticipation.substantivePerPullRequestReviewed}
          />
        </div>
        <CommentBreakdown breakdown={reviewParticipation.comments} summariesById={summariesById} />
      </Panel>
      <Panel title="Review findings received" note="Review comments others wrote on the person's pull requests.">
        <div className={styles.facts}>
          <Drill label="Own PRs merged" count={reviewFindingsReceived.pullRequests} summariesById={summariesById} />
          <PerPullRequest
            label="Substantive comments per PR"
            value={reviewFindingsReceived.substantivePerPullRequest}
          />
        </div>
        <CommentBreakdown breakdown={reviewFindingsReceived.comments} summariesById={summariesById} />
      </Panel>
      <Panel title="Testing contribution" note="The share of the person's pull requests that came with tests.">
        <div className={styles.sideBySide}>
          <TestShareChart
            title="Feature and fix PRs"
            testedLabel="Tested"
            share={testingContribution.featureAndFixPullRequests}
            summariesById={summariesById}
          />
          <TestShareChart
            title="Bug fix PRs"
            testedLabel="Bug fix PRs tested"
            share={testingContribution.bugFixPullRequests}
            summariesById={summariesById}
          />
        </div>
        <div className={styles.facts}>
          <Drill label="PRs without digest" count={testingContribution.withoutDigest} summariesById={summariesById} />
        </div>
      </Panel>
      <Panel
        title="Hygiene"
        note="The repository's share of pull requests linked to a work item. A number of the project, not of the person."
      >
        <div className={styles.ringBlock}>
          <RingMeter label="Linked to a work item" share={hygiene.share} />
          <div className={styles.ringRows}>
            <ShareRow
              label="Linked to a work item"
              count={hygiene.linked}
              share={hygiene.share}
              color={SINGLE_HUE_COLOR}
              form="legend"
              summariesById={summariesById}
            />
            <ShareRow
              label="Not linked"
              count={hygiene.notLinked}
              share={shareOf(hygiene.notLinked, hygiene.total)}
              color={NOT_JUDGED_COLOR}
              form="legend"
              summariesById={summariesById}
            />
          </div>
        </div>
      </Panel>
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
      <div className={styles.facts}>
        <Drill label="Substantive comments" count={breakdown.substantive} summariesById={summariesById} />
        <Drill label="Not judged yet" count={breakdown.notJudgedYet} summariesById={summariesById} />
      </div>
      <DistributionChart
        title="By comment type"
        distribution={breakdown.byCommentType}
        form="ranked"
        summariesById={summariesById}
      />
      <DistributionChart
        title="By tier"
        distribution={breakdown.byTier}
        form="segmented"
        summariesById={summariesById}
      />
    </>
  );
}

function TestShareChart({
  title,
  testedLabel,
  share,
  summariesById,
}: { title: string; testedLabel: string; share: TestShare } & WithSummaries) {
  return (
    <div className={styles.distribution}>
      <h3 className={styles.chartTitle}>{title}</h3>
      <div className={styles.ringBlock}>
        <RingMeter label={testedLabel} share={share.share} />
        <div className={styles.ringRows}>
          <Drill label={testedLabel} count={share.tested} summariesById={summariesById} swatch={SINGLE_HUE_COLOR} />
          <Drill label="Not tested" count={share.untested} summariesById={summariesById} swatch="var(--sky-100)" />
        </div>
      </div>
      <DistributionChart
        title="Tested by tier"
        distribution={share.testedByTier}
        form="segmented"
        summariesById={summariesById}
      />
    </div>
  );
}
