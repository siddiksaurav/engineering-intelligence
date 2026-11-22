import type { TypeCounts } from "@/lib/evidence";
import { EVIDENCE_SECTIONS } from "@/lib/evidenceSections";
import { typeColor } from "@/lib/palette";
import { SegmentedBar } from "./charts/SegmentedBar";
import styles from "./EvidenceTypes.module.css";

/** The evidence items of one developer or of the team as one bar, a segment per type. */
export function TypeBar({ label, counts, size }: { label: string; counts: TypeCounts; size?: "thin" | "regular" }) {
  const segments = EVIDENCE_SECTIONS.map((section) => ({
    key: section.type,
    label: section.title,
    value: counts[section.countKey],
    color: typeColor(section.type),
  }));
  return <SegmentedBar label={label} segments={segments} size={size} />;
}

/** A dot in the colour of a series, next to the text that names it. Text never takes the series colour itself. */
export function Swatch({ color }: { color: string }) {
  return <span className={styles.swatch} style={{ background: color }} aria-hidden="true" />;
}

/** The eight evidence types with their colours, once per page that draws type bars. */
export function TypeLegend() {
  return (
    <ul className={styles.legend} aria-label="Evidence types">
      {EVIDENCE_SECTIONS.map((section) => (
        <li key={section.type}>
          <Swatch color={typeColor(section.type)} />
          {section.title}
        </li>
      ))}
    </ul>
  );
}
