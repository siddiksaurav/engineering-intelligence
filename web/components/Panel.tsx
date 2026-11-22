import type { ReactNode } from "react";
import styles from "./Panel.module.css";

/** A white card with a title and, when given, one line that says what the card shows. */
export function Panel({
  title,
  note,
  className,
  children,
}: {
  title: string;
  note?: ReactNode;
  className?: string;
  children: ReactNode;
}) {
  return (
    <section className={className ? `${styles.panel} ${className}` : styles.panel}>
      <header className={styles.head}>
        <h2 className={styles.title}>{title}</h2>
        {note && <p className={styles.note}>{note}</p>}
      </header>
      {children}
    </section>
  );
}

type NoticeTone = "info" | "warning";

/** A message about the state of the page: nothing to show, a backend that is down, numbers that will change. */
export function Notice({ tone = "info", children }: { tone?: NoticeTone; children: ReactNode }) {
  return <div className={`${styles.notice} ${styles[tone]}`}>{children}</div>;
}
