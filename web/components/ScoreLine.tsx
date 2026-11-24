import type { Tier } from "@/lib/profile";
import { TierChip } from "./TierChip";
import styles from "./ScoreLine.module.css";

/** A score of 2 as a square in its blue and, beside it, the tier the developer works at. */
export function ScoreLine({ score, workingTier }: { score: 0 | 1 | 2; workingTier: Tier | null }) {
  return (
    <span className={styles.line}>
      <span className={styles.score} data-score={score}>
        {score}
      </span>
      {workingTier ? <TierChip tier={workingTier} /> : <span className={styles.noTier}>no working tier</span>}
    </span>
  );
}
