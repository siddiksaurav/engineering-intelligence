import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  fetchAnalytics,
  fetchDimensionScores,
  type Analytics,
  type Count,
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

  it("separates the score from the working tier in the text", async () => {
    answeringWith(scores("L2", [dimension("design", DESIGN, "T2", 1)]));

    expect(await render({ quarter: "2026-Q3" })).toContain("1</strong> of 2, <span");
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

  it("marks the selected level with a class of its own", async () => {
    answeringWith(scores("L2", [dimension("design", DESIGN, "T2", 1)]));

    const html = await render({ quarter: "2026-Q3", level: "L2" });

    const classOf = (level: string) => html.match(new RegExp(`class="([^"]*)"[^>]*>${level}</a>`))?.[1];
    expect(classOf("L2")).toBeDefined();
    expect(classOf("L2")).not.toEqual(classOf("L1"));
    expect(classOf("L1")).toEqual(classOf("L3"));
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
      "2 PRs and 1 item are not judged yet; the numbers will change",
    );
  });

  it("uses the singular for one pull request and for one item in the notice", async () => {
    answeringWith(
      scores("L2", [dimension("design", DESIGN, "T2", 1)]),
      analytics({
        workClassification: { total: 1, slices: [{ name: "not_judged_yet", count: count([11]), share: 1 }] },
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
          person: EMPTY_DISTRIBUTION,
          repository: { total: 2, slices: [{ name: "not_judged_yet", count: count([], [5, 6]), share: 1 }] },
        },
      }),
    );

    expect(await render({ quarter: "2026-Q3" })).not.toContain("are not judged yet");
  });

  it("shows the header, the notice, the dimension cards, the gates panel and then the analytics", async () => {
    const engineering = withTrail(dimension("engineering", ENGINEERING, "T2", 1), {}, [gate("G1")]);
    answeringWith(
      scores("L2", [dimension("design", DESIGN, "T2", 1), engineering]),
      analytics({
        workClassification: { total: 1, slices: [{ name: "not_judged_yet", count: count([11]), share: 1 }] },
      }),
    );

    const html = await render({ quarter: "2026-Q3" });

    const positions = ["<h1", "are not judged yet", DESIGN, ENGINEERING, "Quality gates", "Work classification"].map(
      (text) => html.indexOf(text),
    );
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

    expect(await render({ quarter: "2026-Q3" })).not.toContain("not judged yet");
  });

  it("a trail item opens to its item page and its pull request on GitHub", async () => {
    const design = withTrail(dimension("design", DESIGN, "T2", 1), {
      evidenceTypes: [typeTrail({ counted: [trailItem(41)] })],
    });
    answeringWith(scores("L2", [design]), analytics(), [summary(41, { title: "Single sign-on" })]);

    const html = await render({ quarter: "2026-Q3" });

    expect(html).toContain("<details");
    expect(html).toContain("Trail");
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

  it("shows the gates panel once, with the shares, the limits and the PR lists that open", async () => {
    const engineering = withTrail(dimension("engineering", ENGINEERING, "T2", 1), {}, [
      gate("G1", { outcome: "CLOSE_MISS" }),
      gate("G2"),
      gate("G4", { outcome: "UNVERIFIED", unverifiedReason: "NOT_CALCULATED" }),
    ]);
    answeringWith(scores("L2", [dimension("design", DESIGN, "T2", 1), engineering]), analytics());

    const html = await render({ quarter: "2026-Q3" });

    expect(html.match(/Quality gates/g)).toHaveLength(1);
    expect(html).toContain("G1");
    expect(html).toContain("close miss");
    expect(html).toContain("G2");
    expect(html).toContain("66.7%");
    expect(html).toContain("4 developers");
    expect(html).toContain('href="https://github.com/opf/openproject/pull/201"');
    expect(html).toContain('href="https://github.com/opf/openproject/pull/301"');
    expect(html).toContain("G4");
    expect(html).toContain("gate unverified");
  });

  it("shows no gates panel when no dimension has gates", async () => {
    answeringWith(scores("L2", [dimension("design", DESIGN, "T2", 1)]), analytics());

    expect(await render({ quarter: "2026-Q3" })).not.toContain("Quality gates");
  });

  it("every analytics number on the page equals the API value", async () => {
    const slices = (total: number, parts: [string, number][]): Distribution => ({
      total,
      slices: parts.map(([name, size]) => ({
        name,
        count: count(
          [],
          Array.from({ length: size }, (_, index) => 900 + index),
        ),
        share: total === 0 ? null : size / total,
      })),
    });
    const breakdown = (
      substantive: number,
      types: [string, number][],
      tiers: [string, number][],
      unjudged: number,
    ) => ({
      substantive: count(
        [],
        Array.from({ length: substantive }, (_, index) => 800 + index),
      ),
      byCommentType: slices(
        types.reduce((sum, [, size]) => sum + size, 0),
        types,
      ),
      byTier: slices(
        tiers.reduce((sum, [, size]) => sum + size, 0),
        tiers,
      ),
      notJudgedYet: count(
        [],
        Array.from({ length: unjudged }, (_, index) => 700 + index),
      ),
    });
    const full = analytics({
      workClassification: slices(7, [
        ["feature_work", 3],
        ["bug_fix", 2],
        ["test_change", 1],
        ["not_judged_yet", 1],
      ]),
      workComplexity: {
        person: slices(8, [
          ["low", 1],
          ["medium", 2],
          ["high", 3],
          ["very_high", 1],
          ["not_judged_yet", 1],
        ]),
        repository: slices(9, [
          ["low", 4],
          ["medium", 2],
          ["high", 1],
          ["very_high", 1],
          ["not_judged_yet", 1],
        ]),
      },
      reviewParticipation: {
        pullRequestsReviewed: count([501, 502, 503, 504, 505]),
        authorsReviewed: count([], [601, 602, 603, 604]),
        comments: breakdown(
          5,
          [
            ["suggestion", 3],
            ["question", 2],
          ],
          [
            ["T1", 1],
            ["T2", 4],
          ],
          6,
        ),
        substantivePerPullRequestReviewed: 1.25,
      },
      reviewFindingsReceived: {
        pullRequests: count([511, 512, 513, 514, 515, 516, 517]),
        comments: breakdown(2, [["defect", 2]], [["T3", 2]], 7),
        substantivePerPullRequest: 0.5,
      },
      testingContribution: {
        featureAndFixPullRequests: {
          total: 8,
          tested: count([521, 522, 523, 524, 525]),
          untested: count([531, 532, 533]),
          share: 5 / 8,
          testedByTier: slices(5, [
            ["T1", 3],
            ["T2", 2],
          ]),
        },
        withoutDigest: count([541, 542, 543, 544, 545, 546, 547, 548]),
        bugFixPullRequests: {
          total: 3,
          tested: count([551, 552]),
          untested: count([561]),
          share: 2 / 3,
          testedByTier: slices(2, [["T1", 2]]),
        },
      },
      hygiene: {
        total: 11,
        linked: count([571, 572, 573, 574, 575, 576, 577, 578, 579]),
        notLinked: count([581, 582]),
        share: 9 / 11,
      },
    });
    answeringWith(scores("L2", [dimension("design", DESIGN, "T2", 1)]), full);

    const html = await render({ quarter: "2026-Q3" });

    for (const text of [
      "Feature work: 3",
      "Bug fix: 2",
      "Test change: 1",
      "43%",
      "29%",
      "14%",
      "Low: 1",
      "Very high: 1",
      "Not judged yet: 1",
      "Low: 4",
      "44%",
      "22%",
      "11%",
      "PRs reviewed: 5",
      "Authors reviewed: 4",
      "1.25",
      "Substantive comments: 5",
      "Suggestion: 3",
      "Question: 2",
      "60%",
      "40%",
      "T1: 1",
      "T2: 4",
      "20%",
      "80%",
      "Not judged yet: 6",
      "Own PRs merged: 7",
      "Defect: 2",
      "100%",
      "T3: 2",
      "Not judged yet: 7",
      "0.5",
      "Tested: 5",
      "63%",
      "T1: 3",
      "T2: 2",
      "Not tested: 3",
      "PRs without digest: 8",
      "Bug fix PRs tested: 2",
      "67%",
      "T1: 2",
      "Linked to a work item: 9",
      "82%",
      "Not linked: 2",
      "18%",
    ]) {
      expect(html).toContain(text);
    }
    const classification = html.slice(html.indexOf("Work classification"), html.indexOf("Work complexity"));
    const percents = [...classification.matchAll(/>(\d+)%</g)].map((match) => Number(match[1]));
    expect(percents.reduce((sum, value) => sum + value, 0)).toBe(100);
  });

  it("opens the items and pull requests behind an analytics number, the repository's too", async () => {
    const withPullRequests = count([801, 802]);
    answeringWith(
      scores("L2", []),
      analytics({
        workComplexity: {
          person: EMPTY_DISTRIBUTION,
          repository: { total: 2, slices: [{ name: "low", count: withPullRequests, share: 1 }] },
        },
      }),
    );

    const html = await render({ quarter: "2026-Q3" });

    expect(html).toContain('href="https://github.com/opf/openproject/pull/801"');
  });

  it("says no PRs and draws no bar for a share that is null", async () => {
    answeringWith(
      scores("L2", []),
      analytics({ hygiene: { total: 0, linked: count(), notLinked: count(), share: null } }),
    );

    const html = await render({ quarter: "2026-Q3" });

    expect(html).toContain("Tested: 0");
    expect(html.match(/no PRs/g)?.length).toBeGreaterThanOrEqual(2);
    expect(html).not.toContain('role="meter"');
  });

  it("shows four figures of the analytics in the header", async () => {
    answeringWith(
      scores("L2", []),
      analytics({
        reviewParticipation: {
          pullRequestsReviewed: count([1, 2, 3, 4, 5]),
          authorsReviewed: count(),
          comments: { ...BREAKDOWN, substantive: count([], [41, 42, 43]) },
          substantivePerPullRequestReviewed: null,
        },
        reviewFindingsReceived: {
          pullRequests: count([11, 12, 13, 14, 15, 16, 17]),
          comments: BREAKDOWN,
          substantivePerPullRequest: null,
        },
      }),
    );

    const html = await render({ quarter: "2026-Q3" });

    const figure = (label: string) => html.match(new RegExp(`>([^<]*)</span><span[^>]*>${label}<`))?.[1];
    expect(figure("Repository hygiene: pull requests linked to a work item")).toBe("63%");
    expect(figure("Own pull requests merged")).toBe("7");
    expect(figure("Pull requests reviewed")).toBe("5");
    expect(figure("Substantive comments written")).toBe("3");
  });

  it("draws the tier steps of a dimension with the step the level expects, and a pip per score point", async () => {
    answeringWith(scores("L3", [dimension("design", DESIGN, "T2", 1)]));

    const html = await render({ quarter: "2026-Q3", level: "L3" });

    expect(html).toContain('aria-label="Works at T2; L3 expects T3"');
    expect(html.match(/data-reached="true"/g)).toHaveLength(1);
    expect(html.match(/data-filled="true"/g)).toHaveLength(2);
  });

  it("draws ordered distributions as one segmented bar and class lists as a bar per row", async () => {
    const complexity: Distribution = {
      total: 4,
      slices: [
        { name: "low", count: count([], [1, 2, 3]), share: 0.75 },
        { name: "high", count: count([], [4]), share: 0.25 },
      ],
    };
    const workTypes: Distribution = {
      total: 4,
      slices: [
        { name: "fix", count: count([21, 22, 23]), share: 0.75 },
        { name: "enhancement", count: count([24]), share: 0.25 },
      ],
    };
    answeringWith(
      scores("L2", []),
      analytics({
        workClassification: workTypes,
        workComplexity: { person: complexity, repository: EMPTY_DISTRIBUTION },
      }),
    );

    const html = await render({ quarter: "2026-Q3" });

    expect(html).toContain('aria-label="Person: Low 3, High 1"');
    expect(html).not.toContain('aria-label="Repository:');
    const classification = html.slice(html.indexOf("Work classification"), html.indexOf("Work complexity"));
    expect(classification.match(/role="meter"/g)).toHaveLength(2);
    expect(classification).toContain('title="Fix: 3, 75%"');
    expect(classification).not.toContain('role="img"');
    const person = html.slice(html.indexOf("Work complexity"), html.indexOf("Review participation"));
    expect(person).not.toContain('role="meter"');
    expect(person.match(/background:var\(--step-1\)/g)).toHaveLength(2);
  });

  it("draws the tested shares and the hygiene share as ring meters", async () => {
    const tested = { total: 8, tested: count([1, 2, 3, 4, 5]), untested: count([6, 7, 8]), share: 5 / 8 };
    answeringWith(
      scores("L2", []),
      analytics({
        testingContribution: {
          featureAndFixPullRequests: { ...tested, testedByTier: EMPTY_DISTRIBUTION },
          withoutDigest: count(),
          bugFixPullRequests: TEST_SHARE,
        },
      }),
    );

    const html = await render({ quarter: "2026-Q3" });

    expect(html).toMatch(/role="meter" aria-label="Tested"[^>]*aria-valuenow="63"/);
    expect(html).toMatch(/role="meter" aria-label="Linked to a work item"[^>]*aria-valuenow="63"/);
    expect(html).not.toContain('aria-label="Bug fix PRs tested"');
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
