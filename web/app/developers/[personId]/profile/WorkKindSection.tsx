import { DonutChart } from "@/components/charts/DonutChart";
import { Panel } from "@/components/Panel";
import { mergeCounts, sliceCount } from "@/lib/counts";
import { orderedColor, workTypeColor } from "@/lib/palette";
import type { Analytics, Count, Distribution } from "@/lib/profile";
import { percent, plainName } from "@/lib/profileText";
import { workTypeSlices } from "@/lib/workTypeSlices";
import { Drill } from "./Drill";
import type { SummariesById } from "./ItemReferences";
import styles from "./profile.module.css";

const COMPLEXITIES = ["low", "medium", "high", "very_high"] as const;

function allOf(distribution: Distribution): Count {
  return mergeCounts(distribution.slices.map((slice) => slice.count));
}

/**
 * Section 2: the person's merged pull requests by work type as a donut, three tiles of delivery (decision 290) and
 * the complexity counts of Feature work next to the rest of the repository (decision 298). Every number opens to
 * the items and pull requests behind it.
 */
export function WorkKindSection({ analytics, summariesById }: { analytics: Analytics; summariesById: SummariesById }) {
  const { distribution } = analytics.workClassification;
  const { person, restOfRepository } = analytics.workComplexity;
  return (
    <Panel title="2 · What kind of work" note={`${distribution.total} pull requests merged, by work type.`}>
      <DonutChart
        label="Pull requests by work type"
        centerLabel={`${distribution.total} PRs`}
        slices={workTypeSlices(distribution)}
      />
      <div className={styles.rows}>
        {distribution.slices.map((slice) => (
          <Drill
            key={slice.name}
            label={plainName(slice.name)}
            count={slice.count}
            swatch={workTypeColor(slice.name)}
            trailing={<span className={styles.rowShare}>{percent(slice.share)}</span>}
            summariesById={summariesById}
          />
        ))}
      </div>
      <div className={styles.tiles}>
        <Drill figure label="Feature work items" count={allOf(person)} summariesById={summariesById} />
        <Drill figure label="PRs merged" count={allOf(distribution)} summariesById={summariesById} />
        <Drill figure label="Bugs fixed" count={analytics.bugsFixed} summariesById={summariesById} />
      </div>
      <h3 className={styles.chartTitle}>Feature work by complexity</h3>
      <div className={styles.sideBySide}>
        <ComplexityCounts title={analytics.person.displayName} distribution={person} summariesById={summariesById} />
        <ComplexityCounts title="Rest of repository" distribution={restOfRepository} summariesById={summariesById} />
      </div>
    </Panel>
  );
}

function ComplexityCounts({
  title,
  distribution,
  summariesById,
}: {
  title: string;
  distribution: Distribution;
  summariesById: SummariesById;
}) {
  return (
    <div>
      <h4 className={styles.chartTitle}>{title}</h4>
      {COMPLEXITIES.map((name) => (
        <Drill
          key={name}
          label={plainName(name)}
          count={sliceCount(distribution, name)}
          swatch={orderedColor(name)}
          summariesById={summariesById}
        />
      ))}
      <Drill
        label="Not judged yet"
        count={sliceCount(distribution, "not_judged_yet")}
        swatch={orderedColor("not_judged_yet")}
        summariesById={summariesById}
      />
    </div>
  );
}
