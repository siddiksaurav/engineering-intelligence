import type { EvidenceItem, ItemTier } from "@/lib/evidence";
import { ItemLinks } from "./ItemLinks";
import { ItemMeta } from "./ItemMeta";
import styles from "./developer.module.css";

const TIER_CLASSES: Record<ItemTier["tier"], string> = {
  T1: styles.tier1,
  T2: styles.tier2,
  T3: styles.tier3,
  T4: styles.tier3,
};

/** A section of items built from one pull request each, such as Test change; the tier is shown when there is one. */
export function PullRequestItemSection({
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
              <div className={styles.cardHeader}>
                {item.tier && (
                  <span className={`${styles.tier} ${TIER_CLASSES[item.tier.tier]}`}>{item.tier.tier}</span>
                )}
                <h3 className={styles.cardTitle}>{item.title}</h3>
              </div>
              <ItemMeta item={item} />
              {item.tier && <p className={styles.tierReason}>{item.tier.reason}</p>}
              <ItemLinks links={item.links} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
