import Link from "next/link";
import { Panel } from "@/components/Panel";
import type { BlindCheckNumbers } from "@/lib/blindCheck";
import styles from "./blindCheck.module.css";

/** The labeled items whose reason is not marked yet, each linked to its item page. */
export function ReasonsToMark({ items }: { items: BlindCheckNumbers["reasonsToMark"] }) {
  if (items.length === 0) {
    return null;
  }
  return (
    <Panel title="Reasons to mark" note="Open each item, read the AI's reason and mark it.">
      <ul className={styles.itemLinks}>
        {items.map((item) => (
          <li key={item.itemId}>
            <Link href={`/items/${item.itemId}`}>{item.title}</Link>
          </li>
        ))}
      </ul>
    </Panel>
  );
}
