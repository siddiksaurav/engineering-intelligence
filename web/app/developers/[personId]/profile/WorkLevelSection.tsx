import { GroupedBarChart } from "@/components/charts/GroupedBarChart";
import { Panel } from "@/components/Panel";
import { COMPARISON_COLOR, PERSON_COLOR } from "@/lib/palette";
import type { Analytics } from "@/lib/profile";
import { workLevelSentence } from "@/lib/profileText";
import styles from "./profile.module.css";

/** Section 1: the share of weighted Feature work at Medium or harder and at High, the person against the rest of the repository. */
export function WorkLevelSection({ analytics }: { analytics: Analytics }) {
  const { workLevel } = analytics;
  const name = analytics.person.displayName;
  return (
    <Panel
      title="1 · Level of the work"
      note={`Is ${name}'s Feature work at the level? To work at a tier, a share at that tier or higher must reach the line.`}
    >
      <GroupedBarChart
        label="Feature work by level"
        unit="percent"
        series={[
          { key: "person", label: name, color: PERSON_COLOR },
          { key: "restOfRepository", label: "Rest of repository", color: COMPARISON_COLOR },
        ]}
        categories={[
          {
            key: "t2",
            label: "Medium or harder",
            values: {
              person: workLevel.person.shareAtT2OrAbove,
              restOfRepository: workLevel.restOfRepository.shareAtT2OrAbove,
            },
          },
          {
            key: "t3",
            label: "High",
            values: { person: workLevel.person.shareAtT3, restOfRepository: workLevel.restOfRepository.shareAtT3 },
          },
        ]}
        referenceLine={{ value: workLevel.line, label: "Line to clear" }}
      />
      <p className={styles.reading}>{workLevelSentence(name, workLevel)}</p>
      <p className={styles.coverage}>
        Judged Feature work: {workLevel.coverage.judged} of {workLevel.coverage.total}
      </p>
    </Panel>
  );
}
