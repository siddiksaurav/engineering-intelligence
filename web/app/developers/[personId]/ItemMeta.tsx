import type { EvidenceItem } from "@/lib/evidence";
import styles from "./developer.module.css";

export function ItemMeta({ item }: { item: EvidenceItem }) {
  return (
    <p className={styles.meta}>
      {item.date}
      <span className={styles.quarter}>{item.quarter}</span>
    </p>
  );
}
