import Link from "next/link";
import { notFound } from "next/navigation";
import { fetchItem, type ItemDetail } from "@/lib/items";
import { EVIDENCE_SECTIONS } from "../../developers/evidenceSections";
import { JudgmentView } from "./JudgmentView";
import styles from "./item.module.css";

// Read on every request, on the server. The browser never calls the backend.
export const dynamic = "force-dynamic";

export default async function ItemPage({ params }: { params: Promise<{ itemId: string }> }) {
  const { itemId } = await params;
  if (!/^\d+$/.test(itemId)) {
    notFound();
  }
  const result = await fetchItem(process.env.BACKEND_URL, Number(itemId));
  if (result.status === "not-found") {
    notFound();
  }
  return (
    <main>
      <header className={styles.header}>
        <Link href="/developers" className={styles.back}>
          Developers
        </Link>
        <h1>{result.status === "ok" ? result.data.title : "Evidence item"}</h1>
      </header>
      <div className={styles.content}>
        {result.status === "down" ? <p className={styles.notice}>backend down</p> : <Item item={result.data} />}
      </div>
    </main>
  );
}

function Item({ item }: { item: ItemDetail }) {
  const typeTitle = EVIDENCE_SECTIONS.find((section) => section.type === item.type)?.title ?? item.type;
  return (
    <>
      {!item.current && <Superseded currentItemId={item.currentItemId} />}
      <p className={styles.meta}>
        <strong>{typeTitle}</strong>, {item.date}, {item.quarter}, by{" "}
        <Link href={`/developers/${item.person.personId}/profile`}>{item.person.displayName}</Link>
      </p>
      <ul className={styles.links}>
        {item.links.map((link) => (
          <li key={link.url}>
            <a href={link.url}>{link.label}</a>
          </li>
        ))}
      </ul>
      <section className={styles.judgment}>
        <h2>AI judgment</h2>
        <JudgmentView item={item} />
      </section>
    </>
  );
}

function Superseded({ currentItemId }: { currentItemId: number | null }) {
  return (
    <p className={styles.notice}>
      This item is no longer current.{" "}
      {currentItemId !== null && <Link href={`/items/${currentItemId}`}>Open the current item</Link>}
    </p>
  );
}
