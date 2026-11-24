import Link from "next/link";
import { notFound } from "next/navigation";
import type { ReactNode } from "react";
import { Avatar } from "@/components/Avatar";
import { TypeBar, TypeLegend } from "@/components/EvidenceTypes";
import { LevelSwitch } from "@/components/LevelSwitch";
import { PageContent, PageHeader } from "@/components/Page";
import { Notice } from "@/components/Panel";
import { ScoreLine } from "@/components/ScoreLine";
import { fetchDevelopers, type Developer } from "@/lib/evidence";
import type { Level } from "@/lib/profile";
import { profileParams } from "@/lib/profileParams";
import { fetchTeamScores, totalOf, type DeveloperScore } from "@/lib/team";
import styles from "./developers.module.css";

// Read on every request, on the server. The browser never calls the backend.
export const dynamic = "force-dynamic";

type SearchParams = Record<string, string | string[] | undefined>;

export default async function DevelopersPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const query = await searchParams;
  const defaultQuarter = process.env.DEFAULT_QUARTER || undefined;
  if (query.quarter === undefined && defaultQuarter === undefined) {
    return (
      <Roster
        lead="The developers need a quarter."
        notice="Set DEFAULT_QUARTER in the web environment, for example 2026-Q3."
      />
    );
  }
  const selection = profileParams(query, defaultQuarter);
  if (!selection.valid) {
    notFound();
  }
  const { quarter, level } = selection;
  const backendUrl = process.env.BACKEND_URL;
  const controls = <LevelSwitch selected={level} hrefFor={(each) => `/developers?quarter=${quarter}&level=${each}`} />;
  const result = await fetchDevelopers(backendUrl, quarter);
  if (result.status !== "ok") {
    return <Roster lead="The developers could not be read." notice="backend down" controls={controls} />;
  }
  if (result.data.length === 0) {
    return <Roster lead="Persons made of the stored GitHub accounts." notice="No developers" controls={controls} />;
  }
  const developers = [...result.data].sort((first, second) => first.displayName.localeCompare(second.displayName));
  const scores = await fetchTeamScores(
    backendUrl,
    developers.map((developer) => developer.personId),
    quarter,
    level,
  );
  return (
    <Roster
      lead={`${developers.length} developers by name. Scores of ${quarter}, read at ${level}.`}
      controls={controls}
    >
      <section className={styles.roster}>
        <header className={styles.rosterHead}>
          <TypeLegend />
          <Link href={`/developers/compare?quarter=${quarter}&level=${level}`}>Compare two developers</Link>
        </header>
        <ul className={styles.rows}>
          {developers.map((developer) => (
            <DeveloperRow
              key={developer.personId}
              developer={developer}
              scores={scores.get(developer.personId)}
              quarter={quarter}
              level={level}
            />
          ))}
        </ul>
      </section>
    </Roster>
  );
}

function Roster({
  lead,
  notice,
  controls,
  children,
}: {
  lead: string;
  notice?: string;
  controls?: ReactNode;
  children?: ReactNode;
}) {
  return (
    <main>
      <PageHeader crumbs={[{ href: "/", label: "Overview" }]} title="Developers" controls={controls}>
        {lead}
      </PageHeader>
      <PageContent>
        {notice && <Notice>{notice}</Notice>}
        {children}
      </PageContent>
    </main>
  );
}

function DeveloperRow({
  developer,
  scores,
  quarter,
  level,
}: {
  developer: Developer;
  scores: DeveloperScore[] | undefined;
  quarter: string;
  level: Level;
}) {
  const total = totalOf(developer.counts);
  return (
    <li className={styles.row}>
      <div className={styles.who}>
        <Avatar name={developer.displayName} />
        <div className={styles.whoText}>
          <Link
            href={`/developers/${developer.personId}/profile?quarter=${quarter}&level=${level}`}
            className={styles.name}
          >
            {developer.displayName}
          </Link>
          <span className={styles.links}>
            <Link href={`/developers/${developer.personId}?quarter=${quarter}&level=${level}`}>Evidence list</Link>
            <Link href={`/developers/compare?a=${developer.personId}&quarter=${quarter}&level=${level}`}>Compare</Link>
          </span>
        </div>
      </div>
      <Scores scores={scores} />
      <div className={styles.items}>
        <p className={styles.total}>
          <strong>{total}</strong> items
        </p>
        {total > 0 && <TypeBar label={`Items of ${developer.displayName}`} counts={developer.counts} size="thin" />}
      </div>
    </li>
  );
}

function Scores({ scores }: { scores: DeveloperScore[] | undefined }) {
  if (scores === undefined) {
    return <p className={styles.notScored}>not scored</p>;
  }
  return (
    <ul className={styles.scores}>
      {scores.map((dimension) => (
        <li key={dimension.key} className={styles.score}>
          <span className={styles.dimension}>{dimension.name}</span>
          <ScoreLine score={dimension.score} workingTier={dimension.workingTier} />
        </li>
      ))}
    </ul>
  );
}
