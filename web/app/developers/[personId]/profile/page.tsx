import Link from "next/link";
import { notFound } from "next/navigation";
import { type Analytics, type DimensionScores, fetchAnalytics, fetchDimensionScores, type Level } from "@/lib/profile";
import { fetchItemSummaries } from "@/lib/items";
import { itemIdsOnPage, notJudgedYet, profileParams } from "@/lib/profileParams";
import { percent } from "@/lib/profileText";
import developers from "../../developers.module.css";
import { DimensionCard } from "./DimensionCard";
import { GatesPanel } from "./GatesPanel";
import type { SummariesById } from "./ItemReferences";
import styles from "./profile.module.css";

// Read on every request, on the server. The browser never calls the backend.
export const dynamic = "force-dynamic";

const LEVELS: Level[] = ["L1", "L2", "L3"];

type SearchParams = Record<string, string | string[] | undefined>;

export default async function ProfilePage({
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
      <Shell title="Profile" notice="No quarter is given and DEFAULT_QUARTER is not set in the web environment." />
    );
  }
  const selection = profileParams(query, defaultQuarter);
  if (!selection.valid) {
    notFound();
  }
  const backendUrl = process.env.BACKEND_URL;
  const person = Number(personId);
  const [analytics, scores] = await Promise.all([
    fetchAnalytics(backendUrl, person, selection.quarter),
    fetchDimensionScores(backendUrl, person, selection.quarter, selection.level),
  ]);
  if (analytics.status === "not-found" || scores.status === "not-found") {
    notFound();
  }
  if (analytics.status === "down" || scores.status === "down") {
    return <Shell title="Profile" notice="backend down" />;
  }
  const summaries = await fetchItemSummaries(backendUrl, itemIdsOnPage(analytics.data, scores.data));
  const summariesById: SummariesById = new Map(
    (summaries.status === "ok" ? summaries.data : []).map((summary) => [summary.id, summary]),
  );
  return <Profile analytics={analytics.data} scores={scores.data} summariesById={summariesById} />;
}

function Profile({
  analytics,
  scores,
  summariesById,
}: {
  analytics: Analytics;
  scores: DimensionScores;
  summariesById: SummariesById;
}) {
  const unjudged = notJudgedYet(analytics, scores);
  const gates = scores.dimensions.find((dimension) => dimension.gates.length > 0)?.gates ?? [];
  return (
    <main>
      <ProfileHeader analytics={analytics} scores={scores} />
      <div className={developers.content}>
        {(unjudged.pullRequests > 0 || unjudged.items > 0) && (
          <p className={styles.warning}>
            {unjudged.pullRequests} PRs and {unjudged.items} items are not judged yet; the numbers will change.
          </p>
        )}
        {scores.scored ? (
          <>
            {scores.dimensions.map((dimension) => (
              <DimensionCard
                key={dimension.key}
                dimension={dimension}
                level={scores.level}
                summariesById={summariesById}
              />
            ))}
            {gates.length > 0 && <GatesPanel gates={gates} summariesById={summariesById} />}
          </>
        ) : (
          <NoScores quarter={scores.quarter} />
        )}
      </div>
    </main>
  );
}

function ProfileHeader({ analytics, scores }: { analytics: Analytics; scores: DimensionScores }) {
  const { personId, displayName } = analytics.person;
  return (
    <header className={developers.header}>
      <nav className={styles.headerLinks}>
        <Link href="/developers" className={developers.back}>
          Developers
        </Link>
        <Link href={`/developers/${personId}`} className={developers.back}>
          Evidence list
        </Link>
      </nav>
      <h1>{displayName}</h1>
      <p className={styles.headerLine}>
        {scores.quarter}, hygiene {percent(analytics.hygiene.share)} of pull requests linked to a work item
      </p>
      <nav className={styles.levels} aria-label="Level">
        {LEVELS.map((level) => (
          <Link
            key={level}
            href={`/developers/${personId}/profile?quarter=${scores.quarter}&level=${level}`}
            className={level === scores.level ? styles.levelSelected : styles.level}
            aria-current={level === scores.level ? "page" : undefined}
          >
            {level}
          </Link>
        ))}
      </nav>
    </header>
  );
}

function NoScores({ quarter }: { quarter: string }) {
  return (
    <section className={styles.warning}>
      <h2>No scores for {quarter} yet</h2>
      <p>
        The scores are made by the dimension-scoring job: start it with{" "}
        <code>POST /api/jobs/dimension-scoring/runs</code> and the body <code>{`{"quarter":"${quarter}"}`}</code>.
      </p>
    </section>
  );
}

function Shell({ title, notice }: { title: string; notice: string }) {
  return (
    <main>
      <header className={developers.header}>
        <Link href="/developers" className={developers.back}>
          Developers
        </Link>
        <h1>{title}</h1>
      </header>
      <div className={developers.content}>
        <p className={developers.notice}>{notice}</p>
      </div>
    </main>
  );
}
