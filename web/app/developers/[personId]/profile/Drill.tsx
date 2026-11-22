import { EVIDENCE_SECTIONS } from "../../evidenceSections";
import type { Count } from "@/lib/profile";
import { ItemLink, PullRequestLink, type SummariesById } from "./ItemReferences";
import styles from "./profile.module.css";

/** The count of a plain list of pull requests, for the numbers that have no items behind them. */
export function ofPullRequests(pullRequestNumbers: number[]): Count {
  return { count: pullRequestNumbers.length, evidenceItemIds: [], pullRequestNumbers };
}

/**
 * A number that folds open to the items and pull requests behind it (decision 255). A count of 0 has nothing to open.
 */
export function Drill({ label, count, summariesById }: { label: string; count: Count; summariesById: SummariesById }) {
  if (count.count === 0) {
    return <p className={styles.drillEmpty}>{label}: 0</p>;
  }
  return (
    <details className={styles.drill}>
      <summary>
        {label}: {count.count}
      </summary>
      {count.evidenceItemIds.length > 0 && (
        <ul className={styles.drillList}>
          {count.evidenceItemIds.map((itemId) => (
            <li key={itemId}>
              <ItemLink itemId={itemId} summariesById={summariesById} />
              <ItemKind itemId={itemId} summariesById={summariesById} />
            </li>
          ))}
        </ul>
      )}
      {count.pullRequestNumbers.length > 0 && (
        <p className={styles.drillPullRequests}>
          Pull requests:{" "}
          {count.pullRequestNumbers.map((number) => (
            <span key={number} className={styles.pullRequest}>
              <PullRequestLink number={number} />
            </span>
          ))}
        </p>
      )}
    </details>
  );
}

function ItemKind({ itemId, summariesById }: { itemId: number; summariesById: SummariesById }) {
  const summary = summariesById.get(itemId);
  if (!summary) {
    return null;
  }
  const typeTitle = EVIDENCE_SECTIONS.find((section) => section.type === summary.type)?.title ?? summary.type;
  return (
    <span className={styles.itemKind}>
      {" "}
      {typeTitle}
      {summary.tier ? `, ${summary.tier}` : ""}
    </span>
  );
}
