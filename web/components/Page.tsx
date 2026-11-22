import Link from "next/link";
import type { ReactNode } from "react";
import styles from "./Page.module.css";

type Crumb = { href: string; label: string };

/**
 * The band at the top of a page: where the page sits (crumbs), what it is (title), and what belongs to the whole
 * page (the line under the title, the controls at the side, the figures at the foot).
 */
export function PageHeader({
  crumbs = [],
  title,
  children,
  controls,
  figures,
}: {
  crumbs?: Crumb[];
  title: ReactNode;
  children?: ReactNode;
  controls?: ReactNode;
  figures?: ReactNode;
}) {
  return (
    <header className={styles.header}>
      <div className={styles.headerInner}>
        {crumbs.length > 0 && (
          <nav className={styles.crumbs} aria-label="Breadcrumb">
            {crumbs.map((crumb) => (
              <Link key={crumb.href} href={crumb.href} className={styles.crumb}>
                {crumb.label}
              </Link>
            ))}
          </nav>
        )}
        <div className={styles.titleRow}>
          <div className={styles.titleBlock}>
            <h1 className={styles.title}>{title}</h1>
            {children && <div className={styles.lead}>{children}</div>}
          </div>
          {controls && <div className={styles.controls}>{controls}</div>}
        </div>
        {figures && <div className={styles.figures}>{figures}</div>}
      </div>
    </header>
  );
}

/** The column under the header that holds the panels of a page. */
export function PageContent({ children }: { children: ReactNode }) {
  return <div className={styles.content}>{children}</div>;
}

/** One figure in the foot of the header: a large value over its label. */
export function HeaderFigure({ value, label }: { value: ReactNode; label: string }) {
  return (
    <div className={styles.figure}>
      <span className={styles.figureValue}>{value}</span>
      <span className={styles.figureLabel}>{label}</span>
    </div>
  );
}
