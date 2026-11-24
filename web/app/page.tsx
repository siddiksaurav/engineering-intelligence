import Link from "next/link";
import { notFound } from "next/navigation";
import type { ReactNode } from "react";
import { DonutChart } from "@/components/charts/DonutChart";
import { GroupedBarChart } from "@/components/charts/GroupedBarChart";
import { StackedBarChart } from "@/components/charts/StackedBarChart";
import { LevelSwitch } from "@/components/LevelSwitch";
import { HeaderFigure, PageContent, PageHeader } from "@/components/Page";
import { Notice, Panel } from "@/components/Panel";
import { sliceCount } from "@/lib/counts";
import { fetchDevelopers } from "@/lib/evidence";
import { checkBackend, type BackendStatus } from "@/lib/health";
import { SINGLE_HUE_COLOR, scoreColor, workTypeColor } from "@/lib/palette";
import type { Level } from "@/lib/profile";
import { profileParams } from "@/lib/profileParams";
import { percent, plainName } from "@/lib/profileText";
import {
  dimensionSummaries,
  fetchTeamAnalytics,
  fetchTeamScores,
  type DimensionSummary,
  type TeamAnalytics,
} from "@/lib/team";
import styles from "./overview.module.css";

// Asked on every request, on the server. The browser never calls the backend.
export const dynamic = "force-dynamic";

type SearchParams = Record<string, string | string[] | undefined>;

const SCORES = [0, 1, 2] as const;
const COMPLEXITIES = ["low", "medium", "high", "very_high", "not_judged_yet"] as const;

export default async function Home({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const query = await searchParams;
  const backendUrl = process.env.BACKEND_URL;
  const defaultQuarter = process.env.DEFAULT_QUARTER || undefined;
  if (query.quarter === undefined && defaultQuarter === undefined) {
    return (
      <Overview status={await checkBackend(backendUrl)}>
        <Notice>
          The overview needs a quarter: set <code>DEFAULT_QUARTER</code> in the web environment, for example{" "}
          <code>2026-Q3</code>.
        </Notice>
      </Overview>
    );
  }
  const selection = profileParams(query, defaultQuarter);
  if (!selection.valid) {
    notFound();
  }
  const { quarter, level } = selection;
  const [status, developers, analytics] = await Promise.all([
    checkBackend(backendUrl),
    fetchDevelopers(backendUrl, quarter),
    fetchTeamAnalytics(backendUrl, quarter),
  ]);
  const levelSwitch = <LevelSwitch selected={level} hrefFor={(each) => `/?quarter=${quarter}&level=${each}`} />;
  if (developers.status !== "ok" || analytics.status !== "ok") {
    return (
      <Overview status={status} controls={levelSwitch}>
        <Notice>backend down</Notice>
      </Overview>
    );
  }
  if (developers.data.length === 0) {
    return (
      <Overview status={status} controls={levelSwitch}>
        <Notice>No developers. Run the import jobs and then identity-build and evidence-build.</Notice>
      </Overview>
    );
  }
  const scores = await fetchTeamScores(
    backendUrl,
    developers.data.map((developer) => developer.personId),
    quarter,
    level,
  );
  return (
    <Overview
      status={status}
      controls={levelSwitch}
      figures={<TeamFigures developers={developers.data.length} analytics={analytics.data} />}
      quarter={quarter}
      level={level}
    >
      <div className={styles.grid}>
        <ScorePanel dimensions={dimensionSummaries(scores)} quarter={quarter} level={level} />
        <WorkTypePanel analytics={analytics.data} />
        <ComplexityPanel analytics={analytics.data} />
      </div>
    </Overview>
  );
}

function Overview({
  status,
  controls,
  figures,
  quarter,
  level,
  children,
}: {
  status: BackendStatus;
  controls?: ReactNode;
  figures?: ReactNode;
  quarter?: string;
  level?: Level;
  children: ReactNode;
}) {
  return (
    <main>
      <PageHeader
        title="Engineering Intelligence"
        controls={
          <div className={styles.controls}>
            <span className={styles.status} data-status={status}>
              backend {status}
            </span>
            {controls}
          </div>
        }
        figures={figures}
      >
        What a team leaves in Git and OpenProject, judged by AI. Every score opens down to the pull request behind it.
        {quarter !== undefined && level !== undefined && (
          <span className={styles.links}>
            <Link href={`/developers?quarter=${quarter}&level=${level}`}>See the developers</Link>
            <Link href={`/developers/compare?quarter=${quarter}&level=${level}`}>Compare two developers</Link>
          </span>
        )}
      </PageHeader>
      <PageContent>{children}</PageContent>
    </main>
  );
}

function TeamFigures({ developers, analytics }: { developers: number; analytics: TeamAnalytics }) {
  const { workClassification, workComplexity, hygiene } = analytics;
  return (
    <>
      <HeaderFigure value={developers} label="Developers" />
      <HeaderFigure
        value={`${workClassification.coverage.judged} of ${workClassification.coverage.total}`}
        label="PRs judged"
      />
      <HeaderFigure
        value={`${workComplexity.coverage.judged} of ${workComplexity.coverage.total}`}
        label="Feature work judged"
      />
      <HeaderFigure value={percent(hygiene.share)} label="PRs linked to a work item" />
    </>
  );
}

function ScorePanel({ dimensions, quarter, level }: { dimensions: DimensionSummary[]; quarter: string; level: Level }) {
  if (dimensions.length === 0) {
    return (
      <div className={styles.full}>
        <Notice>
          No scores for {quarter} yet. They are made by the dimension-scoring job:{" "}
          <code>POST /api/jobs/dimension-scoring/runs</code> with the body <code>{`{"quarter":"${quarter}"}`}</code>.
        </Notice>
      </div>
    );
  }
  return (
    <Panel
      title="Developers by score"
      note={`How many developers score 0, 1 and 2 in each dimension, ${quarter} read at ${level}.`}
      className={styles.full}
    >
      <StackedBarChart
        label="Developers by score"
        segments={SCORES.map((score) => ({ key: String(score), label: `Score ${score}`, color: scoreColor(score) }))}
        bars={dimensions.map((dimension) => ({
          key: dimension.key,
          label: dimension.name,
          values: Object.fromEntries(SCORES.map((score) => [String(score), dimension.byScore[score]])),
        }))}
      />
    </Panel>
  );
}

function WorkTypePanel({ analytics }: { analytics: TeamAnalytics }) {
  const { distribution } = analytics.workClassification;
  return (
    <Panel
      title="Work by type"
      note={`${distribution.total} pull requests merged by the team.`}
      className={styles.half}
    >
      <DonutChart
        label="Pull requests by work type"
        centerLabel={`${distribution.total} PRs`}
        slices={distribution.slices.map((slice) => ({
          key: slice.name,
          label: plainName(slice.name),
          value: slice.count.count,
          color: workTypeColor(slice.name),
        }))}
      />
    </Panel>
  );
}

function ComplexityPanel({ analytics }: { analytics: TeamAnalytics }) {
  const { distribution } = analytics.workComplexity;
  return (
    <Panel
      title="Feature work by complexity"
      note={`${distribution.total} Feature work items of the team.`}
      className={styles.half}
    >
      <GroupedBarChart
        label="Feature work by complexity"
        unit="count"
        series={[{ key: "items", label: "Feature work items", color: SINGLE_HUE_COLOR }]}
        categories={COMPLEXITIES.map((name) => ({
          key: name,
          label: plainName(name),
          values: { items: sliceCount(distribution, name).count },
        }))}
      />
    </Panel>
  );
}
