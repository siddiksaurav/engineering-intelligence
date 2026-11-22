import { TierChip } from "@/components/TierChip";
import type { EvidenceItem, EvidenceType } from "@/lib/evidence";
import { ItemLinks } from "./ItemLinks";
import { ItemMeta } from "./ItemMeta";
import { SectionFrame } from "./SectionFrame";
import styles from "./developer.module.css";

/** A section of items built from one pull request each, such as Test change; the tier is shown when there is one. */
export function PullRequestItemSection({
  type,
  title,
  items,
}: {
  type: EvidenceType;
  title: string;
  items: EvidenceItem[];
}) {
  return (
    <SectionFrame type={type} title={title} count={items.length}>
      {items.map((item) => (
        <li key={item.id} className={styles.card}>
          <div className={styles.cardHeader}>
            <h3 className={styles.cardTitle}>{item.title}</h3>
            {item.tier && <TierChip tier={item.tier.tier} />}
          </div>
          <ItemMeta item={item} />
          {item.tier && <p className={styles.tierReason}>{item.tier.reason}</p>}
          <ItemLinks links={item.links} />
        </li>
      ))}
    </SectionFrame>
  );
}
