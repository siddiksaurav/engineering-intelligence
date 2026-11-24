import { notFound } from "next/navigation";
import type { ReactNode } from "react";
import { LevelSwitch } from "@/components/LevelSwitch";
import { PageContent, PageHeader } from "@/components/Page";
import { Notice } from "@/components/Panel";
import { fetchDevelopers } from "@/lib/evidence";
import { fetchAnalytics, fetchDimensionScores, type Level } from "@/lib/profile";
import { profileParams } from "@/lib/profileParams";
import { compareHref } from "./compareHref";
import styles from "./compare.module.css";
import { Pickers } from "./Pickers";
import { ComplexitySection, ScoresSection, WorkKindSection, WorkLevelSection, type Side } from "./Sections";

// Read on every request, on the server. The browser never calls the backend.
export const dynamic = "force-dynamic";

type SearchParams = Record<string, string | string[] | undefined>;

export default async function ComparePage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const query = await searchParams;
  const defaultQuarter = process.env.DEFAULT_QUARTER || undefined;
  if (query.quarter === undefined && defaultQuarter === undefined) {
    return <Compare notice="No quarter is given and DEFAULT_QUARTER is not set in the web environment." />;
  }
  const selection = profileParams(query, defaultQuarter);
  const a = personIdOf(query.a);
  const b = personIdOf(query.b);
  if (!selection.valid || a === null || b === null) {
    notFound();
  }
  const { quarter, level } = selection;
  const backendUrl = process.env.BACKEND_URL;
  const controls = <LevelSwitch selected={level} hrefFor={(each) => compareHref({ a, b, quarter, level: each })} />;
  const developers = await fetchDevelopers(backendUrl, quarter);
  if (developers.status !== "ok") {
    return <Compare notice="backend down" controls={controls} />;
  }
  const pickers = <Pickers developers={sortedByName(developers.data)} selection={{ a, b, quarter, level }} />;
  if (a === undefined || b === undefined) {
    return <Compare controls={controls} pickers={pickers} notice="Choose two developers to compare." />;
  }
  if (a === b) {
    return <Compare controls={controls} pickers={pickers} notice="Choose two different developers." />;
  }
  const sides = await readSides(backendUrl, [a, b], quarter, level);
  if (sides === "down") {
    return <Compare controls={controls} pickers={pickers} notice="backend down" />;
  }
  return (
    <Compare controls={controls} pickers={pickers}>
      <ScoresSection sides={sides} />
      <WorkLevelSection sides={sides} />
      <WorkKindSection sides={sides} />
      <ComplexitySection sides={sides} />
    </Compare>
  );
}

/** A person id of the URL: undefined when absent, null when it is not a whole number. */
function personIdOf(parameter: string | string[] | undefined): number | undefined | null {
  if (parameter === undefined) {
    return undefined;
  }
  return typeof parameter === "string" && /^\d+$/.test(parameter) ? Number(parameter) : null;
}

function sortedByName<T extends { displayName: string }>(people: T[]): T[] {
  return [...people].sort((first, second) => first.displayName.localeCompare(second.displayName));
}

async function readSides(
  backendUrl: string | undefined,
  personIds: [number, number],
  quarter: string,
  level: Level,
): Promise<[Side, Side] | "down"> {
  const [first, second] = await Promise.all(
    personIds.map((personId) => readSide(backendUrl, personId, quarter, level)),
  );
  return first === "down" || second === "down" ? "down" : [first, second];
}

async function readSide(
  backendUrl: string | undefined,
  personId: number,
  quarter: string,
  level: Level,
): Promise<Side | "down"> {
  const [analytics, scores] = await Promise.all([
    fetchAnalytics(backendUrl, personId, quarter),
    fetchDimensionScores(backendUrl, personId, quarter, level),
  ]);
  if (analytics.status === "not-found" || scores.status === "not-found") {
    notFound();
  }
  if (analytics.status === "down" || scores.status === "down") {
    return "down";
  }
  return { analytics: analytics.data, scores: scores.data };
}

function Compare({
  controls,
  pickers,
  notice,
  children,
}: {
  controls?: ReactNode;
  pickers?: ReactNode;
  notice?: string;
  children?: ReactNode;
}) {
  return (
    <main>
      <PageHeader
        crumbs={[
          { href: "/", label: "Overview" },
          { href: "/developers", label: "Developers" },
        ]}
        title="Compare two developers"
        controls={controls}
      >
        Scores, work level and kind of work, side by side.
      </PageHeader>
      <PageContent>
        {pickers}
        {notice && <Notice>{notice}</Notice>}
        {children && <div className={styles.sections}>{children}</div>}
      </PageContent>
    </main>
  );
}
