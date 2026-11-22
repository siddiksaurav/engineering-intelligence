"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import styles from "./AppBar.module.css";

type Section = { href: string; label: string; holds: (path: string) => boolean };

const SECTIONS: Section[] = [
  { href: "/", label: "Overview", holds: (path) => path === "/" },
  {
    href: "/developers",
    label: "Developers",
    // An item page is reached from a developer, so it belongs to this section.
    holds: (path) => path.startsWith("/developers") || path.startsWith("/items"),
  },
];

/** The links to the sections; the one that holds the current path is marked (decision 269). */
export function SectionLinks() {
  const path = usePathname();
  return (
    <nav className={styles.sections} aria-label="Sections">
      {SECTIONS.map((section) => (
        <Link
          key={section.href}
          href={section.href}
          className={styles.section}
          aria-current={section.holds(path) ? "page" : undefined}
        >
          {section.label}
        </Link>
      ))}
    </nav>
  );
}
