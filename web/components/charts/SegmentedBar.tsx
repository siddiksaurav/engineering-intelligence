import styles from "./charts.module.css";

export type Segment = { key: string; label: string; value: number; color: string };

/**
 * One whole split into its parts: each part takes its share of the width. The picture never carries a number alone;
 * the label names every part, and the page lists the parts next to the bar.
 */
export function SegmentedBar({
  label,
  segments,
  size = "regular",
}: {
  label: string;
  segments: Segment[];
  size?: "thin" | "regular";
}) {
  const drawn = segments.filter((segment) => segment.value > 0);
  const parts = drawn.map((segment) => `${segment.label} ${segment.value}`).join(", ");
  return (
    <div className={`${styles.segmented} ${styles[size]}`} role="img" aria-label={`${label}: ${parts || "none"}`}>
      {drawn.map((segment) => (
        <span
          key={segment.key}
          className={styles.segment}
          style={{ flexGrow: segment.value, background: segment.color }}
          title={`${segment.label}: ${segment.value}`}
        />
      ))}
    </div>
  );
}
