import { percent } from "@/lib/profileText";
import styles from "./charts.module.css";

const CENTRE = 60;
const RADIUS = 50;

/** One ratio as a ring filled to its share, the whole percent in the middle. A null share is "no PRs", with no ring. */
export function RingMeter({ label, share }: { label: string; share: number | null }) {
  if (share === null) {
    return (
      <div className={`${styles.ring} ${styles.ringEmpty}`}>
        <span className={styles.ringValue}>{percent(share)}</span>
      </div>
    );
  }
  const filled = Number((share * 100).toPrecision(12));
  return (
    <div
      className={styles.ring}
      role="meter"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round(filled)}
    >
      <svg viewBox="0 0 120 120" aria-hidden="true">
        <circle className={styles.ringTrack} cx={CENTRE} cy={CENTRE} r={RADIUS} />
        {filled > 0 && (
          <circle
            className={styles.ringArc}
            cx={CENTRE}
            cy={CENTRE}
            r={RADIUS}
            pathLength={100}
            strokeDasharray={`${filled} 100`}
            transform={`rotate(-90 ${CENTRE} ${CENTRE})`}
          />
        )}
      </svg>
      <span className={styles.ringValue}>{percent(share)}</span>
    </div>
  );
}
