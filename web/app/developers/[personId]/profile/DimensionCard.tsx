import { TierSteps } from "@/components/TierSteps";
import type { DimensionScore, Level } from "@/lib/profile";
import { badgeOf, expectedTier, nextStepLine, whyLine } from "@/lib/profileText";
import { type SummariesById } from "./ItemReferences";
import { Trail } from "./Trail";
import styles from "./profile.module.css";

const BADGE_WORDS = { strength: "Strength", gap: "Gap" } as const;

const TOP_SCORE = 2;

/** One dimension at the selected level: its score, working tier, why line, next step, strength or gap badge and folded trail. */
export function DimensionCard({
  dimension,
  level,
  summariesById,
}: {
  dimension: DimensionScore;
  level: Level;
  summariesById: SummariesById;
}) {
  const badge = badgeOf(dimension);
  const nextStep = nextStepLine(dimension, level);
  return (
    <article className={styles.card} data-score={dimension.score}>
      <header className={styles.cardHeader}>
        <h2>{dimension.name}</h2>
        {badge && <span className={`${styles.badge} ${styles[badge]}`}>{BADGE_WORDS[badge]}</span>}
      </header>
      <div className={styles.figure}>
        <div>
          <p className={styles.score}>
            <strong>{dimension.score}</strong> of 2,{" "}
            <span className={styles.workingTier}>
              {dimension.workingTier ? `works at ${dimension.workingTier}` : "no working tier"}
            </span>
          </p>
          <div className={styles.pips} aria-hidden="true">
            {Array.from({ length: TOP_SCORE }, (_, index) => (
              <span key={index} className={styles.pip} data-reached={index < dimension.score ? "true" : undefined} />
            ))}
          </div>
        </div>
        <TierSteps workingTier={dimension.workingTier} expected={{ level, tier: expectedTier(level) }} />
      </div>
      <p className={styles.why}>{whyLine(dimension, level)}</p>
      {nextStep && <p className={styles.nextStep}>{nextStep}</p>}
      <Trail trail={dimension.trail} summariesById={summariesById} />
    </article>
  );
}
