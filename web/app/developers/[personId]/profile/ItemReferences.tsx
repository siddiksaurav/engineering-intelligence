import Link from "next/link";
import type { ItemSummary } from "@/lib/items";
import styles from "./profile.module.css";

const PULL_REQUEST_URL = "https://github.com/opf/openproject/pull/";

/** The summaries of the items on the page by item id; an item with no summary is still linked, by its number. */
export type SummariesById = ReadonlyMap<number, ItemSummary>;

/**
 * A link to the item page of an evidence item, named by its title when the summary is known. A superseded item is
 * marked "no longer current", with a link to the current row when there is one (decision 238).
 */
export function ItemLink({ itemId, summariesById }: { itemId: number; summariesById: SummariesById }) {
  const summary = summariesById.get(itemId);
  return (
    <>
      <Link href={`/items/${itemId}`}>{summary?.title ?? `item #${itemId}`}</Link>
      {summary && !summary.current && (
        <span className={styles.superseded}>
          {" "}
          (no longer current
          {summary.currentItemId !== null && (
            <>
              , <Link href={`/items/${summary.currentItemId}`}>current item</Link>
            </>
          )}
          )
        </span>
      )}
    </>
  );
}

/** A link to a pull request of the repository on GitHub. */
export function PullRequestLink({ number }: { number: number }) {
  return <a href={`${PULL_REQUEST_URL}${number}`}>#{number}</a>;
}

export function PullRequestLinks({ numbers }: { numbers: readonly number[] }) {
  return (
    <>
      {numbers.map((number) => (
        <span key={number}>
          {" "}
          <PullRequestLink number={number} />
        </span>
      ))}
    </>
  );
}
