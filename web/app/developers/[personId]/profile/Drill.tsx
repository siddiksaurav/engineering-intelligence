import type { ReactNode } from "react";
import { Swatch } from "@/components/EvidenceTypes";
import { typeTitle } from "@/lib/evidenceSections";
import type { Count } from "@/lib/profile";
import { ItemLink, PullRequestLink, type SummariesById } from "./ItemReferences";
import styles from "./profile.module.css";

/** The count of a plain list of pull requests, for the numbers that have no items behind them. */
export function ofPullRequests(pullRequestNumbers: number[]): Count {
  return { count: pullRequestNumbers.length, evidenceItemIds: [], pullRequestNumbers };
}

/**
 * A number that folds open to the items and pull requests behind it (decision 255). A count of 0 has nothing to open.
 * A swatch ties the number to its segment of a chart; what is given as `trailing` (a bar, a percent) stays on the
 * line of the number, so the opened list takes the full width under it. As a figure the count is large and the
 * label small, for the tiles of a section.
 */
export function Drill({
  label,
  count,
  summariesById,
  swatch,
  trailing,
  figure = false,
}: {
  label: string;
  count: Count;
  summariesById: SummariesById;
  swatch?: string;
  trailing?: ReactNode;
  figure?: boolean;
}) {
  const line = (
    <>
      {swatch && <Swatch color={swatch} />}
      {figure ? (
        <span className={styles.drillLabel}>
          <span className={styles.figureCount}>{count.count}</span> {label}
        </span>
      ) : (
        <span className={styles.drillLabel}>
          {label}: {count.count}
        </span>
      )}
      {trailing}
    </>
  );
  const className = figure ? styles.tile : undefined;
  if (count.count === 0) {
    return <p className={[styles.drillEmpty, className].filter(Boolean).join(" ")}>{line}</p>;
  }
  return (
    <details className={[styles.drill, className].filter(Boolean).join(" ")}>
      <summary>{line}</summary>
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
  return (
    <span className={styles.itemKind}>
      {" "}
      {typeTitle(summary.type) ?? summary.type}
      {summary.tier ? `, ${summary.tier}` : ""}
    </span>
  );
}
