import type { ItemLink } from "@/lib/evidence";
import styles from "./developer.module.css";

export function ItemLinks({ links }: { links: ItemLink[] }) {
  return (
    <ul className={styles.links}>
      {links.map((link) => (
        <li key={link.url}>
          <a href={link.url} className={styles.link}>
            {link.label}
          </a>
        </li>
      ))}
    </ul>
  );
}
