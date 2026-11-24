import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchDevelopers, type Developer, type TypeCounts } from "@/lib/evidence";
import { checkBackend } from "@/lib/health";
import type { Count, Distribution } from "@/lib/profile";
import { fetchTeamAnalytics, fetchTeamScores, type TeamAnalytics, type TeamScores } from "@/lib/team";
import Home from "./page";

vi.mock("next/navigation", () => ({
  notFound: () => {
    throw new Error("not found");
  },
}));
vi.mock("@/lib/health", () => ({ checkBackend: vi.fn() }));
vi.mock("@/lib/evidence", () => ({ fetchDevelopers: vi.fn() }));
vi.mock("@/lib/team", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/team")>()),
  fetchTeamScores: vi.fn(),
  fetchTeamAnalytics: vi.fn(),
}));

const NO_ITEMS: TypeCounts = {
  featureWork: 0,
  bugFix: 0,
  reviewCommentWritten: 0,
  testChange: 0,
  refactorPerformance: 0,
  security: 0,
  infraRelease: 0,
  documentation: 0,
};

const DEVELOPERS: Developer[] = [
  { personId: 7, displayName: "ada", counts: NO_ITEMS },
  { personId: 8, displayName: "grace", counts: NO_ITEMS },
  { personId: 9, displayName: "linus", counts: NO_ITEMS },
];

const SCORES: TeamScores = new Map([
  [
    7,
    [
      { key: "coding", name: "Coding and code quality", score: 2, workingTier: "T3" },
      { key: "standards", name: "Engineering standards", score: 0, workingTier: null },
    ],
  ],
  [8, [{ key: "coding", name: "Coding and code quality", score: 1, workingTier: "T2" }]],
]);

function count(value: number): Count {
  return { count: value, evidenceItemIds: [], pullRequestNumbers: [] };
}

function distribution(parts: Record<string, number>): Distribution {
  const total = Object.values(parts).reduce((sum, value) => sum + value, 0);
  return {
    total,
    slices: Object.entries(parts).map(([name, value]) => ({ name, count: count(value), share: value / total })),
  };
}

const ANALYTICS: TeamAnalytics = {
  quarter: "2026-Q3",
  workClassification: { distribution: distribution({ feature: 6, fix: 2 }), coverage: { judged: 8, total: 10 } },
  workComplexity: {
    distribution: distribution({ low: 1, medium: 3, high: 2, very_high: 0, not_judged_yet: 1 }),
    coverage: { judged: 6, total: 7 },
  },
  hygiene: { total: 10, linked: count(7), notLinked: count(3), share: 0.7 },
};

async function render(searchParams: Record<string, string> = {}): Promise<string> {
  return renderToStaticMarkup(await Home({ searchParams: Promise.resolve(searchParams) }));
}

describe("Home", () => {
  beforeEach(() => {
    vi.stubEnv("DEFAULT_QUARTER", "2026-Q3");
    vi.mocked(checkBackend).mockResolvedValue("up");
    vi.mocked(fetchDevelopers).mockResolvedValue({ status: "ok", data: DEVELOPERS });
    vi.mocked(fetchTeamScores).mockResolvedValue(SCORES);
    vi.mocked(fetchTeamAnalytics).mockResolvedValue({ status: "ok", data: ANALYTICS });
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.mocked(checkBackend).mockReset();
    vi.mocked(fetchDevelopers).mockReset();
    vi.mocked(fetchTeamScores).mockReset();
    vi.mocked(fetchTeamAnalytics).mockReset();
  });

  it("shows that the backend is up", async () => {
    expect(await render()).toContain("backend up");
  });

  it("shows that the backend is down, and no chart", async () => {
    vi.mocked(checkBackend).mockResolvedValue("down");
    vi.mocked(fetchDevelopers).mockResolvedValue({ status: "down" });

    const html = await render();

    expect(html).toContain("backend down");
    expect(html).not.toContain("Developers by score");
  });

  it("links to the developer list and the comparison", async () => {
    const html = await render({ level: "L3" });

    expect(html).toContain('href="/developers?quarter=2026-Q3&amp;level=L3"');
    expect(html).toContain('href="/developers/compare?quarter=2026-Q3&amp;level=L3"');
  });

  it("asks the backend named by BACKEND_URL", async () => {
    vi.stubEnv("BACKEND_URL", "http://backend.test:9000");

    await render({ quarter: "2026-Q2" });

    expect(checkBackend).toHaveBeenCalledWith("http://backend.test:9000");
    expect(fetchDevelopers).toHaveBeenCalledWith("http://backend.test:9000", "2026-Q2");
    expect(fetchTeamAnalytics).toHaveBeenCalledWith("http://backend.test:9000", "2026-Q2");
  });

  it("shows how many developers score 0, 1 and 2 per dimension", async () => {
    const html = await render({ level: "L1" });

    expect(fetchTeamScores).toHaveBeenCalledWith(undefined, [7, 8, 9], "2026-Q3", "L1");
    expect(html).toContain("Coding and code quality: Score 0 0, Score 1 1, Score 2 1, total 2");
    expect(html).toContain("Engineering standards: Score 0 1, Score 1 0, Score 2 0, total 1");
  });

  it("shows the work of the team by type and its Feature work by complexity", async () => {
    const html = await render();

    expect(html).toContain("Feature: 6 (75%)");
    expect(html).toContain("Fix: 2 (25%)");
    expect(html).toContain("Medium: Feature work items 3");
    expect(html).toContain("Not judged yet: Feature work items 1");
  });

  it("shows the developers, the judged coverage and the hygiene number", async () => {
    const html = await render();

    expect(html).toMatch(/>3<\/span><span[^>]*>Developers</);
    expect(html).toMatch(/>8 of 10<\/span><span[^>]*>PRs judged</);
    expect(html).toMatch(/>6 of 7<\/span><span[^>]*>Feature work judged</);
    expect(html).toMatch(/>70%<\/span><span[^>]*>PRs linked to a work item</);
  });

  it("does not rank developers by evidence", async () => {
    expect(await render()).not.toContain("Most evidence");
  });

  it("links each level and marks the chosen one", async () => {
    const html = await render({ quarter: "2026-Q2" });

    expect(html).toContain('href="/?quarter=2026-Q2&amp;level=L1"');
    expect(html).toMatch(/aria-current="page"[^>]*>L2</);
  });

  it("says that the overview needs a quarter when none is given or set, and asks for nothing", async () => {
    vi.stubEnv("DEFAULT_QUARTER", "");

    const html = await render();

    expect(html).toContain("DEFAULT_QUARTER");
    expect(fetchDevelopers).not.toHaveBeenCalled();
    expect(fetchTeamScores).not.toHaveBeenCalled();
    expect(fetchTeamAnalytics).not.toHaveBeenCalled();
  });

  it("says how scores are made when the quarter has none yet", async () => {
    vi.mocked(fetchTeamScores).mockResolvedValue(new Map());

    expect(await render()).toContain("dimension-scoring");
  });

  it("says there are no developers when none is stored", async () => {
    vi.mocked(fetchDevelopers).mockResolvedValue({ status: "ok", data: [] });

    const html = await render();

    expect(html).toContain("No developers");
    expect(html).not.toContain("Developers by score");
  });

  it("answers not found for an unknown level", async () => {
    await expect(render({ level: "L9" })).rejects.toThrow("not found");
  });
});
