import type { CSSProperties } from "react";
import type { Count, Distribution } from "@/lib/profile";
import { percent } from "@/lib/profileText";
import { EVIDENCE_SECTIONS } from "../../evidenceSections";
import { Drill } from "./Drill";
import type { SummariesById } from "./ItemReferences";
import styles from "./profile.module.css";

const NOT_JUDGED_YET = "not_judged_yet";

/** Complexity and tier are magnitudes: one sky hue from light to dark. Types are identities: a fixed colour each. */
const MAGNITUDE_COLORS: Record<string, string> = {
  low: "var(--complexity-low)",
  medium: "var(--sky-500)",
  high: "var(--sky-700)",
  very_high: "var(--sky-900)",
  T1: "var(--tier-1)",
  T2: "var(--tier-2)",
  T3: "var(--tier-3)",
  [NOT_JUDGED_YET]: "var(--not-judged)",
};

const DEFAULT_COLOR = "var(--sky-500)";

/** A snake case name in plain words: "very_high" becomes "Very high"; a work type uses its screen title. */
export function plainName(name: string): string {
  const workType = EVIDENCE_SECTIONS.find((section) => section.type === name);
  if (workType) {
    return workType.title;
  }
  const words = name.replaceAll("_", " ");
  return words.charAt(0).toUpperCase() + words.slice(1);
}

function colorOf(name: string): string {
  const workType = EVIDENCE_SECTIONS.find((section) => section.type === name);
  return (
    MAGNITUDE_COLORS[name] ??
    (workType
      ? `var(--${workType.colorClass.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`)})`
      : DEFAULT_COLOR)
  );
}

/** The share of a count in a total; null when the total is 0. */
export function shareOf(count: Count, total: number): number | null {
  return total === 0 ? null : count.count / total;
}

/**
 * One horizontal bar: the name and count open to the items behind them, the whole percent is written next to the
 * bar so no reading depends on colour. A null share shows "no PRs" and no bar (decision 234).
 */
export function BarRow({
  label,
  count,
  share,
  color = DEFAULT_COLOR,
  summariesById,
}: {
  label: string;
  count: Count;
  share: number | null;
  color?: string;
  summariesById: SummariesById;
}) {
  const style = { "--bar-color": color, "--bar-width": `${(share ?? 0) * 100}%` } as CSSProperties;
  return (
    <div className={styles.barRow} style={style}>
      <Drill label={label} count={count} summariesById={summariesById} />
      <div className={styles.barLine}>
        {share !== null && (
          <div
            className={styles.barTrack}
            role="meter"
            aria-label={label}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={Math.round(share * 100)}
            title={`${label}: ${count.count}, ${percent(share)}`}
          >
            <div className={styles.barFill} />
          </div>
        )}
        <span className={styles.barValue}>{percent(share)}</span>
      </div>
    </div>
  );
}

/** The slices of a distribution as bars, in the order the backend gives them. */
export function DistributionBars({
  title,
  distribution,
  summariesById,
}: {
  title: string;
  distribution: Distribution;
  summariesById: SummariesById;
}) {
  return (
    <div className={styles.distribution}>
      <h4>{title}</h4>
      {distribution.slices.length === 0 && <p className={styles.barValue}>{percent(null)}</p>}
      {distribution.slices.map((slice) => (
        <BarRow
          key={slice.name}
          label={plainName(slice.name)}
          count={slice.count}
          share={slice.share}
          color={colorOf(slice.name)}
          summariesById={summariesById}
        />
      ))}
    </div>
  );
}
