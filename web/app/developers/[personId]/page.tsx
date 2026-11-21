import Link from "next/link";
import { notFound } from "next/navigation";
import { fetchEvidenceItems, type EvidenceItem, type EvidenceType } from "@/lib/evidence";
import developers from "../developers.module.css";
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
  const ofType = (type: EvidenceType) => items.filter((item) => item.type === type);
  return (
    <>
      <WorkItemSection title="Feature work" accentClass={styles.featureWork} items={ofType("feature_work")} />
      <WorkItemSection title="Bug fix" accentClass={styles.bugFix} items={ofType("bug_fix")} />
      <ReviewCommentSection items={ofType("review_comment_written")} />
    </>
  );
}
