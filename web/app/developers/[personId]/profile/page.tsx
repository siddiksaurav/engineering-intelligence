import Link from "next/link";
import { notFound } from "next/navigation";
import { type Analytics, type DimensionScores, fetchAnalytics, fetchDimensionScores } from "@/lib/profile";
import { fetchItemSummaries } from "@/lib/items";
import { itemIdsOnPage, notJudgedYet, profileParams } from "@/lib/profileParams";
import { Avatar } from "@/components/Avatar";
import { LevelSwitch } from "@/components/LevelSwitch";
import { HeaderFigure, PageContent, PageHeader } from "@/components/Page";
import { Notice } from "@/components/Panel";
import { DimensionsSection } from "./DimensionsSection";
import type { SummariesById } from "./ItemReferences";
import { QualityChecksSection } from "./QualityChecksSection";
import { ReviewsSection } from "./ReviewsSection";
import styles from "./profile.module.css";
import { TrailSection } from "./TrailSection";
import { WorkKindSection } from "./WorkKindSection";
import { WorkLevelSection } from "./WorkLevelSection";

// Read on every request, on the server. The browser never calls the backend.
export const dynamic = "force-dynamic";

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
  const itemIds = itemIdsOnPage(analytics.data, scores.data);
  const summaries = await fetchItemSummaries(backendUrl, itemIds);
  const summariesById: SummariesById = new Map(
    (summaries.status === "ok" ? summaries.data : []).map((summary) => [summary.id, summary]),
  );
  warnAboutMissingSummaries(itemIds, summaries.status === "ok" ? summariesById : null);
  return <Profile analytics={analytics.data} scores={scores.data} summariesById={summariesById} />;
}

/** An id the batch call did not answer is left out of the lists and named here; the page still shows it as "item #id". */
function warnAboutMissingSummaries(itemIds: number[], summariesById: SummariesById | null): void {
  if (summariesById === null) {
    return;
  }
  const missing = itemIds.filter((itemId) => !summariesById.has(itemId));
  if (missing.length > 0) {
    console.warn(`The evidence item batch answer has no item ${missing.join(", ")}`);
  }
}

function plural(count: number, noun: string): string {
  return `${count} ${noun}${count === 1 ? "" : "s"}`;
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
      <PageContent>
        {(unjudged.pullRequests > 0 || unjudged.items > 0) && (
          <Notice tone="warning">
            {plural(unjudged.pullRequests, "PR")} and {plural(unjudged.items, "item")} are not judged yet; the numbers
            will change.
          </Notice>
        )}
        <div className={styles.sections}>
          <WorkLevelSection analytics={analytics} />
          <WorkKindSection analytics={analytics} summariesById={summariesById} />
        </div>
        {scores.scored ? (
          <DimensionsSection dimensions={scores.dimensions} level={scores.level} />
        ) : (
          <NoScores quarter={scores.quarter} />
        )}
        <div className={styles.sections}>
          {scores.scored && <QualityChecksSection gates={gates} summariesById={summariesById} />}
          <ReviewsSection comments={analytics.reviewParticipation.comments} summariesById={summariesById} />
        </div>
        {scores.scored && <TrailSection dimensions={scores.dimensions} summariesById={summariesById} />}
      </PageContent>
    </main>
  );
}

function ProfileHeader({ analytics, scores }: { analytics: Analytics; scores: DimensionScores }) {
  const { personId, displayName } = analytics.person;
  const { judged, total } = analytics.workClassification.coverage;
  return (
    <PageHeader
      crumbs={[
        { href: "/", label: "Overview" },
        { href: "/developers", label: "Developers" },
      ]}
      title={
        <span className={styles.person}>
          <Avatar name={displayName} size="large" />
          {displayName}
        </span>
      }
      controls={
        <LevelSwitch
          selected={scores.level}
          hrefFor={(level) => `/developers/${personId}/profile?quarter=${scores.quarter}&level=${level}`}
        />
      }
      figures={scores.dimensions.map((dimension) => (
        <HeaderFigure
          key={dimension.key}
          value={`${dimension.score} of 2`}
          label={`${dimension.name}, ${dimension.workingTier ? `works at ${dimension.workingTier}` : "no working tier"}`}
        />
      ))}
    >
      <span className={styles.quarter}>{scores.quarter}</span>
      <span className={styles.quarter}>
        Based on {judged} of {total} PRs judged
      </span>
      <Link href={`/developers/${personId}`}>Evidence list</Link>
    </PageHeader>
  );
}

function NoScores({ quarter }: { quarter: string }) {
  return (
    <Notice tone="warning">
      <h2>No scores for {quarter} yet</h2>
      <p>
        The scores are made by the dimension-scoring job: start it with{" "}
        <code>POST /api/jobs/dimension-scoring/runs</code> and the body <code>{`{"quarter":"${quarter}"}`}</code>.
      </p>
    </Notice>
  );
}

function Shell({ title, notice }: { title: string; notice: string }) {
  return (
    <main>
      <PageHeader
        crumbs={[
          { href: "/", label: "Overview" },
          { href: "/developers", label: "Developers" },
        ]}
        title={title}
      />
      <PageContent>
        <Notice>{notice}</Notice>
      </PageContent>
    </main>
  );
}
