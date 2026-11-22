import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  fetchAnalytics,
  fetchDimensionScores,
  type Analytics,
  type Count,
  type Distribution,
  type DimensionScore,
  type DimensionScores,
  type Level,
  type Tier,
} from "@/lib/profile";
import ProfilePage from "./page";

vi.mock("@/lib/profile", () => ({ fetchAnalytics: vi.fn(), fetchDimensionScores: vi.fn() }));

const PERSON = { personId: 7, displayName: "ada" };

function count(pullRequestNumbers: number[] = [], evidenceItemIds: number[] = []): Count {
  return { count: evidenceItemIds.length || pullRequestNumbers.length, evidenceItemIds, pullRequestNumbers };
}

const EMPTY_DISTRIBUTION: Distribution = { total: 0, slices: [] };

const BREAKDOWN = {
  substantive: count(),
  byCommentType: EMPTY_DISTRIBUTION,
  byTier: EMPTY_DISTRIBUTION,
  notJudgedYet: count(),
};

const TEST_SHARE = { total: 0, tested: count(), untested: count(), share: null, testedByTier: EMPTY_DISTRIBUTION };

function analytics(overrides: Partial<Analytics> = {}): Analytics {
  return {
    person: PERSON,
    quarter: "2026-Q3",
    workClassification: EMPTY_DISTRIBUTION,
    workComplexity: { person: EMPTY_DISTRIBUTION, repository: EMPTY_DISTRIBUTION },
    reviewParticipation: {
      pullRequestsReviewed: count(),
      authorsReviewed: count(),
      comments: BREAKDOWN,
      substantivePerPullRequestReviewed: null,
    },
    reviewFindingsReceived: { pullRequests: count(), comments: BREAKDOWN, substantivePerPullRequest: null },
    testingContribution: {
      featureAndFixPullRequests: TEST_SHARE,
      withoutDigest: count(),
      bugFixPullRequests: TEST_SHARE,
    },
    hygiene: { total: 8, linked: count([], []), notLinked: count(), share: 0.625 },
    ...overrides,
  };
}

function dimension(key: string, name: string, workingTier: Tier | null, score: 0 | 1 | 2): DimensionScore {
  return {
    key,
    name,
    workingTier,
    score,
    gates: [],
    notes: [],
    trail: {
      dimensionKey: key,
      level: "L2",
      evidenceTypesVersion: "v1",
      gatesVersion: null,
      settings: {
        presenceThreshold: { numerator: 1, denominator: 3 },
        featureWorkWeights: { T1: 1, T2: 2, T3: 4 },
        frequentEvidenceTypes: [],
        t3ItemsNeededAtLevel3: {},
        gateLimits: {},
        gateMinimumDevelopers: 3,
        gateMinimumPullRequestsPerDeveloper: 1,
      },
      evidenceTypes: [],
      levelThreeRule: null,
      workingTier,
      scoreBeforeGates: score,
      gates: [],
      countedNotTiered: [],
      notes: [],
      finalScore: score,
    },
  };
}

function scores(level: Level, dimensions: DimensionScore[], scored = true): DimensionScores {
  return {
    person: PERSON,
    quarter: "2026-Q3",
    level,
    scored,
    jobRunId: 3,
    calculatedAt: "2026-11-21T08:00:00Z",
    dimensions,
  };
}

const DESIGN = "Design and architecture";
const ENGINEERING = "Engineering standards";

async function render(
  searchParams: Record<string, string | string[] | undefined> = {},
  personId = "7",
): Promise<string> {
  return renderToStaticMarkup(
    await ProfilePage({ params: Promise.resolve({ personId }), searchParams: Promise.resolve(searchParams) }),
  );
}

function answeringWith(levelScores: DimensionScores, profileAnalytics: Analytics = analytics()): void {
  vi.mocked(fetchAnalytics).mockResolvedValue({ status: "ok", data: profileAnalytics });
  vi.mocked(fetchDimensionScores).mockResolvedValue({ status: "ok", data: levelScores });
}

describe("ProfilePage", () => {
  afterEach(() => {
    vi.mocked(fetchAnalytics).mockReset();
    vi.mocked(fetchDimensionScores).mockReset();
    vi.unstubAllEnvs();
  });

  it("shows both dimension scores at the selected level with the why line", async () => {
    const atLevel: Record<Level, DimensionScores> = {
      L1: scores("L1", [dimension("design", DESIGN, "T2", 2), dimension("engineering", ENGINEERING, "T2", 2)]),
      L2: scores("L2", [dimension("design", DESIGN, "T2", 1), dimension("engineering", ENGINEERING, "T2", 1)]),
      L3: scores("L3", [dimension("design", DESIGN, "T2", 0), dimension("engineering", ENGINEERING, null, 0)]),
    };
    vi.mocked(fetchAnalytics).mockResolvedValue({ status: "ok", data: analytics() });
    vi.mocked(fetchDimensionScores).mockImplementation(async (_url, _id, _quarter, level) => ({
      status: "ok",
      data: atLevel[level],
    }));

    const levelOne = await render({ quarter: "2026-Q3", level: "L1" });
    const levelThree = await render({ quarter: "2026-Q3", level: "L3" });

    expect(levelOne).toContain(DESIGN);
    expect(levelOne).toContain("Works at T2. L1 expects T1: 2.");
    expect(levelOne).toContain("Strength");
    expect(levelThree).toContain("Works at T2. L3 expects T3: 0.");
    expect(levelThree).toContain("No working tier. L3 expects T3: 0.");
    expect(levelThree).toContain("Gap");
    expect(fetchDimensionScores).toHaveBeenLastCalledWith(undefined, 7, "2026-Q3", "L3");
  });

  it("shows the working tier, the next step and no next step at a score of 2", async () => {
    answeringWith(scores("L2", [dimension("design", DESIGN, "T2", 1), dimension("engineering", ENGINEERING, "T3", 2)]));

    const html = await render({ quarter: "2026-Q3" });

    expect(html).toContain("works at T2");
    expect(html).toContain("works at T3");
    expect(html.match(/To score/g)).toHaveLength(1);
  });

  it("shows no working tier for a dimension without one", async () => {
    answeringWith(scores("L2", [dimension("design", DESIGN, null, 0)]));

    expect(await render({ quarter: "2026-Q3" })).toContain("no working tier");
  });

  it("shows the header with the name, quarter, hygiene and level links that keep the quarter", async () => {
    answeringWith(scores("L2", [dimension("design", DESIGN, "T2", 1)]));

    const html = await render({ quarter: "2026-Q3", level: "L2" });

    expect(html).toContain("ada");
    expect(html).toContain("2026-Q3");
    expect(html).toContain("63%");
    expect(html).toContain('href="/developers/7/profile?quarter=2026-Q3&amp;level=L1"');
    expect(html).toContain('href="/developers/7/profile?quarter=2026-Q3&amp;level=L3"');
    expect(html).toMatch(/aria-current="page"[^>]*>L2</);
    expect(html).toContain('href="/developers/7"');
    expect(html).toContain('href="/developers"');
  });

  it("says no PRs for a hygiene share that is null", async () => {
    answeringWith(
      scores("L2", []),
      analytics({ hygiene: { total: 0, linked: count(), notLinked: count(), share: null } }),
    );

    expect(await render({ quarter: "2026-Q3" })).toContain("no PRs");
  });

  it("takes the default quarter from DEFAULT_QUARTER and the level L2", async () => {
    vi.stubEnv("DEFAULT_QUARTER", "2026-Q3");
    answeringWith(scores("L2", []));

    await render();

    expect(fetchAnalytics).toHaveBeenCalledWith(undefined, 7, "2026-Q3");
    expect(fetchDimensionScores).toHaveBeenCalledWith(undefined, 7, "2026-Q3", "L2");
  });

  it("names DEFAULT_QUARTER in a notice when no quarter is given and none is set", async () => {
    vi.stubEnv("DEFAULT_QUARTER", "");

    const html = await render();

    expect(html).toContain("DEFAULT_QUARTER");
    expect(fetchAnalytics).not.toHaveBeenCalled();
  });

  it("says no scores yet and how they are made, and still shows the header", async () => {
    answeringWith(scores("L2", [], false));

    const html = await render({ quarter: "2026-Q3" });

    expect(html).toContain("No scores for 2026-Q3 yet");
    expect(html).toContain("dimension-scoring");
    expect(html).toContain("63%");
  });

  it("warns that the numbers will change when pull requests or items are not judged yet", async () => {
    answeringWith(
      scores("L2", [dimension("design", DESIGN, "T2", 1)]),
      analytics({
        workClassification: {
          total: 3,
          slices: [{ name: "not_judged_yet", count: count([11, 12]), share: 0.5 }],
        },
        reviewParticipation: {
          pullRequestsReviewed: count(),
          authorsReviewed: count(),
          comments: { ...BREAKDOWN, notJudgedYet: count([], [5]) },
          substantivePerPullRequestReviewed: null,
        },
      }),
    );

    expect(await render({ quarter: "2026-Q3" })).toContain(
      "2 PRs and 1 items are not judged yet; the numbers will change",
    );
  });

  it("shows no notice when everything is judged", async () => {
    answeringWith(scores("L2", [dimension("design", DESIGN, "T2", 1)]));

    expect(await render({ quarter: "2026-Q3" })).not.toContain("not judged yet");
  });

  it("says the backend is down when either call fails", async () => {
    vi.mocked(fetchAnalytics).mockResolvedValue({ status: "down" });
    vi.mocked(fetchDimensionScores).mockResolvedValue({ status: "ok", data: scores("L2", []) });

    expect(await render({ quarter: "2026-Q3" })).toContain("backend down");
  });

  it("is not found for an unknown developer", async () => {
    vi.mocked(fetchAnalytics).mockResolvedValue({ status: "not-found" });
    vi.mocked(fetchDimensionScores).mockResolvedValue({ status: "not-found" });

    await expect(render({ quarter: "2026-Q3" }, "99")).rejects.toThrow("NEXT_HTTP_ERROR_FALLBACK;404");
  });

  it.each([{ quarter: "Q3" }, { quarter: "2026-Q3", level: "L9" }])(
    "is not found for the bad URL parameters %j, without asking the backend",
    async (searchParams) => {
      await expect(render(searchParams)).rejects.toThrow("NEXT_HTTP_ERROR_FALLBACK;404");
      expect(fetchAnalytics).not.toHaveBeenCalled();
    },
  );

  it("is not found for a person id that is no number", async () => {
    await expect(render({ quarter: "2026-Q3" }, "ada")).rejects.toThrow("NEXT_HTTP_ERROR_FALLBACK;404");
    expect(fetchAnalytics).not.toHaveBeenCalled();
  });
});
