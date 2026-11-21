import Link from "next/link";
import { notFound } from "next/navigation";
import { fetchEvidenceItems, type EvidenceItem } from "@/lib/evidence";
import { EVIDENCE_SECTIONS } from "../evidenceSections";
import developers from "../developers.module.css";
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
  const result = await fetchEvidenceItems(process.env.BACKEND_URL, Number(personId));
  if (result.status === "not-found") {
    notFound();
  }
  return (
    <main>
      <header className={developers.header}>
        <Link href="/developers" className={developers.back}>
          Developers
        </Link>
        <h1>{result.status === "ok" ? result.data.person.displayName : "Developer"}</h1>
      </header>
      <div className={developers.content}>
        {result.status === "down" ? (
          <p className={developers.notice}>backend down</p>
        ) : result.data.items.length === 0 ? (
          <p className={developers.notice}>No evidence items</p>
        ) : (
          <Sections items={result.data.items} />
        )}
      </div>
    </main>
  );
}

function Sections({ items }: { items: EvidenceItem[] }) {
  return (
    <>
      {EVIDENCE_SECTIONS.map((section) => {
        const ofType = items.filter((item) => item.type === section.type);
        const accentClass = styles[section.colorClass];
        if (section.type === "review_comment_written") {
          return <ReviewCommentSection key={section.type} items={ofType} />;
        }
        const Section =
          section.type === "feature_work" || section.type === "bug_fix" ? WorkItemSection : PullRequestItemSection;
        return <Section key={section.type} title={section.title} accentClass={accentClass} items={ofType} />;
      })}
    </>
  );
}
