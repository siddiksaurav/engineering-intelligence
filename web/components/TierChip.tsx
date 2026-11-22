import styles from "./TierChip.module.css";

/** A tier as a small label in the blue of its step; T4 takes the darkest, as T3 does. */
export function TierChip({ tier }: { tier: string }) {
  return (
    <span className={styles.chip} data-tier={tier}>
      {tier}
    </span>
  );
}
