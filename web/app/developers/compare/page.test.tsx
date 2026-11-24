import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchDevelopers, type Developer, type TypeCounts } from "@/lib/evidence";
import {
  fetchAnalytics,
  fetchDimensionScores,
  type Analytics,
  type Count,
  type Distribution,
  type DimensionScores,
  type Tier,
} from "@/lib/profile";
import ComparePage from "./page";

vi.mock("next/navigation", () => ({
  notFound: () => {
    throw new Error("not found");
  },
}));
vi.mock("@/lib/evidence", () => ({ fetchDevelopers: vi.fn() }));
vi.mock("@/lib/profile", () => ({ fetchAnalytics: vi.fn(), fetchDimensionScores: vi.fn() }));

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
  { personId: 8, displayName: "grace", counts: NO_ITEMS },
  { personId: 7, displayName: "ada", counts: NO_ITEMS },
  { personId: 9, displayName: "linus", counts: NO_ITEMS },
];

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

/** Only the parts of the analytics and the scores that the compare page reads; the rest is not needed. */
function analyticsOf(
  personId: number,
  displayName: string,
  parts: { kinds: Record<string, number>; complexities: Record<string, number>; atT2: number; atT3: number },
): Analytics {
  const level = {
    weightedTotal: 1,
    weightedAtT2OrAbove: 0,
    shareAtT2OrAbove: parts.atT2,
    weightedAtT3: 0,
    shareAtT3: parts.atT3,
  };
  return {
    person: { personId, displayName },
    quarter: "2026-Q3",
    workClassification: { distribution: distribution(parts.kinds), coverage: { judged: 1, total: 1 } },
    workComplexity: {
      person: distribution(parts.complexities),
      restOfRepository: distribution({}),
      coverage: { judged: 1, total: 1 },
    },
    workLevel: { person: level, restOfRepository: level, line: 1 / 3, coverage: { judged: 1, total: 1 } },
  } as Analytics;
}

function scoresOf(personId: number, dimensions: [string, 0 | 1 | 2, Tier | null][]): DimensionScores {
  return {
    person: { personId, displayName: `person ${personId}` },
    quarter: "2026-Q3",
    level: "L2",
    scored: true,
    jobRunId: 1,
    calculatedAt: null,
    dimensions: dimensions.map(([key, score, workingTier]) => ({ key, name: `Dimension ${key}`, score, workingTier })),
  } as DimensionScores;
}

const ADA = analyticsOf(7, "ada", {
  kinds: { feature: 3, fix: 1 },
  complexities: { low: 1, medium: 2 },
  atT2: 0.75,
  atT3: 0.25,
});
const GRACE = analyticsOf(8, "grace", {
  kinds: { fix: 4 },
  complexities: { high: 3 },
  atT2: 0.5,
  atT3: 0.5,
});

async function render(searchParams: Record<string, string> = {}): Promise<string> {
  return renderToStaticMarkup(await ComparePage({ searchParams: Promise.resolve(searchParams) }));
}

describe("ComparePage", () => {
  beforeEach(() => {
    vi.stubEnv("DEFAULT_QUARTER", "2026-Q3");
    vi.mocked(fetchDevelopers).mockResolvedValue({ status: "ok", data: DEVELOPERS });
    vi.mocked(fetchAnalytics).mockImplementation(async (_url, personId) => ({
      status: "ok",
      data: personId === 7 ? ADA : GRACE,
    }));
    vi.mocked(fetchDimensionScores).mockImplementation(async (_url, personId) => ({
      status: "ok",
      data:
        personId === 7
          ? scoresOf(7, [
              ["coding", 2, "T3"],
              ["standards", 0, null],
            ])
          : scoresOf(8, [
              ["coding", 1, "T2"],
              ["standards", 1, "T1"],
            ]),
    }));
  });

  afterEach(() => {
    vi.mocked(fetchDevelopers).mockReset();
    vi.mocked(fetchAnalytics).mockReset();
    vi.mocked(fetchDimensionScores).mockReset();
    vi.unstubAllEnvs();
  });

  it("shows both developers' scores side by side", async () => {
    const html = await render({ a: "7", b: "8", level: "L3" });

    expect(fetchDimensionScores).toHaveBeenCalledWith(undefined, 7, "2026-Q3", "L3");
    expect(fetchDimensionScores).toHaveBeenCalledWith(undefined, 8, "2026-Q3", "L3");
    const panel = html.slice(html.indexOf(">Scores<"));
    const grace = panel.indexOf(">grace<");
    const ada = panel.slice(0, grace);
    expect(panel.indexOf(">ada<")).toBeLessThan(grace);
    expect(ada).toContain("Dimension coding");
    expect(ada).toMatch(/data-score="2"[^>]*>2</);
    expect(ada).toContain('data-tier="T3"');
    expect(ada).toContain("no working tier");
    expect(panel.slice(grace)).toMatch(/data-score="1"[^>]*>1</);
    expect(panel.slice(grace)).toContain('data-tier="T2"');
  });

  it("sets the work level of both against the line in one chart", async () => {
    const html = await render({ a: "7", b: "8" });

    expect(html).toContain("Medium or harder: ada 75%, grace 50%");
    expect(html).toContain("High: ada 25%, grace 50%");
  });

  it("shows the work type of each developer and the complexity counts of both", async () => {
    const html = await render({ a: "7", b: "8" });

    expect(html).toContain("Feature: 3 (75%)");
    expect(html).toContain("Fix: 4 (100%)");
    expect(html).toContain("Medium: ada 2, grace 0");
    expect(html).toContain("High: ada 0, grace 3");
  });

  it("lists the developers by name as links that choose each side, keeping the other and the level", async () => {
    const html = await render({ a: "7", b: "8", level: "L1" });

    expect(html.indexOf(">ada<")).toBeLessThan(html.indexOf(">grace<"));
    expect(html).toContain('href="/developers/compare?a=9&amp;b=8&amp;quarter=2026-Q3&amp;level=L1"');
    expect(html).toContain('href="/developers/compare?a=7&amp;b=9&amp;quarter=2026-Q3&amp;level=L1"');
  });

  it("links each level, keeping both developers and the quarter", async () => {
    const html = await render({ a: "7", b: "8", quarter: "2026-Q2" });

    expect(html).toContain('href="/developers/compare?a=7&amp;b=8&amp;quarter=2026-Q2&amp;level=L3"');
  });

  it("asks to choose two developers when one or none is chosen, and reads nothing for the missing one", async () => {
    const html = await render({ a: "7" });

    expect(html).toContain("Choose two developers");
    expect(fetchAnalytics).not.toHaveBeenCalled();
  });

  it("says so when both sides are the same developer", async () => {
    expect(await render({ a: "7", b: "7" })).toContain("Choose two different developers");
  });

  it("says a developer is not scored without a scoring run", async () => {
    vi.mocked(fetchDimensionScores).mockImplementation(async (_url, personId) => ({
      status: "ok",
      data: { ...scoresOf(personId, []), scored: false },
    }));

    expect(await render({ a: "7", b: "8" })).toContain("not scored");
  });

  it("answers not found for an id that is not a number, an unknown level or a developer the backend does not know", async () => {
    await expect(render({ a: "x", b: "8" })).rejects.toThrow("not found");
    await expect(render({ a: "7", b: "8", level: "L9" })).rejects.toThrow("not found");
    vi.mocked(fetchAnalytics).mockResolvedValue({ status: "not-found" });
    await expect(render({ a: "7", b: "8" })).rejects.toThrow("not found");
  });

  it("says the backend is down when it cannot be read", async () => {
    vi.mocked(fetchDevelopers).mockResolvedValue({ status: "down" });

    expect(await render({ a: "7", b: "8" })).toContain("backend down");
  });

  it("asks for nothing and says why when no quarter is given or set", async () => {
    vi.stubEnv("DEFAULT_QUARTER", "");

    const html = await render({ a: "7", b: "8" });

    expect(html).toContain("DEFAULT_QUARTER");
    expect(fetchDevelopers).not.toHaveBeenCalled();
  });
});
