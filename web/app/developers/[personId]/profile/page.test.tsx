import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  fetchAnalytics,
  fetchDimensionScores,
  type Analytics,
  type Count,
  type Coverage,
  type WeightedWorkLevel,
  type Distribution,
  type EvidenceTypeTrail,
  type GateResult,
  type TrailItem,
  type DimensionScore,
  type DimensionScores,
  type Level,
  type Tier,
} from "@/lib/profile";
import { fetchItemSummaries, type ItemSummary } from "@/lib/items";
import ProfilePage from "./page";

vi.mock("@/lib/items", () => ({ fetchItemSummaries: vi.fn() }));
vi.mock("@/lib/profile", () => ({ fetchAnalytics: vi.fn(), fetchDimensionScores: vi.fn() }));

const PERSON = { personId: 7, displayName: "ada" };

function count(pullRequestNumbers: number[] = [], evidenceItemIds: number[] = []): Count {
  return { count: evidenceItemIds.length || pullRequestNumbers.length, evidenceItemIds, pullRequestNumbers };
}

const EMPTY_DISTRIBUTION: Distribution = { total: 0, slices: [] };

const NO_COVERAGE: Coverage = { judged: 0, total: 0 };

const NO_WORK_LEVEL: WeightedWorkLevel = {
  weightedTotal: 0,
  weightedAtT2OrAbove: 0,
  shareAtT2OrAbove: null,
  weightedAtT3: 0,
  shareAtT3: null,
};

const BREAKDOWN = {
  substantive: count(),
  notSubstantive: count(),
  coverage: NO_COVERAGE,
  byCommentType: EMPTY_DISTRIBUTION,
  byTier: EMPTY_DISTRIBUTION,
  notJudgedYet: count(),
};

const TEST_SHARE = { total: 0, tested: count(), untested: count(), share: null, testedByTier: EMPTY_DISTRIBUTION };

function analytics(overrides: Partial<Analytics> = {}): Analytics {
  return {
    person: PERSON,
    quarter: "2026-Q3",
    workClassification: { distribution: EMPTY_DISTRIBUTION, coverage: NO_COVERAGE },
    bugsFixed: count(),
    workComplexity: { coverage: NO_COVERAGE, person: EMPTY_DISTRIBUTION, restOfRepository: EMPTY_DISTRIBUTION },
    workLevel: { person: NO_WORK_LEVEL, restOfRepository: NO_WORK_LEVEL, line: 1 / 3, coverage: NO_COVERAGE },
    reviewParticipation: {
      pullRequestsReviewed: count(),
      authorsReviewed: count(),
      comments: BREAKDOWN,
      substantivePerPullRequestReviewed: null,
    },
    reviewFindingsReceived: { pullRequests: count(), comments: BREAKDOWN, substantivePerPullRequest: null },
    testingContribution: {
      coverage: NO_COVERAGE,
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

function trailItem(id: number, overrides: Partial<TrailItem> = {}): TrailItem {
  return {
    evidenceItemId: id,
    itemKey: `item-${id}`,
    evidenceType: "feature_work",
    tier: "T2",
    complexity: "medium",
    weight: 2,
    pullRequestNumbers: [100 + id],
    sourceUrl: `https://github.com/opf/openproject/pull/${100 + id}`,
    ...overrides,
  };
}

function summary(id: number, overrides: Partial<ItemSummary> = {}): ItemSummary {
  return {
    id,
    type: "feature_work",
    date: "2026-07-03",
    quarter: "2026-Q3",
    title: `Item title ${id}`,
    sourceUrl: "https://github.com/opf/openproject/pull/101",
    links: [],
    person: PERSON,
    tier: "T2",
    current: true,
    currentItemId: null,
    ...overrides,
  };
}

function typeTrail(overrides: Partial<EvidenceTypeTrail> = {}): EvidenceTypeTrail {
  return {
    evidenceType: "feature_work",
    rule: "SHARE",
    counted: [],
    notJudgedYet: [],
    notSubstantive: [],
    notReadAtLevel: [],
    presence: [],
    ...overrides,
  };
}

function withTrail(
  base: DimensionScore,
  trail: Partial<DimensionScore["trail"]>,
  gates: GateResult[] = [],
): DimensionScore {
  return { ...base, gates, trail: { ...base.trail, ...trail, gates } };
}

function gate(name: GateResult["gate"], overrides: Partial<GateResult> = {}): GateResult {
  return {
    gate: name,
    outcome: "PASS",
    unverifiedReason: null,
    personTestedPullRequests: [201, 202],
    personPullRequests: [201, 202, 203],
    repositoryTestedPullRequests: [301],
    repositoryPullRequests: [301, 302],
    personSharePercent: 66.7,
    repositorySharePercent: 50,
    differencePoints: -16.7,
    developersInGroup: [1, 2, 3, 4],
    minimumDevelopers: 3,
    limits: { passWithinPoints: 10, farMissBeyondPoints: 25 },
    pullRequestsWithoutDigest: [],
    scored: true,
    note: null,
    ...overrides,
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

function answeringWith(
  levelScores: DimensionScores,
  profileAnalytics: Analytics = analytics(),
  summaries: ItemSummary[] = [],
): void {
  vi.mocked(fetchAnalytics).mockResolvedValue({ status: "ok", data: profileAnalytics });
  vi.mocked(fetchDimensionScores).mockResolvedValue({ status: "ok", data: levelScores });
  vi.mocked(fetchItemSummaries).mockResolvedValue({ status: "ok", data: summaries });
}

describe("ProfilePage", () => {
  afterEach(() => {
    vi.mocked(fetchAnalytics).mockReset();
    vi.mocked(fetchDimensionScores).mockReset();
    vi.mocked(fetchItemSummaries).mockReset();
    vi.unstubAllEnvs();
  });

  it("shows both dimension scores at the selected level with the why line", async () => {
    const atLevel: Record<Level, DimensionScores> = {
      L1: scores("L1", [dimension("design", DESIGN, "T2", 2), dimension("engineering", ENGINEERING, "T2", 2)]),
      L2: scores("L2", [dimension("design", DESIGN, "T2", 1), dimension("engineering", ENGINEERING, "T2", 1)]),
      L3: scores("L3", [dimension("design", DESIGN, "T2", 0), dimension("engineering", ENGINEERING, null, 0)]),
    };
    vi.mocked(fetchAnalytics).mockResolvedValue({ status: "ok", data: analytics() });
    vi.mocked(fetchItemSummaries).mockResolvedValue({ status: "ok", data: [] });
    vi.mocked(fetchDimensionScores).mockImplementation(async (_url, _id, _quarter, level) => ({
      status: "ok",
      data: atLevel[level],
    }));

    const levelOne = await render({ quarter: "2026-Q3", level: "L1" });
    const levelThree = await render({ quarter: "2026-Q3", level: "L3" });

    expect(levelOne).toContain(DESIGN);
    expect(levelOne).toContain("Works at T2. L1 expects T1: 2.");
    expect(levelThree).toContain("Works at T2. L3 expects T3: 0.");
    expect(levelThree).toContain("No working tier. L3 expects T3: 0.");
    expect(fetchDimensionScores).toHaveBeenLastCalledWith(undefined, 7, "2026-Q3", "L3");
  });

  it("shows the working tier, the next step and no next step at a score of 2", async () => {
    answeringWith(scores("L2", [dimension("design", DESIGN, "T2", 1), dimension("engineering", ENGINEERING, "T3", 2)]));

    const html = await render({ quarter: "2026-Q3" });

    expect(html).toContain("works at T2");
    expect(html).toContain("works at T3");
    expect(html.match(/To score/g)).toHaveLength(1);
  });

  it("shows the two dimension scores in the header", async () => {
    answeringWith(
      scores("L2", [dimension("design", DESIGN, "T2", 1), dimension("engineering", ENGINEERING, null, 2)]),
      analytics({
        workClassification: { distribution: EMPTY_DISTRIBUTION, coverage: { judged: 17, total: 20 } },
      }),
    );

    const html = await render({ quarter: "2026-Q3" });

    const header = html.slice(html.indexOf("<header"), html.indexOf("</header>"));
    expect(header).toContain(DESIGN);
    expect(header).toContain(ENGINEERING);
    expect(header).toContain("1 of 2");
    expect(header).toContain("2 of 2");
    expect(header).toContain("works at T2");
    expect(header).toContain("no working tier");
    expect(header).toContain("Based on 17 of 20 PRs judged");
  });

  it("shows the work level against the rest of the repository with one sentence that reads it", async () => {
    const level = (atT2OrAbove: number, atT3: number): WeightedWorkLevel => ({
      ...NO_WORK_LEVEL,
      weightedTotal: 10,
      shareAtT2OrAbove: atT2OrAbove,
      shareAtT3: atT3,
    });
    answeringWith(
      scores("L2", []),
      analytics({
        workLevel: {
          person: level(0.83, 0.14),
          restOfRepository: level(0.7, 0.18),
          line: 1 / 3,
          coverage: { judged: 7, total: 8 },
        },
      }),
    );

    const html = await render({ quarter: "2026-Q3" });

    expect(html).toContain("Medium or harder: ada 83%, Rest of repository 70%");
    expect(html).toContain("High: ada 14%, Rest of repository 18%");
    expect(html).toContain("Line to clear: 33%");
    expect(html).toContain(
      "83% of ada&#x27;s Feature work was Medium or harder, at or above the one-third line, so it counts at T2.",
    );
    expect(html).toContain("Judged Feature work: 7 of 8");
  });

  it("shows the work types as a donut with the three delivery tiles, each opening to its items", async () => {
    const workTypes: Distribution = {
      total: 4,
      slices: [
        { name: "feature", count: count([21, 22]), share: 0.5 },
        { name: "fix", count: count([23]), share: 0.25 },
        { name: "docs", count: count([24]), share: 0.25 },
      ],
    };
    answeringWith(
      scores("L2", []),
      analytics({
        workClassification: { distribution: workTypes, coverage: NO_COVERAGE },
        bugsFixed: count([30, 31], [41, 42]),
        workComplexity: {
          coverage: NO_COVERAGE,
          person: { total: 2, slices: [{ name: "low", count: count([], [5, 6]), share: 1 }] },
          restOfRepository: { total: 1, slices: [{ name: "high", count: count([], [7]), share: 1 }] },
        },
      }),
    );

    const html = await render({ quarter: "2026-Q3" });

    expect(html).toContain("Feature: 2 (50%)");
    expect(html).toMatch(/>2<\/span> Feature work items/);
    expect(html).toMatch(/>4<\/span> PRs merged/);
    expect(html).toMatch(/>2<\/span> Bugs fixed/);
    expect(html).toContain('href="/items/41"');
    expect(html).toContain('href="https://github.com/opf/openproject/pull/23"');
    expect(html).toContain('href="/items/5"');
    expect(html).toContain('href="/items/7"');
    expect(html).toContain("Low: 2");
    expect(html).toContain("High: 1");
  });

  it("shows the header with the name, quarter and level links that keep the quarter", async () => {
    answeringWith(scores("L2", [dimension("design", DESIGN, "T2", 1)]));

    const html = await render({ quarter: "2026-Q3", level: "L2" });

    expect(html).toContain("ada");
    expect(html).toContain("2026-Q3");
    expect(html).toContain('href="/developers/7/profile?quarter=2026-Q3&amp;level=L1"');
    expect(html).toContain('href="/developers/7/profile?quarter=2026-Q3&amp;level=L3"');
    expect(html).toMatch(/aria-current="page"[^>]*>L2</);
    expect(html).toContain('href="/developers/7?quarter=2026-Q3&amp;level=L2"');
    expect(html).toContain('href="/developers"');
  });

  it("marks the selected level with a class of its own", async () => {
    answeringWith(scores("L2", [dimension("design", DESIGN, "T2", 1)]));

    const html = await render({ quarter: "2026-Q3", level: "L2" });

    const classOf = (level: string) => html.match(new RegExp(`class="([^"]*)"[^>]*>${level}</a>`))?.[1];
    expect(classOf("L2")).toBeDefined();
    expect(classOf("L2")).not.toEqual(classOf("L1"));
    expect(classOf("L1")).toEqual(classOf("L3"));
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
    expect(html).toContain("<h1");
  });

  it("warns that the numbers will change when pull requests or items are not judged yet", async () => {
    answeringWith(
      scores("L2", [dimension("design", DESIGN, "T2", 1)]),
      analytics({
        workClassification: {
          distribution: {
            total: 3,
            slices: [{ name: "not_judged_yet", count: count([11, 12]), share: 0.5 }],
          },
          coverage: NO_COVERAGE,
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
      "2 PRs and 1 item are not judged yet; the numbers will change",
    );
  });

  it("uses the singular for one pull request and for one item in the notice", async () => {
    answeringWith(
      scores("L2", [dimension("design", DESIGN, "T2", 1)]),
      analytics({
        workClassification: {
          distribution: { total: 1, slices: [{ name: "not_judged_yet", count: count([11]), share: 1 }] },
          coverage: NO_COVERAGE,
        },
        reviewParticipation: {
          pullRequestsReviewed: count(),
          authorsReviewed: count(),
          comments: { ...BREAKDOWN, notJudgedYet: count([], [5]) },
          substantivePerPullRequestReviewed: null,
        },
      }),
    );

    expect(await render({ quarter: "2026-Q3" })).toContain("1 PR and 1 item are not judged yet");
  });

  it("does not count the repository's unjudged Feature work in the notice", async () => {
    answeringWith(
      scores("L2", [dimension("design", DESIGN, "T2", 1)]),
      analytics({
        workComplexity: {
          coverage: NO_COVERAGE,
          person: EMPTY_DISTRIBUTION,
          restOfRepository: { total: 2, slices: [{ name: "not_judged_yet", count: count([], [5, 6]), share: 1 }] },
        },
      }),
    );

    expect(await render({ quarter: "2026-Q3" })).not.toContain("are not judged yet");
  });

  it("shows the header, the notice and the sections in order", async () => {
    const engineering = withTrail(dimension("engineering", ENGINEERING, "T2", 1), {}, [gate("G1")]);
    answeringWith(
      scores("L2", [dimension("design", DESIGN, "T2", 1), engineering]),
      analytics({
        workClassification: {
          distribution: { total: 1, slices: [{ name: "not_judged_yet", count: count([11]), share: 1 }] },
          coverage: NO_COVERAGE,
        },
      }),
    );

    const html = await render({ quarter: "2026-Q3" });

    const positions = [
      "<h1",
      "are not judged yet",
      "1 · Level of the work",
      "2 · What kind of work",
      "3 · Dimensions",
      "4 · Quality checks",
      "5 · Reviews",
      "Details: every item",
    ].map((text) => html.indexOf(text));
    expect(positions).not.toContain(-1);
    expect(positions).toEqual([...positions].sort((first, second) => first - second));
  });

  it("logs the item ids the batch answer left out and names them by number", async () => {
    const warning = vi.spyOn(console, "warn").mockImplementation(() => undefined);
    const design = withTrail(dimension("design", DESIGN, "T2", 1), {
      evidenceTypes: [typeTrail({ counted: [trailItem(41), trailItem(42), trailItem(43)] })],
    });
    answeringWith(scores("L2", [design]), analytics(), [summary(41)]);

    const html = await render({ quarter: "2026-Q3" });

    expect(warning).toHaveBeenCalledOnce();
    expect(warning.mock.calls[0][0]).toContain("42, 43");
    expect(html).toContain("item #42");
    warning.mockRestore();
  });

  it("logs nothing when every item has its summary", async () => {
    const warning = vi.spyOn(console, "warn").mockImplementation(() => undefined);
    const design = withTrail(dimension("design", DESIGN, "T2", 1), {
      evidenceTypes: [typeTrail({ counted: [trailItem(41)] })],
    });
    answeringWith(scores("L2", [design]), analytics(), [summary(41)]);

    await render({ quarter: "2026-Q3" });

    expect(warning).not.toHaveBeenCalled();
    warning.mockRestore();
  });

  it("shows no notice when everything is judged", async () => {
    answeringWith(scores("L2", [dimension("design", DESIGN, "T2", 1)]));

    expect(await render({ quarter: "2026-Q3" })).not.toContain("are not judged yet");
  });

  it("a trail item opens to its item page and its pull request on GitHub", async () => {
    const design = withTrail(dimension("design", DESIGN, "T2", 1), {
      evidenceTypes: [typeTrail({ counted: [trailItem(41)] })],
    });
    answeringWith(scores("L2", [design]), analytics(), [summary(41, { title: "Single sign-on" })]);

    const html = await render({ quarter: "2026-Q3" });

    expect(html).toContain("<details");
    expect(html).toContain("Details: every item");
    expect(html).toContain('href="/items/41"');
    expect(html).toContain("Single sign-on");
    expect(html).toContain('href="https://github.com/opf/openproject/pull/141"');
    expect(html).toContain("weight 2");
  });

  it("marks a superseded trail item as no longer current with a link to the current row", async () => {
    const design = withTrail(dimension("design", DESIGN, "T2", 1), {
      evidenceTypes: [typeTrail({ counted: [trailItem(41)] })],
    });
    answeringWith(scores("L2", [design]), analytics(), [summary(41, { current: false, currentItemId: 55 })]);

    const html = await render({ quarter: "2026-Q3" });

    expect(html).toContain("no longer current");
    expect(html).toContain('href="/items/55"');
  });

  it("asks for the summaries of the items on the page, once", async () => {
    const design = withTrail(dimension("design", DESIGN, "T2", 1), {
      evidenceTypes: [typeTrail({ counted: [trailItem(41), trailItem(42)] })],
    });
    answeringWith(scores("L2", [design]), analytics());

    await render({ quarter: "2026-Q3" });

    expect(fetchItemSummaries).toHaveBeenCalledWith(undefined, [41, 42]);
  });

  it("names an item without a summary by its number and still links to it", async () => {
    const design = withTrail(dimension("design", DESIGN, "T2", 1), {
      evidenceTypes: [typeTrail({ counted: [trailItem(41)] })],
    });
    vi.mocked(fetchAnalytics).mockResolvedValue({ status: "ok", data: analytics() });
    vi.mocked(fetchDimensionScores).mockResolvedValue({ status: "ok", data: scores("L2", [design]) });
    vi.mocked(fetchItemSummaries).mockResolvedValue({ status: "down" });

    const html = await render({ quarter: "2026-Q3" });

    expect(html).toContain("item #41");
    expect(html).toContain('href="/items/41"');
  });

  it("shows the presence rows, the groups of items, the level three rule and the notes in the trail", async () => {
    const design = withTrail(dimension("design", DESIGN, "T2", 1), {
      evidenceTypes: [
        typeTrail({
          counted: [trailItem(41)],
          notJudgedYet: [trailItem(42, { tier: null, weight: null })],
          notSubstantive: [trailItem(43)],
          notReadAtLevel: [trailItem(44)],
          presence: [
            { tier: "T1", atOrAbove: 5, total: 9, presence: true },
            { tier: "T2", atOrAbove: 2, total: 9, presence: false },
            { tier: "T3", atOrAbove: 0, total: 9, presence: false },
          ],
        }),
      ],
      levelThreeRule: { t3Items: 1, t3ItemsNeeded: 2, met: false },
      countedNotTiered: [trailItem(45, { tier: null })],
      notes: ["Bug fix is never Feature work"],
    });
    answeringWith(scores("L2", [design]), analytics());

    const html = await render({ quarter: "2026-Q3" });

    expect(html).toContain("T1: 5 of 9 at or above, needs 1/3: yes");
    expect(html).toContain("T2: 2 of 9 at or above, needs 1/3: no");
    expect(html).toContain("Not judged yet: 1");
    expect(html).toContain("Not substantive: 1");
    expect(html).toContain("Not read at this level: 1");
    expect(html).toContain("Counted, not tiered: 1");
    expect(html).toContain("1 of 2 T3 items needed");
    expect(html).toContain("Bug fix is never Feature work");
    for (const id of [41, 42, 43, 44, 45]) {
      expect(html).toContain(`href="/items/${id}"`);
    }
  });

  it("shows the dimensions in a table with the next step, and one row each for the others", async () => {
    answeringWith(scores("L2", [dimension("design", DESIGN, "T2", 1), dimension("engineering", ENGINEERING, "T3", 2)]));

    const html = await render({ quarter: "2026-Q3" });

    const table = html.slice(html.indexOf("<table"), html.indexOf("</table>"));
    expect(table).toContain(DESIGN);
    expect(table).toContain("Works at T2. L2 expects T2: 1.");
    expect(table).toContain("To score 2");
    expect(table).toContain("Works at T3. L2 expects T2: 2.");
    expect(table).toContain("Not assessed in the pilot");
    for (const name of [
      "Design and architecture",
      "Planning and execution",
      "Collaboration",
      "Mentoring",
      "Knowledge sharing",
      "Hiring",
      "Pre-sales",
      "Client delivery",
    ]) {
      expect(table).toContain(name);
    }
    expect(table).toContain("13 lead-rated dimensions");
    expect(table).toContain("Not rated: no lead input in the pilot");
  });

  it("shows gates G1 and G2 as pass, close miss or far miss and G4 with the rest as unverified", async () => {
    const engineering = withTrail(dimension("engineering", ENGINEERING, "T2", 1), {}, [
      gate("G1", { outcome: "CLOSE_MISS" }),
      gate("G2", { outcome: "FAR_MISS", personTestedPullRequests: [], personPullRequests: [205] }),
      gate("G4", { outcome: "UNVERIFIED", unverifiedReason: "NOT_CALCULATED" }),
    ]);
    answeringWith(scores("L2", [dimension("design", DESIGN, "T2", 1), engineering]));

    const html = await render({ quarter: "2026-Q3" });

    const quality = html.slice(html.indexOf("4 · Quality checks"), html.indexOf("5 · Reviews"));
    expect(quality).toContain("Tests with features");
    expect(quality).toContain("2 of 3 feature PRs have tests");
    expect(quality).toContain("repository 50%");
    expect(quality).toContain("Close miss");
    expect(quality).toContain("Tests with bug fixes");
    expect(quality).toContain("0 of 1 bug fix PRs change tests");
    expect(quality).toContain("Far miss");
    expect(quality).toContain("G4, escaped defects and sprint commitment: unverified, no data in the pilot");
    expect(quality).toContain('href="https://github.com/opf/openproject/pull/201"');
    expect(quality).toContain('href="https://github.com/opf/openproject/pull/301"');
  });

  it('G2 shows its result with the tag "not scored"', async () => {
    const engineering = withTrail(dimension("engineering", ENGINEERING, "T2", 1), {}, [
      gate("G1", { outcome: "CLOSE_MISS" }),
      gate("G2", { outcome: "FAR_MISS", scored: false }),
      gate("G4", { outcome: "UNVERIFIED", unverifiedReason: "NOT_CALCULATED", scored: false }),
    ]);
    answeringWith(scores("L2", [engineering]));

    const html = await render({ quarter: "2026-Q3" });

    const quality = html.slice(html.indexOf("4 · Quality checks"), html.indexOf("5 · Reviews"));
    expect(quality).toContain("Far miss");
    expect(quality.match(/not scored/g)).toHaveLength(1);
    const bugFixCheck = quality.slice(quality.indexOf("Tests with bug fixes"));
    expect(bugFixCheck).toContain("not scored");
  });

  it("says why a gate is unverified instead of giving an outcome", async () => {
    const engineering = withTrail(dimension("engineering", ENGINEERING, "T2", 1), {}, [
      gate("G1", { outcome: "UNVERIFIED", unverifiedReason: "TOO_FEW_DEVELOPERS" }),
    ]);
    answeringWith(scores("L2", [engineering]));

    const html = await render({ quarter: "2026-Q3" });

    expect(html).toContain("Fewer than 3 developers have pull requests of this kind");
    expect(html).toContain("Unverified");
  });

  it("shows written comments with not substantive and not judged counts", async () => {
    const tier = (name: string, ids: number[]) => ({ name, count: count([], ids), share: ids.length / 3 });
    answeringWith(
      scores("L2", []),
      analytics({
        reviewParticipation: {
          pullRequestsReviewed: count(),
          authorsReviewed: count(),
          comments: {
            ...BREAKDOWN,
            substantive: count([], [41, 42, 43]),
            notSubstantive: count([], [44]),
            notJudgedYet: count([], [45, 46]),
            coverage: { judged: 4, total: 6 },
            byTier: { total: 3, slices: [tier("T1", [41]), tier("T2", [42, 43])] },
          },
          substantivePerPullRequestReviewed: null,
        },
      }),
    );

    const html = await render({ quarter: "2026-Q3" });

    const reviews = html.slice(html.indexOf("5 · Reviews"));
    expect(reviews).toContain("6 review comments written");
    expect(reviews).toContain("1 not substantive, 2 not judged yet");
    expect(reviews).toContain("Judged substantive: T1 1, T2 2, T3 0, total 3");
    expect(reviews).toContain("T2 · Logic, edge case or readability: 2");
    expect(reviews).toContain('href="/items/42"');
    expect(reviews).toContain('href="/items/44"');
  });

  it("folds the trail of every dimension at the end", async () => {
    const design = withTrail(dimension("design", DESIGN, "T2", 1), {
      evidenceTypes: [typeTrail({ counted: [trailItem(41)] })],
    });
    answeringWith(scores("L2", [design]), analytics(), [summary(41)]);

    const html = await render({ quarter: "2026-Q3" });

    const fold = html.indexOf("Details: every item, tier and check behind the scores");
    expect(fold).toBeGreaterThan(html.indexOf("5 · Reviews"));
    expect(html.indexOf('href="/items/41"')).toBeGreaterThan(fold);
  });

  it("opens the items and pull requests behind an analytics number, the repository's too", async () => {
    const withPullRequests = count([801, 802]);
    answeringWith(
      scores("L2", []),
      analytics({
        workComplexity: {
          coverage: NO_COVERAGE,
          person: EMPTY_DISTRIBUTION,
          restOfRepository: { total: 2, slices: [{ name: "low", count: withPullRequests, share: 1 }] },
        },
      }),
    );

    const html = await render({ quarter: "2026-Q3" });

    expect(html).toContain('href="https://github.com/opf/openproject/pull/801"');
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
