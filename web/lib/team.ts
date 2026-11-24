import { type BackendResult, readBackend } from "./backend";
import type { TypeCounts } from "./evidence";
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
};

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

/** The number of developers at each score, per dimension, in the order the dimensions first appear. */
export function dimensionSummaries(scores: TeamScores): DimensionSummary[] {
  const summaries = new Map<string, DimensionSummary>();
  for (const dimensions of scores.values()) {
    for (const dimension of dimensions) {
      const summary = summaries.get(dimension.key) ?? {
        key: dimension.key,
        name: dimension.name,
        byScore: [0, 0, 0],
      };
      summary.byScore[dimension.score] += 1;
      summaries.set(dimension.key, summary);
    }
  }
  return [...summaries.values()];
}
