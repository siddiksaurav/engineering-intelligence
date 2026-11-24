import Link from "next/link";
import { notFound } from "next/navigation";
import { Avatar } from "@/components/Avatar";
import { Swatch, TypeBar } from "@/components/EvidenceTypes";
import { PageContent, PageHeader } from "@/components/Page";
import { Notice } from "@/components/Panel";
import { fetchEvidenceItems, type EvidenceItem, type TypeCounts } from "@/lib/evidence";
import { EVIDENCE_SECTIONS } from "@/lib/evidenceSections";
import { typeColor } from "@/lib/palette";
import { PullRequestItemSection } from "./PullRequestItemSection";
import { ReviewCommentSection } from "./ReviewCommentSection";
import { WorkItemSection } from "./WorkItemSection";
import styles from "./developer.module.css";

// Read on every request, on the server. The browser never calls the backend.
export const dynamic = "force-dynamic";

export default async function DeveloperPage({ params }: { params: Promise<{ personId: string }> }) {
  const { personId } = await params;
  if (!/^\d+$/.test(personId)) {
    notFound();
  }
  const quarter = process.env.DEFAULT_QUARTER || undefined;
  if (quarter === undefined) {
    return (
      <main>
        <PageHeader crumbs={[{ href: "/developers", label: "Developers" }]} title="Evidence list" />
        <PageContent>
          <Notice>Set DEFAULT_QUARTER in the web environment, for example 2026-Q3.</Notice>
        </PageContent>
      </main>
    );
  }
  const result = await fetchEvidenceItems(process.env.BACKEND_URL, Number(personId), quarter);
  if (result.status === "not-found") {
    notFound();
  }
  const name = result.status === "ok" ? result.data.person.displayName : "Developer";
  return (
    <main>
      <PageHeader
        crumbs={[
          { href: "/", label: "Overview" },
          { href: "/developers", label: "Developers" },
        ]}
        title={
          <span className={styles.person}>
            <Avatar name={name} size="large" />
            {name}
          </span>
        }
      >
        Evidence list: every current evidence item, with its sources.{" "}
        <Link href={`/developers/${personId}/profile`}>Profile</Link>
      </PageHeader>
      <PageContent>
        {result.status === "down" ? (
          <Notice>backend down</Notice>
        ) : result.data.items.length === 0 ? (
          <Notice>No evidence items</Notice>
        ) : (
          <>
            <TypeSummary name={name} items={result.data.items} />
            <Sections items={result.data.items} />
          </>
        )}
      </PageContent>
    </main>
  );
}

function ofType(items: EvidenceItem[], type: EvidenceItem["type"]): EvidenceItem[] {
  return items.filter((item) => item.type === type);
}

/** The items per type as one bar and a list; a type that has items links to its section further down. */
function TypeSummary({ name, items }: { name: string; items: EvidenceItem[] }) {
  const counts = Object.fromEntries(
    EVIDENCE_SECTIONS.map((section) => [section.countKey, ofType(items, section.type).length]),
  ) as TypeCounts;
  return (
    <nav className={styles.summary} aria-label="Evidence types">
      <p className={styles.summaryTotal}>
        <strong>{items.length}</strong> evidence items
      </p>
      <TypeBar label={`Evidence of ${name}`} counts={counts} />
      <ul className={styles.summaryTypes}>
        {EVIDENCE_SECTIONS.map((section) => {
          const count = counts[section.countKey];
          const entry = (
            <>
              <Swatch color={typeColor(section.type)} />
              {section.title}
              <span className={styles.summaryCount}>{count}</span>
            </>
          );
          return (
            <li key={section.type}>
              {count === 0 ? (
                <span className={styles.summaryEmpty}>{entry}</span>
              ) : (
                <a href={`#${section.type}`} className={styles.summaryLink}>
                  {entry}
                </a>
              )}
            </li>
          );
        })}
      </ul>
    </nav>
  );
}

/** One section per type that has items, in the order of the summary. */
function Sections({ items }: { items: EvidenceItem[] }) {
  return (
    <>
      {EVIDENCE_SECTIONS.map((section) => {
        const sectionItems = ofType(items, section.type);
        if (sectionItems.length === 0) {
          return null;
        }
        if (section.type === "review_comment_written") {
          return <ReviewCommentSection key={section.type} items={sectionItems} />;
        }
        const Section =
          section.type === "feature_work" || section.type === "bug_fix" ? WorkItemSection : PullRequestItemSection;
        return <Section key={section.type} type={section.type} title={section.title} items={sectionItems} />;
      })}
    </>
  );
}
