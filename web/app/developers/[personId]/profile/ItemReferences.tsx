import Link from "next/link";
import type { ItemSummary } from "@/lib/items";

const PULL_REQUEST_URL = "https://github.com/opf/openproject/pull/";

/** The summaries of the items on the page by item id; an item with no summary is still linked, by its number. */
export type SummariesById = ReadonlyMap<number, ItemSummary>;

/** A link to the item page of an evidence item, named by its title when the summary is known. */
export function ItemLink({ itemId, summariesById }: { itemId: number; summariesById: SummariesById }) {
  return <Link href={`/items/${itemId}`}>{summariesById.get(itemId)?.title ?? `item #${itemId}`}</Link>;
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
