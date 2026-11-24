import Link from "next/link";
import type { Developer } from "@/lib/evidence";
import type { Level } from "@/lib/profile";
import { compareHref } from "./compareHref";
import styles from "./compare.module.css";

type Selection = { a?: number; b?: number; quarter: string; level: Level };

/** One list of developers per side; each name is a link that sets that side and keeps the other. */
export function Pickers({ developers, selection }: { developers: Developer[]; selection: Selection }) {
  return (
    <div className={styles.pickers}>
      <Picker side="a" title="First developer" developers={developers} selection={selection} />
      <Picker side="b" title="Second developer" developers={developers} selection={selection} />
    </div>
  );
}

function Picker({
  side,
  title,
  developers,
  selection,
}: {
  side: "a" | "b";
  title: string;
  developers: Developer[];
  selection: Selection;
}) {
  return (
    <nav aria-label={title} className={styles.picker}>
      <h2 className={styles.pickerTitle}>{title}</h2>
      <ul className={styles.choices}>
        {developers.map((developer) => (
          <li key={developer.personId}>
            <Link
              href={compareHref({ ...selection, [side]: developer.personId })}
              className={selection[side] === developer.personId ? styles.chosen : styles.choice}
              aria-current={selection[side] === developer.personId ? "page" : undefined}
            >
              {developer.displayName}
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}
