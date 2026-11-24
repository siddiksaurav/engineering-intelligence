import { describe, expect, it } from "vitest";
import type {
  Analytics,
  Count,
  Coverage,
  DimensionScore,
  DimensionScores,
  Distribution,
  GateResult,
  TrailItem,
  WeightedWorkLevel,
} from "./profile";
import { itemIdsOnPage, notJudgedYet, profileParams } from "./profileParams";

describe("profileParams", () => {
  it("takes the default quarter and L2 when the URL has none", () => {
    expect(profileParams({}, "2026-Q3")).toEqual({ valid: true, quarter: "2026-Q3", level: "L2" });
  });

  it("takes the quarter and level of the URL", () => {
    expect(profileParams({ quarter: "2026-Q2", level: "L3" }, undefined)).toEqual({
      valid: true,
      quarter: "2026-Q2",
      level: "L3",
    });
  });

  it.each(["L0", "L4", "l2", "", "L2,L3"])("is invalid for the level %j", (level) => {
    expect(profileParams({ level }, "2026-Q3")).toEqual({ valid: false, reason: `Unknown level "${level}"` });
  });

  it.each(["2026-Q5", "2026-Q0", "2026Q3", "26-Q3", "2026-q3", "", "2026-Q3 "])(
    "is invalid for the quarter %j",
    (quarter) => {
      expect(profileParams({ quarter }, "2026-Q3")).toEqual({ valid: false, reason: `Unknown quarter "${quarter}"` });
    },
  );

  it("is invalid when a parameter is given twice", () => {
    expect(profileParams({ level: ["L1", "L2"] }, "2026-Q3")).toEqual({
      valid: false,
      reason: 'Unknown level "L1,L2"',
    });
  });

  it("is invalid, with a reason, when the URL has no quarter and no default quarter is set", () => {
    expect(profileParams({}, undefined)).toEqual({
      valid: false,
      reason: "The URL has no quarter and DEFAULT_QUARTER is not set",
    });
  });

  it("is invalid when the default quarter is not a quarter", () => {
    expect(profileParams({}, "next summer")).toEqual({ valid: false, reason: 'Unknown quarter "next summer"' });
  });
});

function count(evidenceItemIds: number[] = [], pullRequestNumbers: number[] = []): Count {
  return { count: Math.max(evidenceItemIds.length, pullRequestNumbers.length), evidenceItemIds, pullRequestNumbers };
}

function distribution(slices: Record<string, Count> = {}): Distribution {
  return {
    total: 0,
    slices: Object.entries(slices).map(([name, sliceCount]) => ({ name, count: sliceCount, share: null })),
  };
}

const NO_COVERAGE: Coverage = { judged: 0, total: 0 };

const NO_WORK_LEVEL: WeightedWorkLevel = {
  weightedTotal: 0,
  weightedAtT2OrAbove: 0,
  shareAtT2OrAbove: null,
  weightedAtT3: 0,
  shareAtT3: null,
};

function breakdown(notJudged: Count = count(), substantive: Count = count()) {
  return {
    substantive,
    notSubstantive: count(),
    coverage: NO_COVERAGE,
    byCommentType: distribution(),
    byTier: distribution(),
    notJudgedYet: notJudged,
  };
}

function testShare(tested: Count = count(), byTier: Distribution = distribution()) {
  return { total: 0, tested, untested: count(), share: null, testedByTier: byTier };
}

function analytics(overrides: Partial<Analytics> = {}): Analytics {
  return {
    person: { personId: 7, displayName: "ada" },
    quarter: "2026-Q3",
    workClassification: { distribution: distribution(), coverage: NO_COVERAGE },
    workComplexity: { coverage: NO_COVERAGE, person: distribution(), restOfRepository: distribution() },
    workLevel: { person: NO_WORK_LEVEL, restOfRepository: NO_WORK_LEVEL, line: 1 / 3, coverage: NO_COVERAGE },
    reviewParticipation: {
      pullRequestsReviewed: count(),
      authorsReviewed: count(),
      comments: breakdown(),
      substantivePerPullRequestReviewed: null,
    },
    reviewFindingsReceived: { pullRequests: count(), comments: breakdown(), substantivePerPullRequest: null },
    testingContribution: {
      coverage: NO_COVERAGE,
      featureAndFixPullRequests: testShare(),
      withoutDigest: count(),
      bugFixPullRequests: testShare(),
    },
    hygiene: { total: 0, linked: count(), notLinked: count(), share: null },
    ...overrides,
  };
}

function trailItem(evidenceItemId: number): TrailItem {
  return {
    evidenceItemId,
    itemKey: `key-${evidenceItemId}`,
    evidenceType: "feature_work",
    tier: null,
    complexity: null,
    weight: null,
    pullRequestNumbers: [],
    sourceUrl: "https://example.test",
  };
}

function gateWithoutDigest(pullRequestsWithoutDigest: number[]): GateResult {
  return {
    gate: "G1",
    outcome: "PASS",
    unverifiedReason: null,
    personTestedPullRequests: [],
    personPullRequests: [],
    repositoryTestedPullRequests: [],
    repositoryPullRequests: [],
    personSharePercent: null,
    repositorySharePercent: null,
    differencePoints: null,
    developersInGroup: [],
    minimumDevelopers: 3,
    limits: null,
    pullRequestsWithoutDigest,
    note: null,
  };
}

function dimension(trailParts: {
  counted?: TrailItem[];
  notJudgedYet?: TrailItem[];
  notSubstantive?: TrailItem[];
  notReadAtLevel?: TrailItem[];
  countedNotTiered?: TrailItem[];
  gates?: GateResult[];
}): DimensionScore {
  const gates = trailParts.gates ?? [];
  return {
    key: "engineering_standards",
    name: "Engineering standards",
    workingTier: null,
    score: 0,
    gates,
    notes: [],
    trail: {
      dimensionKey: "engineering_standards",
      level: "L2",
      evidenceTypesVersion: "2026-Q2",
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
      evidenceTypes: [
        {
          evidenceType: "feature_work",
          rule: "SHARE",
          counted: trailParts.counted ?? [],
          notJudgedYet: trailParts.notJudgedYet ?? [],
          notSubstantive: trailParts.notSubstantive ?? [],
          notReadAtLevel: trailParts.notReadAtLevel ?? [],
          presence: [],
        },
      ],
      levelThreeRule: null,
      workingTier: null,
      scoreBeforeGates: 0,
      gates,
      countedNotTiered: trailParts.countedNotTiered ?? [],
      notes: [],
      finalScore: 0,
    },
  };
}

function scores(dimensions: DimensionScore[], scored = true): DimensionScores {
  return {
    person: { personId: 7, displayName: "ada" },
    quarter: "2026-Q3",
    level: "L2",
    scored,
    jobRunId: scored ? 1 : null,
    calculatedAt: null,
    dimensions,
  };
}

describe("notJudgedYet", () => {
  it("counts each pull request without a digest and each item not judged yet once", () => {
    const sameItemTwice = trailItem(5);
    const result = notJudgedYet(
      analytics({
        workClassification: {
          distribution: distribution({ not_judged_yet: count([], [100, 101]) }),
          coverage: NO_COVERAGE,
        },
        workComplexity: {
          coverage: NO_COVERAGE,
          person: distribution({ not_judged_yet: count([5, 6]) }),
          restOfRepository: distribution({ not_judged_yet: count([5, 6, 7]) }),
        },
        reviewParticipation: {
          pullRequestsReviewed: count(),
          authorsReviewed: count(),
          comments: breakdown(count([8])),
          substantivePerPullRequestReviewed: null,
        },
        reviewFindingsReceived: {
          pullRequests: count(),
          comments: breakdown(count([8, 9])),
          substantivePerPullRequest: null,
        },
        testingContribution: {
          coverage: NO_COVERAGE,
          featureAndFixPullRequests: testShare(count(), distribution({ not_judged_yet: count([10]) })),
          withoutDigest: count([], [101, 102]),
          bugFixPullRequests: testShare(count(), distribution({ not_judged_yet: count([10, 11]) })),
        },
      }),
      scores([
        dimension({ notJudgedYet: [sameItemTwice, trailItem(12)], gates: [gateWithoutDigest([102, 103])] }),
        dimension({ notJudgedYet: [sameItemTwice] }),
      ]),
    );

    // Item 7 is not judged yet only in the repository's complexity bar, so it is not the person's.
    expect(result).toEqual({ pullRequests: 4, items: 7 });
  });

  it("is zero for nothing unjudged, and works before a scoring run", () => {
    expect(notJudgedYet(analytics(), scores([], false))).toEqual({ pullRequests: 0, items: 0 });
  });
});

describe("itemIdsOnPage", () => {
  it("lists every item id the page can open once, in the order met", () => {
    const ids = itemIdsOnPage(
      analytics({
        workComplexity: {
          coverage: NO_COVERAGE,
          person: distribution({ low: count([3, 1]) }),
          restOfRepository: distribution({ low: count([1, 2]) }),
        },
        hygiene: { total: 1, linked: count([], [50]), notLinked: count(), share: 1 },
      }),
      scores([
        dimension({
          counted: [trailItem(2), trailItem(4)],
          notReadAtLevel: [trailItem(5)],
          countedNotTiered: [trailItem(3)],
        }),
      ]),
    );

    expect(ids).toEqual([3, 1, 2, 4, 5]);
  });

  it("lists the ids of the analytics alone when there are no scores", () => {
    const withReviews = analytics({
      reviewParticipation: {
        pullRequestsReviewed: count(),
        authorsReviewed: count(),
        comments: breakdown(count([9]), count([8])),
        substantivePerPullRequestReviewed: null,
      },
    });

    expect(itemIdsOnPage(withReviews, scores([], false))).toEqual([8, 9]);
  });
});
