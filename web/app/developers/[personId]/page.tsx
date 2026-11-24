import Link from "next/link";
import { notFound } from "next/navigation";
import { Avatar } from "@/components/Avatar";
import { Swatch } from "@/components/EvidenceTypes";
import { PageContent, PageHeader } from "@/components/Page";
import { Notice } from "@/components/Panel";
import { fetchEvidenceItems, type EvidenceItem } from "@/lib/evidence";
import { EVIDENCE_SECTIONS } from "@/lib/evidenceSections";
import { typeColor } from "@/lib/palette";
import { profileParams } from "@/lib/profileParams";
import { PullRequestItemSection } from "./PullRequestItemSection";
import { ReviewCommentSection } from "./ReviewCommentSection";
import { WorkItemSection } from "./WorkItemSection";
import styles from "./developer.module.css";

// Read on every request, on the server. The browser never calls the backend.
export const dynamic = "force-dynamic";

type SearchParams = Record<string, string | string[] | undefined>;

export default async function DeveloperPage({
  params,
  searchParams,
}: {
  params: Promise<{ personId: string }>;
  searchParams: Promise<SearchParams>;
}) {
  const { personId } = await params;
  const query = await searchParams;
  if (!/^\d+$/.test(personId)) {
    notFound();
  }
  const defaultQuarter = process.env.DEFAULT_QUARTER || undefined;
  if (query.quarter === undefined && defaultQuarter === undefined) {
    return (
      <main>
        <PageHeader crumbs={[{ href: "/developers", label: "Developers" }]} title="Evidence list" />
        <PageContent>
          <Notice>No quarter is given and DEFAULT_QUARTER is not set in the web environment.</Notice>
        </PageContent>
      </main>
    );
  }
  const selection = profileParams(query, defaultQuarter);
  if (!selection.valid) {
    notFound();
  }
  const result = await fetchEvidenceItems(process.env.BACKEND_URL, Number(personId), selection.quarter);
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
        <span className={styles.headerQuarter}>{selection.quarter}</span>
        Every current evidence item of the quarter, with its sources.{" "}
        <Link href={`/developers/${personId}/profile?quarter=${selection.quarter}&level=${selection.level}`}>
          Profile
        </Link>
      </PageHeader>
      <PageContent>
        {result.status === "down" ? (
          <Notice>backend down</Notice>
        ) : result.data.items.length === 0 ? (
          <Notice>No evidence items</Notice>
        ) : (
          <>
            <TypeSummary items={result.data.items} />
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

/** The number of items and the count per type; a type that has items links to its section further down. */
function TypeSummary({ items }: { items: EvidenceItem[] }) {
  return (
    <nav className={styles.summary} aria-label="Evidence types">
      <p className={styles.summaryTotal}>
        <strong>{items.length}</strong> evidence items
      </p>
      <ul className={styles.summaryTypes}>
        {EVIDENCE_SECTIONS.map((section) => {
          const count = ofType(items, section.type).length;
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
