import { DonutChart } from "@/components/charts/DonutChart";
import { GroupedBarChart } from "@/components/charts/GroupedBarChart";
import { Panel } from "@/components/Panel";
import { ScoreLine } from "@/components/ScoreLine";
import { sliceCount } from "@/lib/counts";
import { COMPARISON_COLOR, PERSON_COLOR, workTypeColor } from "@/lib/palette";
import type { Analytics, DimensionScores } from "@/lib/profile";
import { plainName } from "@/lib/profileText";
import styles from "./compare.module.css";

const COMPLEXITIES = ["low", "medium", "high", "very_high", "not_judged_yet"] as const;

/** One developer with everything the page reads of them. */
export type Side = { analytics: Analytics; scores: DimensionScores };

/** The first developer takes the person colour and the second the grey, so a name keeps its colour in every chart. */
const SIDE_COLORS = [PERSON_COLOR, COMPARISON_COLOR] as const;

function nameOf(side: Side): string {
  return side.analytics.person.displayName;
}

function seriesOf(sides: [Side, Side]) {
  return sides.map((side, index) => ({ key: `side${index}`, label: nameOf(side), color: SIDE_COLORS[index] }));
}

/** The two scores of each developer at the level, one column each. */
export function ScoresSection({ sides }: { sides: [Side, Side] }) {
  return (
    <Panel title="Scores" note="Each skill area, its score of 2 and the tier the developer works at.">
      <div className={styles.columns}>
        {sides.map((side) => (
          <div key={side.analytics.person.personId}>
            <h3 className={styles.columnTitle}>{nameOf(side)}</h3>
            <Scores scores={side.scores} />
          </div>
        ))}
      </div>
    </Panel>
  );
}

function Scores({ scores }: { scores: DimensionScores }) {
  if (!scores.scored) {
    return <p className={styles.notScored}>not scored</p>;
  }
  return (
    <ul className={styles.scores}>
      {scores.dimensions.map((dimension) => (
        <li key={dimension.key} className={styles.score}>
          <span className={styles.dimension}>{dimension.name}</span>
          <ScoreLine score={dimension.score} workingTier={dimension.workingTier} />
        </li>
      ))}
    </ul>
  );
}

/** The share of Feature work at Medium or harder and at High, both developers against the line to clear. */
export function WorkLevelSection({ sides }: { sides: [Side, Side] }) {
  const values = (pick: "shareAtT2OrAbove" | "shareAtT3") =>
    Object.fromEntries(sides.map((side, index) => [`side${index}`, side.analytics.workLevel.person[pick]]));
  return (
    <Panel
      title="Level of the work"
      note="Share of weighted Feature work. To work at a tier, the share must reach the line."
    >
      <GroupedBarChart
        label="Feature work by level"
        unit="percent"
        series={seriesOf(sides)}
        categories={[
          { key: "t2", label: "Medium or harder", values: values("shareAtT2OrAbove") },
          { key: "t3", label: "High", values: values("shareAtT3") },
        ]}
        referenceLine={{ value: sides[0].analytics.workLevel.line, label: "Line to clear" }}
      />
    </Panel>
  );
}

/** The merged pull requests of each developer by work type, one donut each. */
export function WorkKindSection({ sides }: { sides: [Side, Side] }) {
  return (
    <Panel title="What kind of work" note="Pull requests merged, by work type.">
      <div className={styles.columns}>
        {sides.map((side) => {
          const { distribution } = side.analytics.workClassification;
          return (
            <div key={side.analytics.person.personId}>
              <h3 className={styles.columnTitle}>{nameOf(side)}</h3>
              <DonutChart
                label={`Pull requests of ${nameOf(side)} by work type`}
                centerLabel={`${distribution.total} PRs`}
                slices={distribution.slices.map((slice) => ({
                  key: slice.name,
                  label: plainName(slice.name),
                  value: slice.count.count,
                  color: workTypeColor(slice.name),
                }))}
              />
            </div>
          );
        })}
      </div>
    </Panel>
  );
}

/** The Feature work items of each developer by complexity, side by side on one axis. */
export function ComplexitySection({ sides }: { sides: [Side, Side] }) {
  return (
    <Panel title="Feature work by complexity" note="Number of Feature work items at each complexity.">
      <GroupedBarChart
        label="Feature work by complexity"
        unit="count"
        series={seriesOf(sides)}
        categories={COMPLEXITIES.map((name) => ({
          key: name,
          label: plainName(name),
          values: Object.fromEntries(
            sides.map((side, index) => [`side${index}`, sliceCount(side.analytics.workComplexity.person, name).count]),
          ),
        }))}
      />
    </Panel>
  );
}
