import { describe, expect, it } from "vitest";
import type {
  DimensionScore,
  WeightedWorkLevel,
  WorkLevel,
  EvidenceTypeTrail,
  GateOutcome,
  GateResult,
  Level,
  Tier,
  TierPresence,
} from "./profile";
import { gateWords, nextStepLine, percent, plainName, whyLine, workLevelSentence } from "./profileText";

function presence(...present: Tier[]): TierPresence[] {
  return (["T1", "T2", "T3"] as const).map((tier) => ({
    tier,
    atOrAbove: 0,
    total: 0,
    presence: present.includes(tier),
  }));
}

function typeTrail(evidenceType: string, rule: "SHARE" | "ONE_ITEM", read: boolean): EvidenceTypeTrail {
  return {
    evidenceType,
    rule,
    counted: [],
    notJudgedYet: [],
    notSubstantive: [],
    notReadAtLevel: [],
    presence: read ? presence() : [],
  };
}

function gate(outcome: GateOutcome, overrides: Partial<GateResult> = {}): GateResult {
  return {
    gate: "G1",
    outcome,
    unverifiedReason: null,
    personTestedPullRequests: [],
    personPullRequests: [],
    repositoryTestedPullRequests: [],
    repositoryPullRequests: [],
    personSharePercent: 40,
    repositorySharePercent: 55,
    differencePoints: 15,
    developersInGroup: [],
    minimumDevelopers: 3,
    limits: { passWithinPoints: 10, farMissBeyondPoints: 25 },
    pullRequestsWithoutDigest: [],
    note: null,
    ...overrides,
  };
}

type Options = {
  level?: Level;
  workingTier?: Tier | null;
  score?: 0 | 1 | 2;
  scoreBeforeGates?: number;
  gates?: GateResult[];
  evidenceTypes?: EvidenceTypeTrail[];
  levelThreeRule?: { t3Items: number; t3ItemsNeeded: number; met: boolean } | null;
};

function dimension(options: Options = {}): DimensionScore {
  const level = options.level ?? "L2";
  const score = options.score ?? 0;
  const gates = options.gates ?? [];
  return {
    key: "engineering_standards",
    name: "Engineering standards",
    workingTier: options.workingTier === undefined ? "T2" : options.workingTier,
    score,
    gates,
    notes: [],
    trail: {
      dimensionKey: "engineering_standards",
      level,
      evidenceTypesVersion: "2026-Q2",
      gatesVersion: null,
      settings: {
        presenceThreshold: { numerator: 1, denominator: 3 },
        featureWorkWeights: { T1: 1, T2: 2, T3: 4 },
        frequentEvidenceTypes: ["FEATURE_WORK", "REVIEW_COMMENT_WRITTEN", "TEST_CHANGE"],
        t3ItemsNeededAtLevel3: { engineering_standards: 2 },
        gateLimits: {},
        gateMinimumDevelopers: 3,
        gateMinimumPullRequestsPerDeveloper: 1,
      },
      evidenceTypes: options.evidenceTypes ?? [typeTrail("feature_work", "SHARE", true)],
      levelThreeRule: options.levelThreeRule ?? null,
      workingTier: options.workingTier === undefined ? "T2" : options.workingTier,
      scoreBeforeGates: options.scoreBeforeGates ?? score,
      gates,
      countedNotTiered: [],
      notes: [],
      finalScore: score,
    },
  };
}

describe("percent", () => {
  it("rounds a share to a whole percent", () => {
    expect(percent(0.825)).toBe("83%");
    expect(percent(0.145)).toBe("15%");
    expect(percent(0)).toBe("0%");
    expect(percent(1)).toBe("100%");
  });

  it("says no PRs for a share that is null", () => {
    expect(percent(null)).toBe("no PRs");
  });
});

describe("whyLine", () => {
  it("says the working tier, the level's tier and the score, and names a gate that held it", () => {
    expect(whyLine(dimension({ level: "L3", score: 0 }), "L3")).toBe("Works at T2. L3 expects T3: 0.");
    expect(whyLine(dimension({ level: "L1", score: 1, scoreBeforeGates: 2, gates: [gate("CLOSE_MISS")] }), "L1")).toBe(
      "Works at T2. L1 expects T1: 2, held to 1 by G1 (close miss).",
    );
  });

  it("says there is no working tier", () => {
    expect(whyLine(dimension({ workingTier: null, score: 0 }), "L2")).toBe("No working tier. L2 expects T2: 0.");
  });

  it("names only the far misses when a far miss held the score", () => {
    const gates = [gate("CLOSE_MISS"), gate("FAR_MISS", { gate: "G2" })];

    expect(whyLine(dimension({ score: 0, scoreBeforeGates: 2, gates }), "L2")).toBe(
      "Works at T2. L2 expects T2: 2, held to 0 by G2 (far miss).",
    );
  });

  it("names a close miss and a far miss that both hold a score of 0 only by the far miss", () => {
    const gates = [gate("FAR_MISS", { gate: "G2" }), gate("CLOSE_MISS", { gate: "G4" })];

    expect(whyLine(dimension({ score: 0, scoreBeforeGates: 1, gates }), "L1")).toBe(
      "Works at T2. L1 expects T1: 1, held to 0 by G2 (far miss).",
    );
  });

  it("names every close miss that holds a score of 2 to 1", () => {
    const gates = [gate("CLOSE_MISS"), gate("CLOSE_MISS", { gate: "G2" })];

    expect(whyLine(dimension({ score: 1, scoreBeforeGates: 2, gates }), "L1")).toBe(
      "Works at T2. L1 expects T1: 2, held to 1 by G1 (close miss), G2 (close miss).",
    );
  });

  it("does not name a gate that did not hold the score", () => {
    expect(whyLine(dimension({ score: 1, gates: [gate("CLOSE_MISS")] }), "L2")).toBe("Works at T2. L2 expects T2: 1.");
  });
});

describe("nextStepLine", () => {
  const featureAndReviews = [
    typeTrail("feature_work", "SHARE", true),
    typeTrail("review_comment_written", "SHARE", true),
    typeTrail("security", "ONE_ITEM", true),
  ];

  it("asks for the level's tier at score 0, reading the fraction from the settings", () => {
    const line = nextStepLine(dimension({ score: 0, evidenceTypes: featureAndReviews }), "L2");

    expect(line).toBe(
      "To score 1: a third of weighted Feature work at Medium complexity or higher" +
        " or a third of Review comments written at Logic, edge case or readability or higher" +
        " or one Security item at T2: Dependency fix or access-control fix.",
    );
  });

  it("asks for the tier above the level's at score 1", () => {
    const line = nextStepLine(dimension({ score: 1, evidenceTypes: [typeTrail("feature_work", "SHARE", true)] }), "L1");

    expect(line).toBe("To score 2: a third of weighted Feature work at Medium complexity or higher.");
  });

  it("writes the fraction of the settings, not a fixed one", () => {
    const changed = dimension({ score: 0, evidenceTypes: [typeTrail("test_change", "SHARE", true)] });
    changed.trail.settings.presenceThreshold = { numerator: 1, denominator: 2 };

    expect(nextStepLine(changed, "L2")).toBe(
      "To score 1: a half of Test changes at Integration or regression test or higher.",
    );
  });

  it("has no line at score 2", () => {
    expect(nextStepLine(dimension({ score: 2 }), "L1")).toBeNull();
  });

  it("says T3 is the top tier when the next score needs a tier above T3", () => {
    expect(nextStepLine(dimension({ level: "L3", score: 1 }), "L3")).toBe(
      "T3 is the top tier in the PoC, so 1 is the best score at L3.",
    );
  });

  it("names the gate with its limit when a gate held the score", () => {
    const held = dimension({ score: 1, scoreBeforeGates: 2, gates: [gate("CLOSE_MISS")] });

    expect(nextStepLine(held, "L1")).toBe("To score 2: G1 within 10 points of the repository.");
  });

  it("asks only for the far-miss limit when a far miss held a score of 1", () => {
    const held = dimension({
      score: 0,
      scoreBeforeGates: 1,
      gates: [gate("FAR_MISS", { gate: "G2" })],
    });

    expect(nextStepLine(held, "L1")).toBe("To score 1: G2 within 25 points of the repository.");
  });

  it("names every gate that holds the denied score, the close miss beside the far miss", () => {
    const held = dimension({
      score: 0,
      scoreBeforeGates: 2,
      gates: [gate("CLOSE_MISS"), gate("FAR_MISS", { gate: "G2" })],
    });

    expect(nextStepLine(held, "L2")).toBe(
      "To score 2: G1 within 10 points of the repository and G2 within 10 points of the repository.",
    );
  });

  it("names a close miss that would hold the next score after the tier it needs", () => {
    const line = nextStepLine(
      dimension({
        score: 1,
        evidenceTypes: [typeTrail("feature_work", "SHARE", true)],
        gates: [gate("CLOSE_MISS")],
      }),
      "L2",
    );

    expect(line).toBe(
      "To score 2: a third of weighted Feature work at High complexity or higher;" +
        " and G1 within 10 points of the repository.",
    );
  });

  it("names a far miss that would keep a score of 0 at 0 after the tier it needs", () => {
    const line = nextStepLine(
      dimension({
        score: 0,
        evidenceTypes: [typeTrail("feature_work", "SHARE", true)],
        gates: [gate("FAR_MISS", { gate: "G2" })],
      }),
      "L2",
    );

    expect(line).toBe(
      "To score 1: a third of weighted Feature work at Medium complexity or higher;" +
        " and G2 within 25 points of the repository.",
    );
  });

  it("does not name a close miss when the next score is 1", () => {
    const line = nextStepLine(
      dimension({ score: 0, evidenceTypes: [typeTrail("feature_work", "SHARE", true)], gates: [gate("CLOSE_MISS")] }),
      "L2",
    );

    expect(line).toBe("To score 1: a third of weighted Feature work at Medium complexity or higher.");
  });

  it("does not name a gate that passed or is unverified", () => {
    const line = nextStepLine(
      dimension({
        score: 1,
        evidenceTypes: [typeTrail("feature_work", "SHARE", true)],
        gates: [gate("PASS"), gate("UNVERIFIED", { gate: "G4", limits: null })],
      }),
      "L1",
    );

    expect(line).toBe("To score 2: a third of weighted Feature work at Medium complexity or higher.");
  });

  it("leaves out a type that is not read at the level", () => {
    const trails = [typeTrail("feature_work", "SHARE", true), typeTrail("review_comment_written", "SHARE", false)];

    expect(nextStepLine(dimension({ score: 0, evidenceTypes: trails }), "L1")).toBe(
      "To score 1: a third of weighted Feature work at Low complexity or higher.",
    );
  });

  it("leaves out a type that has no wording at the tier", () => {
    const trails = [typeTrail("infra_release", "ONE_ITEM", true), typeTrail("security", "ONE_ITEM", true)];

    expect(nextStepLine(dimension({ score: 0, evidenceTypes: trails }), "L1")).toBe(
      "To score 1: one Security item at T1: Fix to a reported vulnerability in own code.",
    );
  });

  it("adds the T3 items the level three rule needs", () => {
    const line = nextStepLine(
      dimension({
        level: "L3",
        score: 0,
        evidenceTypes: [typeTrail("feature_work", "SHARE", true)],
        levelThreeRule: { t3Items: 0, t3ItemsNeeded: 2, met: false },
      }),
      "L3",
    );

    expect(line).toBe("To score 1: a third of weighted Feature work at High complexity or higher, and two T3 items.");
  });
});

describe("gateWords", () => {
  it("states a pass with the shares and the difference", () => {
    expect(gateWords(gate("PASS", { differencePoints: -5 }))).toEqual({
      outcome: "pass",
      detail: "The person is at 40%, the repository at 55%: 5 points above it. A pass is within 10 points below.",
    });
  });

  it("states a close miss with both limits", () => {
    expect(gateWords(gate("CLOSE_MISS", { differencePoints: 15.25 }))).toEqual({
      outcome: "close miss",
      detail:
        "The person is at 40%, the repository at 55%: 15.3 points below it. A pass is within 10 points below," +
        " a far miss is beyond 25.",
    });
  });

  it("states a far miss", () => {
    expect(gateWords(gate("FAR_MISS", { differencePoints: 30 })).outcome).toBe("far miss");
  });

  it.each([
    ["TOO_FEW_DEVELOPERS", "Fewer than 3 developers have pull requests of this kind, too few to compare."],
    ["NO_PULL_REQUEST_OF_THE_KIND", "The person has no pull request of this kind."],
    ["NO_REPOSITORY_PULL_REQUESTS", "The repository has no pull request of this kind."],
    ["NOT_CALCULATED", "This gate is not calculated in the PoC."],
  ] as const)("says why a gate is unverified: %s", (reason, words) => {
    expect(gateWords(gate("UNVERIFIED", { unverifiedReason: reason }))).toEqual({
      outcome: "unverified",
      detail: words,
    });
  });
});

describe("plainName", () => {
  it("writes a snake case name in words and uses the screen title of a work type", () => {
    expect(plainName("very_high")).toBe("Very high");
    expect(plainName("feature_work")).toBe("Feature work");
    expect(plainName("fix")).toBe("Fix");
  });
});

describe("workLevelSentence", () => {
  const share = (atT2OrAbove: number | null, atT3: number | null): WeightedWorkLevel => ({
    weightedTotal: atT2OrAbove === null ? 0 : 10,
    weightedAtT2OrAbove: 0,
    shareAtT2OrAbove: atT2OrAbove,
    weightedAtT3: 0,
    shareAtT3: atT3,
  });
  const workLevel = (person: WeightedWorkLevel): WorkLevel => ({
    person,
    restOfRepository: share(0.7, 0.18),
    line: 1 / 3,
    coverage: { judged: 7, total: 7 },
  });

  it("reads both shares against the line", () => {
    expect(workLevelSentence("ada", workLevel(share(0.83, 0.14)))).toBe(
      "83% of ada's Feature work was Medium or harder, at or above the one-third line, so it counts at T2. " +
        "High work is 14%, below the line, so not yet T3.",
    );
  });

  it("says not yet T2 for a share below the line", () => {
    expect(workLevelSentence("ada", workLevel(share(0.2, 0)))).toContain("below the one-third line, so not yet T2");
  });

  it("says there is no level to read without judged Feature work", () => {
    expect(workLevelSentence("ada", workLevel(share(null, null)))).toBe(
      "ada has no judged Feature work in this quarter yet, so there is no level to read.",
    );
  });
});
