import { StackedBarChart } from "@/components/charts/StackedBarChart";
import { Panel } from "@/components/Panel";
import { sliceCount } from "@/lib/counts";
import { orderedColor } from "@/lib/palette";
import type { ReviewCommentBreakdown, Tier } from "@/lib/profile";
import { TIER_TABLE } from "@/lib/tierTable";
import { Drill } from "./Drill";
import type { SummariesById } from "./ItemReferences";
import styles from "./profile.module.css";

const TIERS: Tier[] = ["T1", "T2", "T3"];

const TIER_WORDS = TIER_TABLE.review_comment_written?.tiers;

/**
 * Section 5: every review comment the person wrote, the ones judged substantive by tier as a stacked bar, and the
 * ones left out with their counts, "N not substantive, M not judged yet" (decision 297).
 */
export function ReviewsSection({
  comments,
  summariesById,
}: {
  comments: ReviewCommentBreakdown;
  summariesById: SummariesById;
}) {
  const { byTier, notSubstantive, notJudgedYet } = comments;
  return (
    <Panel
      title="5 · Reviews"
      note={`${comments.coverage.total} review comments written, by tier of the ones judged substantive.`}
    >
      <StackedBarChart
        label="Review comments by tier"
        segments={TIERS.map((tier) => ({ key: tier, label: tier, color: orderedColor(tier) ?? "" }))}
        bars={[
          {
            key: "substantive",
            label: "Judged substantive",
            values: Object.fromEntries(TIERS.map((tier) => [tier, sliceCount(byTier, tier).count])),
          },
        ]}
      />
      <p className={styles.reading}>
        {notSubstantive.count} not substantive, {notJudgedYet.count} not judged yet.
      </p>
      <div className={styles.rows}>
        {TIERS.map((tier) => (
          <Drill
            key={tier}
            label={`${tier} · ${TIER_WORDS?.[tier] ?? tier}`}
            count={sliceCount(byTier, tier)}
            swatch={orderedColor(tier)}
            summariesById={summariesById}
          />
        ))}
        <Drill label="Not substantive" count={notSubstantive} summariesById={summariesById} />
        <Drill label="Not judged yet" count={notJudgedYet} summariesById={summariesById} />
      </div>
    </Panel>
  );
}
