import Link from "next/link";
import type { Level } from "@/lib/profile";
import styles from "./LevelSwitch.module.css";

const LEVELS: Level[] = ["L1", "L2", "L3"];

/** The level the scores are read against; each level is a link, so the choice lives in the URL. */
export function LevelSwitch({ selected, hrefFor }: { selected: Level; hrefFor: (level: Level) => string }) {
  return (
    <div className={styles.control}>
      <span className={styles.caption}>Read at level</span>
      <nav className={styles.levels} aria-label="Level">
        {LEVELS.map((level) => (
          <Link
            key={level}
            href={hrefFor(level)}
            className={level === selected ? styles.selected : styles.level}
            aria-current={level === selected ? "page" : undefined}
          >
            {level}
          </Link>
        ))}
      </nav>
    </div>
  );
}
