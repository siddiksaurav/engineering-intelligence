import { percent } from "@/lib/profileText";
import styles from "./charts.module.css";

/** One share as a thin bar on a track. The page writes the count and the percent next to it. */
export function MeterBar({
  label,
  count,
  share,
  color,
}: {
  label: string;
  count: number;
  share: number;
  color: string;
}) {
  const whole = Math.round(Number((share * 100).toPrecision(12)));
  return (
    <div
      className={styles.meterTrack}
      role="meter"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={whole}
      title={`${label}: ${count}, ${percent(share)}`}
    >
      {share > 0 && <div className={styles.meterFill} style={{ width: `${share * 100}%`, background: color }} />}
    </div>
  );
}
