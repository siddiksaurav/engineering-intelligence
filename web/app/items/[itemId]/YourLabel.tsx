import { labelAgreement } from "@/lib/blindCheck";
import type { ItemDetail } from "@/lib/items";
import { capitalised, spaced } from "@/lib/words";
import styles from "./item.module.css";

/** The user's blind label of an item of the sample, and how far it is from the AI's complexity. */
export function YourLabel({ item }: { item: ItemDetail }) {
  if (!item.blindCheck) {
    return null;
  }
  const { label } = item.blindCheck;
  return (
    <section className={styles.yourLabel}>
      <h2>Your label</h2>
      {label === null && <p className={styles.state}>not labeled yet</p>}
      {label?.complexity === null && <p className={styles.state}>You could not judge this item</p>}
      {label?.complexity && (
        <>
          <p className={styles.labelValue}>{capitalised(spaced(label.complexity))}</p>
          <p className={styles.agreement}>{labelAgreement(label.complexity, item.judgment.value?.complexity)}</p>
        </>
      )}
      {label?.note && <p className={styles.labelNote}>{label.note}</p>}
    </section>
  );
}
