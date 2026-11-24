import type { ReactNode } from "react";
import styles from "./charts.module.css";

export type LegendItem = { key: string; label: string; color: string };

/**
 * The shell every chart sits in. The picture is hidden from a screen reader; the text summary says the same
 * numbers, so the chart is never colour or shape alone.
 */
export function ChartFrame({
  label,
  summary,
  legend,
  children,
}: {
  label: string;
  summary: string[];
  legend?: LegendItem[];
  children: ReactNode;
}) {
  return (
    <figure className={styles.frame} aria-label={label}>
      <div className={styles.plot} aria-hidden="true">
        {children}
      </div>
      {legend !== undefined && legend.length > 1 && (
        <ul className={styles.legend} aria-hidden="true">
          {legend.map((item) => (
            <li key={item.key}>
              <span className={styles.legendSwatch} style={{ background: item.color }} />
              {item.label}
            </li>
          ))}
        </ul>
      )}
      <ul className={styles.visuallyHidden}>
        {summary.map((line) => (
          <li key={line}>{line}</li>
        ))}
      </ul>
    </figure>
  );
}
