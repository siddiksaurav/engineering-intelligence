import type { EvidenceItem } from "@/lib/evidence";
import { ItemLinks } from "./ItemLinks";
import { ItemMeta } from "./ItemMeta";
import styles from "./developer.module.css";

/** A section of Feature work or Bug fix items, one card each. */
export function WorkItemSection({
  title,
  accentClass,
  items,
}: {
  title: string;
  accentClass: string;
  items: EvidenceItem[];
}) {
  return (
    <section className={`${styles.section} ${accentClass}`}>
      <h2 className={styles.sectionTitle}>{title}</h2>
      {items.length === 0 ? (
        <p className={styles.none}>None</p>
      ) : (
        <ul className={styles.items}>
          {items.map((item) => (
            <li key={item.id} className={styles.card}>
              <h3 className={styles.cardTitle}>{item.title}</h3>
              <ItemMeta item={item} />
              <ItemLinks links={item.links} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
