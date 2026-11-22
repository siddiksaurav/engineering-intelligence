import type { DimensionScore, Level } from "@/lib/profile";
import { badgeOf, nextStepLine, whyLine } from "@/lib/profileText";
import { type SummariesById } from "./ItemReferences";
import { Trail } from "./Trail";
import styles from "./profile.module.css";

const BADGE_WORDS = { strength: "Strength", gap: "Gap" } as const;

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
    <article className={`${styles.card} ${styles[`score${dimension.score}`]}`}>
      <header className={styles.cardHeader}>
        <h2>{dimension.name}</h2>
        {badge && <span className={`${styles.badge} ${styles[badge]}`}>{BADGE_WORDS[badge]}</span>}
      </header>
      <p className={styles.score}>
        <strong>{dimension.score}</strong> of 2,{" "}
        <span className={styles.workingTier}>
          {dimension.workingTier ? `works at ${dimension.workingTier}` : "no working tier"}
        </span>
      </p>
      <p>{whyLine(dimension, level)}</p>
      {nextStep && <p className={styles.nextStep}>{nextStep}</p>}
      <Trail trail={dimension.trail} summariesById={summariesById} />
    </article>
  );
}
