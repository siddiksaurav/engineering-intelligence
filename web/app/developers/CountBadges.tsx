import type { TypeCounts } from "@/lib/evidence";
import styles from "./developers.module.css";

/** The count of current evidence items per type, one coloured badge each. */
export function CountBadges({ counts }: { counts: TypeCounts }) {
  return (
    <ul className={styles.counts}>
      <li className={`${styles.count} ${styles.featureWork}`}>
        Feature work <strong>{counts.featureWork}</strong>
      </li>
      <li className={`${styles.count} ${styles.bugFix}`}>
        Bug fix <strong>{counts.bugFix}</strong>
      </li>
      <li className={`${styles.count} ${styles.reviewCommentWritten}`}>
        Review comment written <strong>{counts.reviewCommentWritten}</strong>
      </li>
    </ul>
  );
}
