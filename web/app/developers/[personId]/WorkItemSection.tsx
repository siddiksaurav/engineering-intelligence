import type { EvidenceItem, EvidenceType } from "@/lib/evidence";
import { ItemLinks } from "./ItemLinks";
import { ItemMeta } from "./ItemMeta";
import { SectionFrame } from "./SectionFrame";
import styles from "./developer.module.css";

/** A section of Feature work or Bug fix items, one card each. */
export function WorkItemSection({ type, title, items }: { type: EvidenceType; title: string; items: EvidenceItem[] }) {
  return (
    <SectionFrame type={type} title={title} count={items.length}>
      {items.map((item) => (
        <li key={item.id} className={styles.card}>
          <h3 className={styles.cardTitle}>{item.title}</h3>
          <ItemMeta item={item} />
          <ItemLinks links={item.links} />
        </li>
      ))}
    </SectionFrame>
  );
}
