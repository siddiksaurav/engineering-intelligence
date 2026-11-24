import { Panel } from "@/components/Panel";
import { TierChip } from "@/components/TierChip";
import type { DimensionScore, Level } from "@/lib/profile";
import { nextStepLine, whyLine } from "@/lib/profileText";
import styles from "./profile.module.css";

const NOT_ASSESSED =
  "Design and architecture, Collaboration, Mentoring, Knowledge sharing, Hiring, Pre-sales, Client delivery";

const LEAD_RATED_COUNT = 13;

/** Section 3: each scored dimension with its working tier, score and why or next step; the others greyed. */
export function DimensionsSection({ dimensions, level }: { dimensions: DimensionScore[]; level: Level }) {
  return (
    <Panel title="3 · Dimensions" note="Each skill area, its score and the next step.">
      <div className={styles.tableScroll}>
        <table className={styles.dimensionTable}>
          <thead>
            <tr>
              <th>Dimension</th>
              <th>Works at</th>
              <th>Score</th>
              <th>Why, or the next step</th>
            </tr>
          </thead>
          <tbody>
            {dimensions.map((dimension) => (
              <DimensionRow key={dimension.key} dimension={dimension} level={level} />
            ))}
            <tr className={styles.notAssessed}>
              <td>{NOT_ASSESSED}</td>
              <td>–</td>
              <td>–</td>
              <td>Not assessed in the pilot</td>
            </tr>
            <tr className={styles.notAssessed}>
              <td>{LEAD_RATED_COUNT} lead-rated dimensions</td>
              <td>–</td>
              <td>–</td>
              <td>Not rated: no lead input in the pilot</td>
            </tr>
          </tbody>
        </table>
      </div>
    </Panel>
  );
}

function DimensionRow({ dimension, level }: { dimension: DimensionScore; level: Level }) {
  const nextStep = nextStepLine(dimension, level);
  return (
    <tr>
      <th scope="row" className={styles.dimensionName}>
        {dimension.name}
      </th>
      <td>{dimension.workingTier ? <TierChip tier={dimension.workingTier} /> : "–"}</td>
      <td>
        <span className={styles.score} data-score={dimension.score}>
          {dimension.score}
        </span>
      </td>
      <td>
        <p className={styles.why}>{whyLine(dimension, level)}</p>
        {nextStep && <p className={styles.nextStep}>{nextStep}</p>}
      </td>
    </tr>
  );
}
