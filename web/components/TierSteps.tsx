import type { Level, Tier } from "@/lib/profile";
import styles from "./TierSteps.module.css";

const TIERS: Tier[] = ["T1", "T2", "T3"];

/**
 * The three tiers as rising steps. The steps up to the working tier are filled; the step a level expects carries
 * a marker. The same figure says "works at T2, L3 expects T3" that the why line says in words.
 */
export function TierSteps({
  workingTier,
  expected,
  size = "regular",
}: {
  workingTier: Tier | null;
  expected?: { level: Level; tier: Tier };
  size?: "small" | "regular";
}) {
  const reached = workingTier === null ? -1 : TIERS.indexOf(workingTier);
  const works = workingTier === null ? "No working tier" : `Works at ${workingTier}`;
  const label = expected ? `${works}; ${expected.level} expects ${expected.tier}` : works;
  return (
    <div className={`${styles.steps} ${styles[size]}`} role="img" aria-label={label}>
      {TIERS.map((tier, index) => (
        <div
          key={tier}
          className={styles.step}
          data-tier={tier}
          data-filled={index <= reached ? "true" : undefined}
          data-expected={expected?.tier === tier ? "true" : undefined}
        >
          {expected?.tier === tier && size === "regular" && (
            <span className={styles.expects}>{expected.level} expects</span>
          )}
          <span className={styles.riser} />
          {size === "regular" && <span className={styles.tierName}>{tier}</span>}
        </div>
      ))}
    </div>
  );
}
