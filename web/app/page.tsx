import Link from "next/link";
import type { ReactNode } from "react";
import { Avatar } from "@/components/Avatar";
import { MeterBar } from "@/components/charts/MeterBar";
import { SegmentedBar, type Segment } from "@/components/charts/SegmentedBar";
import { Swatch, TypeBar } from "@/components/EvidenceTypes";
import { HeaderFigure, PageContent, PageHeader } from "@/components/Page";
import { Notice, Panel } from "@/components/Panel";
import { fetchDevelopers, type Developer } from "@/lib/evidence";
import { EVIDENCE_SECTIONS } from "@/lib/evidenceSections";
import { checkBackend, type BackendStatus } from "@/lib/health";
import { NOT_JUDGED_COLOR, orderedColor, scoreColor, typeColor } from "@/lib/palette";
import { percent } from "@/lib/profileText";
import { fetchTeamScores, teamSummary, totalOf, type DimensionSummary, type TeamSummary } from "@/lib/team";
import styles from "./overview.module.css";

// Asked on every request, on the server. The browser never calls the backend.
export const dynamic = "force-dynamic";

/** The level the team numbers are read at: the level a profile opens with (decision 247). */
const TEAM_LEVEL = "L2";

const SCORES = [0, 1, 2] as const;
const TIERS = ["T1", "T2", "T3"] as const;

export default async function Home() {
  const backendUrl = process.env.BACKEND_URL;
  const quarter = process.env.DEFAULT_QUARTER || undefined;
  const [status, developers] = await Promise.all([checkBackend(backendUrl), fetchDevelopers(backendUrl)]);
  if (developers.status !== "ok") {
    return (
      <Overview status={status}>
        <Notice>backend down</Notice>
      </Overview>
    );
  }
  if (developers.data.length === 0) {
    return (
      <Overview status={status}>
        <Notice>No developers. Run the import jobs and then identity-build and evidence-build.</Notice>
      </Overview>
    );
  }
  const scores =
    quarter === undefined
      ? null
      : await fetchTeamScores(
          backendUrl,
          developers.data.map((developer) => developer.personId),
          quarter,
          TEAM_LEVEL,
        );
  const summary = teamSummary(developers.data, scores ?? new Map());
  return (
    <Overview status={status} figures={<TeamFigures summary={summary} scoresRead={scores !== null} />}>
      <div className={styles.grid}>
        <EvidenceByType summary={summary} />
        <MostEvidence developers={summary.mostEvidence} />
        <TeamScoresPanels summary={summary} quarter={quarter} />
      </div>
    </Overview>
  );
}

function Overview({ status, figures, children }: { status: BackendStatus; figures?: ReactNode; children: ReactNode }) {
  return (
    <main>
      <PageHeader
        title="Engineering Intelligence"
        controls={
          <span className={styles.status} data-status={status}>
            backend {status}
          </span>
        }
        figures={figures}
      >
        What a team leaves in Git and OpenProject, judged by AI. Every score opens down to the pull request behind it.{" "}
        <Link href="/developers">See the developers</Link>
      </PageHeader>
      <PageContent>{children}</PageContent>
    </main>
  );
}

function TeamFigures({ summary, scoresRead }: { summary: TeamSummary; scoresRead: boolean }) {
  return (
    <>
      <HeaderFigure value={summary.developers} label="Developers" />
      <HeaderFigure value={summary.evidenceItems} label="Evidence items" />
      <HeaderFigure value={summary.byType.reviewCommentWritten} label="Review comments written" />
      {scoresRead && (
        <HeaderFigure value={`${summary.scoredDevelopers} of ${summary.developers}`} label="Developers scored" />
      )}
    </>
  );
}

function EvidenceByType({ summary }: { summary: TeamSummary }) {
  const ranked = EVIDENCE_SECTIONS.map((section) => ({ ...section, count: summary.byType[section.countKey] })).sort(
    (first, second) => second.count - first.count,
  );
  return (
    <Panel title="Evidence by type" note="Current evidence items of the whole team." className={styles.wide}>
      <ul className={styles.ranked}>
        {ranked.map((section) => {
          const share = summary.evidenceItems === 0 ? null : section.count / summary.evidenceItems;
          return (
            <li key={section.type} className={styles.rankedRow}>
              <span className={styles.rankedName}>
                <Swatch color={typeColor(section.type)} />
                {section.title}
              </span>
              {share !== null && (
                <MeterBar label={section.title} count={section.count} share={share} color={typeColor(section.type)} />
              )}
              <span className={styles.count}>{section.count}</span>
              <span className={styles.share}>{percent(share)}</span>
            </li>
          );
        })}
      </ul>
    </Panel>
  );
}

function MostEvidence({ developers }: { developers: Developer[] }) {
  const most = developers.length === 0 ? 0 : totalOf(developers[0].counts);
  return (
    <Panel title="Most evidence" note="A segment per evidence type." className={styles.narrow}>
      <ul className={styles.people}>
        {developers.map((developer) => {
          const total = totalOf(developer.counts);
          return (
            <li key={developer.personId} className={styles.person}>
              <Avatar name={developer.displayName} />
              <div className={styles.personBody}>
                <div className={styles.personLine}>
                  <Link href={`/developers/${developer.personId}/profile`}>{developer.displayName}</Link>
                  <span className={styles.count}>{total}</span>
                </div>
                <div style={{ width: `${(total / most) * 100}%` }}>
                  <TypeBar label={`Evidence of ${developer.displayName}`} counts={developer.counts} size="thin" />
                </div>
              </div>
            </li>
          );
        })}
      </ul>
      <p className={styles.more}>
        <Link href="/developers">All developers</Link>
      </p>
    </Panel>
  );
}

function TeamScoresPanels({ summary, quarter }: { summary: TeamSummary; quarter: string | undefined }) {
  if (quarter === undefined) {
    return (
      <div className={styles.full}>
        <Notice>
          Scores need a quarter: set <code>DEFAULT_QUARTER</code> in the web environment, for example{" "}
          <code>2026-Q3</code>.
        </Notice>
      </div>
    );
  }
  if (summary.dimensions.length === 0) {
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
    <>
      {summary.dimensions.map((dimension) => (
        <DimensionPanel key={dimension.key} dimension={dimension} quarter={quarter} />
      ))}
    </>
  );
}

function DimensionPanel({ dimension, quarter }: { dimension: DimensionSummary; quarter: string }) {
  const scores: Segment[] = SCORES.map((score) => ({
    key: String(score),
    label: `Score ${score}`,
    value: dimension.byScore[score],
    color: scoreColor(score),
  }));
  const tiers: Segment[] = [
    ...TIERS.map((tier) => ({
      key: tier,
      label: tier,
      value: dimension.byWorkingTier[tier],
      color: orderedColor(tier) ?? NOT_JUDGED_COLOR,
    })),
    { key: "none", label: "No working tier", value: dimension.byWorkingTier.none, color: NOT_JUDGED_COLOR },
  ];
  return (
    <Panel title={dimension.name} note={`Developers in ${quarter}, read at ${TEAM_LEVEL}.`} className={styles.half}>
      <Split title="Score, of 2" label="Scores" segments={scores} />
      <Split title="Working tier" label="Working tiers" segments={tiers} />
    </Panel>
  );
}

/** One segmented bar with the list of its parts under it: a swatch, the name and the number of developers. */
function Split({ title, label, segments }: { title: string; label: string; segments: Segment[] }) {
  return (
    <div className={styles.split}>
      <h3 className={styles.splitTitle}>{title}</h3>
      <SegmentedBar label={label} segments={segments} />
      <ul className={styles.splitParts}>
        {segments.map((segment) => (
          <li key={segment.key}>
            <Swatch color={segment.color} />
            {segment.label}
            <span className={styles.count}>{segment.value}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
