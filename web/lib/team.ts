import { type BackendResult, readBackend } from "./backend";
import type { Developer, TypeCounts } from "./evidence";
import { type Count, type Coverage, fetchDimensionScores, type Distribution, type Level, type Tier } from "./profile";

/** One dimension of one developer as the overview and the roster show it. */
export type DeveloperScore = { key: string; name: string; score: 0 | 1 | 2; workingTier: Tier | null };

/** The dimension scores per person id. A developer who has none (no scoring run, or a failed call) is not in it. */
export type TeamScores = ReadonlyMap<number, DeveloperScore[]>;

export type DimensionSummary = {
  key: string;
  name: string;
  /** The number of developers at score 0, 1 and 2. */
  byScore: [number, number, number];
  byWorkingTier: Record<Tier | "none", number>;
};

export type TeamSummary = {
  developers: number;
  evidenceItems: number;
  scoredDevelopers: number;
  byType: TypeCounts;
  dimensions: DimensionSummary[];
  mostEvidence: Developer[];
};

const DEFAULT_MOST_EVIDENCE = 8;

/** The analytics of every developer together in one quarter (F15). */
export type TeamAnalytics = {
  quarter: string;
  workClassification: { distribution: Distribution; coverage: Coverage };
  workComplexity: { distribution: Distribution; coverage: Coverage };
  hygiene: { total: number; linked: Count; notLinked: Count; share: number | null };
};

/** The team analytics of a quarter. Never throws. */
export async function fetchTeamAnalytics(
  baseUrl: string | undefined,
  quarter: string,
  fetchFn: typeof fetch = fetch,
): Promise<BackendResult<TeamAnalytics>> {
  const query = new URLSearchParams({ quarter });
  return readBackend<TeamAnalytics>(baseUrl, `/api/team/analytics?${query}`, fetchFn);
}

/**
 * The scores of every given developer at one level, one call each, in parallel (decision 266). Never throws: a
 * call that fails leaves that developer without scores, and `fetchDimensionScores` logs why.
 */
export async function fetchTeamScores(
  baseUrl: string | undefined,
  personIds: number[],
  quarter: string,
  level: Level,
  fetchScores: typeof fetchDimensionScores = fetchDimensionScores,
): Promise<TeamScores> {
  const answers = await Promise.all(
    personIds.map(async (personId) => ({ personId, answer: await fetchScores(baseUrl, personId, quarter, level) })),
  );
  const scores = new Map<number, DeveloperScore[]>();
  for (const { personId, answer } of answers) {
    if (answer.status === "ok" && answer.data.scored) {
      scores.set(
        personId,
        answer.data.dimensions.map(({ key, name, score, workingTier }) => ({ key, name, score, workingTier })),
      );
    }
  }
  return scores;
}

/** The number of evidence items over all types. */
export function totalOf(counts: TypeCounts): number {
  return Object.values(counts).reduce((sum, count) => sum + count, 0);
}

/** The numbers of the whole team: evidence per type, developers per score and working tier, and who has the most evidence. */
export function teamSummary(
  developers: Developer[],
  scores: TeamScores,
  mostEvidenceCount: number = DEFAULT_MOST_EVIDENCE,
): TeamSummary {
  return {
    developers: developers.length,
    evidenceItems: developers.reduce((sum, developer) => sum + totalOf(developer.counts), 0),
    scoredDevelopers: scores.size,
    byType: sumPerType(developers),
    dimensions: summariseDimensions(scores),
    mostEvidence: developers
      .filter((developer) => totalOf(developer.counts) > 0)
      .sort((first, second) => totalOf(second.counts) - totalOf(first.counts))
      .slice(0, mostEvidenceCount),
  };
}

function sumPerType(developers: Developer[]): TypeCounts {
  const sums: TypeCounts = {
    featureWork: 0,
    bugFix: 0,
    reviewCommentWritten: 0,
    testChange: 0,
    refactorPerformance: 0,
    security: 0,
    infraRelease: 0,
    documentation: 0,
  };
  for (const developer of developers) {
    for (const type of Object.keys(sums) as (keyof TypeCounts)[]) {
      sums[type] += developer.counts[type];
    }
  }
  return sums;
}

function summariseDimensions(scores: TeamScores): DimensionSummary[] {
  const summaries = new Map<string, DimensionSummary>();
  for (const dimensions of scores.values()) {
    for (const dimension of dimensions) {
      const summary = summaries.get(dimension.key) ?? {
        key: dimension.key,
        name: dimension.name,
        byScore: [0, 0, 0],
        byWorkingTier: { T1: 0, T2: 0, T3: 0, none: 0 },
      };
      summary.byScore[dimension.score] += 1;
      summary.byWorkingTier[dimension.workingTier ?? "none"] += 1;
      summaries.set(dimension.key, summary);
    }
  }
  return [...summaries.values()];
}
