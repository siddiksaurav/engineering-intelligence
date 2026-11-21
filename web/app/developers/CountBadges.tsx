import type { TypeCounts } from "@/lib/evidence";
import { EVIDENCE_SECTIONS } from "./evidenceSections";
import styles from "./developers.module.css";

/** The count of current evidence items per type, one coloured badge each. */
export function CountBadges({ counts }: { counts: TypeCounts }) {
  return (
    <ul className={styles.counts}>
      {EVIDENCE_SECTIONS.map((section) => (
        <li key={section.type} className={`${styles.count} ${styles[section.colorClass]}`}>
          {section.title} <strong>{counts[section.countKey]}</strong>
        </li>
      ))}
    </ul>
  );
}
