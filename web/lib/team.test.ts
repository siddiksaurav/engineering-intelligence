import { describe, expect, it, vi } from "vitest";
import type { Developer, TypeCounts } from "./evidence";
import type { DimensionScores } from "./profile";
import { fetchTeamScores, teamSummary, totalOf, type TeamScores } from "./team";

const NO_COUNTS: TypeCounts = {
  featureWork: 0,
  bugFix: 0,
  reviewCommentWritten: 0,
  testChange: 0,
  refactorPerformance: 0,
  security: 0,
  infraRelease: 0,
  documentation: 0,
};

function developer(personId: number, displayName: string, counts: Partial<TypeCounts> = {}): Developer {
  return { personId, displayName, counts: { ...NO_COUNTS, ...counts } };
}

function scoresOf(personId: number, dimensions: [string, 0 | 1 | 2, "T1" | "T2" | "T3" | null][]): DimensionScores {
  return {
    person: { personId, displayName: `person ${personId}` },
    quarter: "2026-Q3",
    level: "L2",
    scored: dimensions.length > 0,
    jobRunId: 1,
    calculatedAt: null,
    dimensions: dimensions.map(([key, score, workingTier]) => ({
      key,
      name: key === "coding" ? "Coding and code quality" : "Engineering standards and system health",
      score,
      workingTier,
    })) as DimensionScores["dimensions"],
  };
}

describe("fetchTeamScores", () => {
  it("asks the scores of every developer at the level and keeps key, name, score and working tier", async () => {
    const fetchScores = vi.fn(async (_baseUrl: string | undefined, personId: number) => ({
      status: "ok" as const,
      data: scoresOf(personId, [["coding", 2, "T3"]]),
    }));

    const scores = await fetchTeamScores("http://backend.test", [1, 2], "2026-Q3", "L2", fetchScores);

    expect(fetchScores).toHaveBeenCalledWith("http://backend.test", 1, "2026-Q3", "L2");
    expect(fetchScores).toHaveBeenCalledWith("http://backend.test", 2, "2026-Q3", "L2");
    expect(scores.get(2)).toEqual([{ key: "coding", name: "Coding and code quality", score: 2, workingTier: "T3" }]);
  });

  it("leaves a developer out when the call fails or there is no scoring run yet", async () => {
    const fetchScores = vi
      .fn()
      .mockResolvedValueOnce({ status: "down" })
      .mockResolvedValueOnce({ status: "ok", data: scoresOf(2, []) })
      .mockResolvedValueOnce({ status: "ok", data: scoresOf(3, [["coding", 1, null]]) });

    const scores = await fetchTeamScores("http://backend.test", [1, 2, 3], "2026-Q3", "L2", fetchScores);

    expect([...scores.keys()]).toEqual([3]);
  });
});

describe("teamSummary", () => {
  const developers = [
    developer(1, "ada", { featureWork: 2, bugFix: 1 }),
    developer(2, "grace", { reviewCommentWritten: 5 }),
    developer(3, "linus"),
  ];

  it("adds up the evidence items of the team per type and in total", () => {
    const summary = teamSummary(developers, new Map());

    expect(summary.developers).toBe(3);
    expect(summary.evidenceItems).toBe(8);
    expect(summary.byType.featureWork).toBe(2);
    expect(summary.byType.reviewCommentWritten).toBe(5);
    expect(summary.byType.security).toBe(0);
  });

  it("counts the developers at each score and each working tier per dimension", () => {
    const scores: TeamScores = new Map([
      [
        1,
        [
          { key: "coding", name: "Coding", score: 2, workingTier: "T3" },
          { key: "standards", name: "Standards", score: 0, workingTier: null },
        ],
      ],
      [2, [{ key: "coding", name: "Coding", score: 2, workingTier: "T2" }]],
    ]);

    const summary = teamSummary(developers, scores);

    expect(summary.scoredDevelopers).toBe(2);
    expect(summary.dimensions.map((dimension) => dimension.key)).toEqual(["coding", "standards"]);
    expect(summary.dimensions[0].byScore).toEqual([0, 0, 2]);
    expect(summary.dimensions[0].byWorkingTier).toEqual({ T1: 0, T2: 1, T3: 1, none: 0 });
    expect(summary.dimensions[1].byScore).toEqual([1, 0, 0]);
    expect(summary.dimensions[1].byWorkingTier).toEqual({ T1: 0, T2: 0, T3: 0, none: 1 });
  });

  it("lists the developers with the most evidence first, as many as asked, and none without evidence", () => {
    expect(teamSummary(developers, new Map(), 1).mostEvidence.map((entry) => entry.displayName)).toEqual(["grace"]);
    expect(teamSummary(developers, new Map(), 8).mostEvidence.map((entry) => entry.displayName)).toEqual([
      "grace",
      "ada",
    ]);
  });
});

describe("totalOf", () => {
  it("adds the counts of all types", () => {
    expect(totalOf({ ...NO_COUNTS, featureWork: 2, security: 3 })).toBe(5);
  });
});
