import type { ReactNode } from "react";
import { Swatch } from "@/components/EvidenceTypes";
import type { EvidenceType } from "@/lib/evidence";
import { typeColor } from "@/lib/palette";
import styles from "./developer.module.css";

/** One evidence type on the evidence list: its swatch, title and count, then its items. The id is the type's name. */
export function SectionFrame({
  type,
  title,
  count,
  children,
}: {
  type: EvidenceType;
  title: string;
  count: number;
  children: ReactNode;
}) {
  return (
    <section id={type} className={styles.section}>
      <h2 className={styles.sectionTitle}>
        <Swatch color={typeColor(type)} />
        {title}
        <span className={styles.sectionCount}>{count}</span>
      </h2>
      <ul className={styles.items}>{children}</ul>
    </section>
  );
}
