import Link from "next/link";
import { notFound } from "next/navigation";
import { Swatch } from "@/components/EvidenceTypes";
import { PageContent, PageHeader } from "@/components/Page";
import { Notice } from "@/components/Panel";
import { typeTitle } from "@/lib/evidenceSections";
import { featureWorkProblems } from "@/lib/featureWorkProblems";
import { fetchItem, type ItemDetail } from "@/lib/items";
import { typeColor } from "@/lib/palette";
import { JudgmentView } from "./JudgmentView";
import { ReasonMarkForm } from "./ReasonMarkForm";
import { YourLabel } from "./YourLabel";
import styles from "./item.module.css";

// Read on every request, on the server. The browser never calls the backend.
export const dynamic = "force-dynamic";

const CRUMBS = [
  { href: "/", label: "Overview" },
  { href: "/developers", label: "Developers" },
];

export default async function ItemPage({ params }: { params: Promise<{ itemId: string }> }) {
  const { itemId } = await params;
  if (!/^\d+$/.test(itemId)) {
    notFound();
  }
  const result = await fetchItem(process.env.BACKEND_URL, Number(itemId));
  if (result.status === "not-found") {
    notFound();
  }
  if (result.status === "down") {
    return (
      <main>
        <PageHeader crumbs={CRUMBS} title="Evidence item" />
        <PageContent>
          <Notice>backend down</Notice>
        </PageContent>
      </main>
    );
  }
  const item = result.data;
  return (
    <main>
      <PageHeader
        crumbs={[
          ...CRUMBS,
          { href: `/developers/${item.person.personId}?quarter=${item.quarter}`, label: item.person.displayName },
        ]}
        title={item.title}
      >
        <span className={styles.meta}>
          <span className={styles.type}>
            <Swatch color={typeColor(item.type)} />
            <strong>{typeTitle(item.type) ?? item.type}</strong>
          </span>
          <span className={styles.date}>{item.date}</span>
          <span className={styles.date}>{item.quarter}</span>
          <span>
            by{" "}
            <Link href={`/developers/${item.person.personId}/profile?quarter=${item.quarter}`}>
              {item.person.displayName}
            </Link>
          </span>
        </span>
      </PageHeader>
      <PageContent>
        <Item item={item} />
      </PageContent>
    </main>
  );
}

function Item({ item }: { item: ItemDetail }) {
  return (
    <div className={styles.columns}>
      {!item.current && <Superseded currentItemId={item.currentItemId} />}
      <section className={styles.judgment}>
        <h2>AI judgment</h2>
        <JudgmentView item={item} />
        {canMarkReason(item) && (
          <ReasonMarkForm
            itemId={item.id}
            problemNames={featureWorkProblems(item.judgment)?.problems.map((problem) => problem.name) ?? []}
            stored={item.blindCheck?.reasonMark ?? null}
          />
        )}
      </section>
      <div className={styles.side}>
        <YourLabel item={item} />
        <aside className={styles.sources}>
          <h2>Sources</h2>
          <ul className={styles.links}>
            {item.links.map((link) => (
              <li key={link.url}>
                <a href={link.url}>{link.label}</a>
              </li>
            ))}
          </ul>
        </aside>
      </div>
    </div>
  );
}

/** The reason is marked on a labeled item of the sample, except one the user could not judge (design flow 6). */
function canMarkReason(item: ItemDetail): boolean {
  return item.blindCheck?.label?.complexity != null;
}

function Superseded({ currentItemId }: { currentItemId: number | null }) {
  return (
    <div className={styles.superseded}>
      <Notice tone="warning">
        This item is no longer current.{" "}
        {currentItemId !== null && <Link href={`/items/${currentItemId}`}>Open the current item</Link>}
      </Notice>
    </div>
  );
}
