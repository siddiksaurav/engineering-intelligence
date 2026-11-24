import { MeterBar } from "@/components/charts/MeterBar";
import { SegmentedBar } from "@/components/charts/SegmentedBar";
import { orderedColor, SINGLE_HUE_COLOR } from "@/lib/palette";
import type { Count, Distribution } from "@/lib/profile";
import { percent, plainName } from "@/lib/profileText";
import { Drill } from "./Drill";
import type { SummariesById } from "./ItemReferences";
import styles from "./profile.module.css";

/** Tiers, complexities and "not judged yet" have a place in an order and a colour for it; other classes share one hue. */
function colorOf(name: string): string {
  return orderedColor(name) ?? SINGLE_HUE_COLOR;
}

/** The share of a count in a total; null when the total is 0. */
export function shareOf(count: Count, total: number): number | null {
  return total === 0 ? null : count.count / total;
}

/**
 * One class as a row: the name and count open to the items behind them, the whole percent is written at the end
 * so no reading depends on colour. As a bar row it carries its own thin bar; as a legend row it carries the swatch
 * of its segment in the bar above. A null share shows "no PRs" and no bar (decision 234).
 */
export function ShareRow({
  label,
  count,
  share,
  color = SINGLE_HUE_COLOR,
  form = "bar",
  summariesById,
}: {
  label: string;
  count: Count;
  share: number | null;
  color?: string;
  form?: "bar" | "legend";
  summariesById: SummariesById;
}) {
  return (
    <Drill
      label={label}
      count={count}
      summariesById={summariesById}
      swatch={form === "legend" ? color : undefined}
      trailing={
        <>
          {form === "bar" && share !== null && (
            <span className={styles.rowBar}>
              <MeterBar label={label} count={count.count} share={share} color={color} />
            </span>
          )}
          <span className={styles.rowShare}>{percent(share)}</span>
        </>
      }
    />
  );
}

/**
 * The classes of a distribution in the order the backend gives them. "ranked" is a bar per class, for classes that
 * are compared with each other; "segmented" is one bar split into the classes, for a whole that has an order.
 */
export function DistributionChart({
  title,
  distribution,
  form,
  summariesById,
}: {
  title: string;
  distribution: Distribution;
  form: "ranked" | "segmented";
  summariesById: SummariesById;
}) {
  return (
    <div className={styles.distribution}>
      <h3 className={styles.chartTitle}>{title}</h3>
      {distribution.slices.length === 0 && <p className={styles.noShare}>{percent(null)}</p>}
      {form === "segmented" && distribution.total > 0 && (
        <SegmentedBar
          label={title}
          segments={distribution.slices.map((slice) => ({
            key: slice.name,
            label: plainName(slice.name),
            value: slice.count.count,
            color: colorOf(slice.name),
          }))}
        />
      )}
      {distribution.slices.map((slice) => (
        <ShareRow
          key={slice.name}
          label={plainName(slice.name)}
          count={slice.count}
          share={slice.share}
          color={colorOf(slice.name)}
          form={form === "segmented" ? "legend" : "bar"}
          summariesById={summariesById}
        />
      ))}
    </div>
  );
}
