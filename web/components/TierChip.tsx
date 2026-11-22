import styles from "./TierChip.module.css";

/**
 * A tier as a small label in the blue of its step; T4 takes the darkest, as T3 does. The large size is for the
 * place where the tier is the verdict of the page.
 */
export function TierChip({ tier, size = "regular" }: { tier: string; size?: "regular" | "large" }) {
  return (
    <span className={size === "large" ? `${styles.chip} ${styles.large}` : styles.chip} data-tier={tier}>
      {tier}
    </span>
  );
}
