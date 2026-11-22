import Link from "next/link";
import type { ReactNode } from "react";
import { Avatar } from "@/components/Avatar";
import { TypeBar, TypeLegend } from "@/components/EvidenceTypes";
import { PageContent, PageHeader } from "@/components/Page";
import { Notice } from "@/components/Panel";
import { TierSteps } from "@/components/TierSteps";
import { fetchDevelopers, type Developer } from "@/lib/evidence";
import { fetchTeamScores, totalOf, type DeveloperScore, type TeamScores } from "@/lib/team";
import styles from "./developers.module.css";

// Read on every request, on the server. The browser never calls the backend.
export const dynamic = "force-dynamic";

/** The level the roster reads the scores at: the level a profile opens with (decision 247). */
const ROSTER_LEVEL = "L2";

export default async function DevelopersPage() {
  const backendUrl = process.env.BACKEND_URL;
  const quarter = process.env.DEFAULT_QUARTER || undefined;
  const result = await fetchDevelopers(backendUrl);
  if (result.status !== "ok") {
    return <Roster lead="The developers could not be read." notice="backend down" />;
  }
  const developers = result.data;
  if (developers.length === 0) {
    return <Roster lead="Persons made of the stored GitHub accounts." notice="No developers" />;
  }
  const scores =
    quarter === undefined
      ? null
      : await fetchTeamScores(
          backendUrl,
          developers.map((developer) => developer.personId),
          quarter,
          ROSTER_LEVEL,
        );
  const mostItems = Math.max(...developers.map((developer) => totalOf(developer.counts)));
  const lead =
    quarter === undefined
      ? `${developers.length} developers.`
      : `${developers.length} developers. Scores of ${quarter}, read at ${ROSTER_LEVEL}.`;
  return (
    <Roster lead={lead}>
      {scores === null && (
        <Notice>
          Scores need a quarter: set <code>DEFAULT_QUARTER</code> in the web environment, for example{" "}
          <code>2026-Q3</code>.
        </Notice>
      )}
      <section className={styles.roster}>
        <header className={styles.rosterHead}>
          <TypeLegend />
        </header>
        {scores !== null && <ColumnTitles scores={scores} />}
        <ul className={styles.rows}>
          {developers.map((developer) => (
            <DeveloperRow key={developer.personId} developer={developer} mostItems={mostItems} scores={scores} />
          ))}
        </ul>
      </section>
    </Roster>
  );
}

function Roster({ lead, notice, children }: { lead: string; notice?: string; children?: ReactNode }) {
  return (
    <main>
      <PageHeader crumbs={[{ href: "/", label: "Overview" }]} title="Developers">
        {lead}
      </PageHeader>
      <PageContent>
        {notice && <Notice>{notice}</Notice>}
        {children}
      </PageContent>
    </main>
  );
}

/** One developer; the evidence bar is as long as the developer's share of the largest total, so rows compare. */
function DeveloperRow({
  developer,
  mostItems,
  scores,
}: {
  developer: Developer;
  mostItems: number;
  scores: TeamScores | null;
}) {
  const total = totalOf(developer.counts);
  return (
    <li className={styles.row}>
      <div className={styles.who}>
        <Avatar name={developer.displayName} />
        <div className={styles.whoText}>
          <Link href={`/developers/${developer.personId}/profile`} className={styles.name}>
            {developer.displayName}
          </Link>
          <Link href={`/developers/${developer.personId}`} className={styles.evidenceLink}>
            Evidence list
          </Link>
        </div>
      </div>
      <div className={styles.evidence}>
        <p className={styles.total}>
          <strong>{total}</strong> items
        </p>
        {total > 0 && (
          <div style={{ width: `${(total / mostItems) * 100}%` }} className={styles.evidenceBar}>
            <TypeBar label={`Evidence of ${developer.displayName}`} counts={developer.counts} size="thin" />
          </div>
        )}
      </div>
      {scores !== null && <Scores scores={scores.get(developer.personId)} />}
    </li>
  );
}

/**
 * The titles of the columns on a wide screen, where the rows leave the dimension names out. Hidden from a screen
 * reader, which reads the name of the dimension in every row.
 */
function ColumnTitles({ scores }: { scores: TeamScores }) {
  const dimensions = scores.values().next().value ?? [];
  return (
    <div className={styles.columnTitles} aria-hidden="true">
      <span>Developer</span>
      <span>Evidence items</span>
      <span className={styles.scores}>
        {dimensions.map((dimension) => (
          <span key={dimension.key}>{dimension.name}</span>
        ))}
      </span>
    </div>
  );
}

function Scores({ scores }: { scores: DeveloperScore[] | undefined }) {
  if (scores === undefined) {
    return <p className={styles.notScored}>not scored</p>;
  }
  return (
    <ul className={styles.scores}>
      {scores.map((dimension) => (
        <li key={dimension.key} className={styles.score} data-score={dimension.score}>
          <span className={styles.dimension}>{dimension.name}</span>
          <span className={styles.scoreLine}>
            <span className={styles.scoreValue}>
              <strong>{dimension.score}</strong> of 2
            </span>
            <TierSteps workingTier={dimension.workingTier} size="small" />
            <span className={styles.tier}>{dimension.workingTier ?? "no working tier"}</span>
          </span>
        </li>
      ))}
    </ul>
  );
}
