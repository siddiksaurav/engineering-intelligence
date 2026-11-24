import type { DimensionScore } from "@/lib/profile";
import type { SummariesById } from "./ItemReferences";
import { Trail } from "./Trail";
import styles from "./profile.module.css";

/** The trail of every dimension, folded at the end of the page: every item with its tier and weight, the checks and the rules. */
export function TrailSection({
  dimensions,
  summariesById,
}: {
  dimensions: DimensionScore[];
  summariesById: SummariesById;
}) {
  return (
    <details className={styles.trailFold}>
      <summary>Details: every item, tier and check behind the scores</summary>
      {dimensions.map((dimension) => (
        <Trail key={dimension.key} name={dimension.name} trail={dimension.trail} summariesById={summariesById} />
      ))}
    </details>
  );
}
