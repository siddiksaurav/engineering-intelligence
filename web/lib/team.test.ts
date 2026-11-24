import { describe, expect, it, vi } from "vitest";
import type { TypeCounts } from "./evidence";
import type { DimensionScores } from "./profile";
import { fetchTeamAnalytics, dimensionSummaries, fetchTeamScores, totalOf, type TeamScores } from "./team";

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

describe("dimensionSummaries", () => {
  it("counts the developers at each score per dimension, in the order the dimensions first appear", () => {
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

    expect(dimensionSummaries(scores)).toEqual([
      { key: "coding", name: "Coding", byScore: [0, 0, 2] },
      { key: "standards", name: "Standards", byScore: [1, 0, 0] },
    ]);
  });

  it("is empty when nobody is scored", () => {
    expect(dimensionSummaries(new Map())).toEqual([]);
  });
});

describe("totalOf", () => {
  it("adds the counts of all types", () => {
    expect(totalOf({ ...NO_COUNTS, featureWork: 2, security: 3 })).toBe(5);
  });
});

describe("fetchTeamAnalytics", () => {
  it("asks the team analytics of the quarter", async () => {
    const body = { quarter: "2026-Q3" };
    const fetchFn = vi.fn(async () => new Response(JSON.stringify(body))) as unknown as typeof fetch;

    const result = await fetchTeamAnalytics("http://localhost:8080", "2026-Q3", fetchFn);

    expect(result).toEqual({ status: "ok", data: body });
    expect(vi.mocked(fetchFn).mock.calls[0][0]).toBe("http://localhost:8080/api/team/analytics?quarter=2026-Q3");
  });

  it("says the backend is down when it answers an error", async () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    const fetchFn = vi.fn(async () => new Response("{}", { status: 400 })) as unknown as typeof fetch;

    expect(await fetchTeamAnalytics("http://localhost:8080", "2026-Q3", fetchFn)).toEqual({ status: "down" });
    warn.mockRestore();
  });
});
