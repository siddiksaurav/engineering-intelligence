import { type BackendResult, readBackend } from "./backend";

export type Tier = "T1" | "T2" | "T3";
export type Level = "L1" | "L2" | "L3";
export type GateName = "G1" | "G2" | "G4";

/** A number of items and the pull requests behind it; the page opens both (decision 255). */
export type Count = {
  count: number;
  evidenceItemIds: number[];
  pullRequestNumbers: number[];
};

/** One bar of a distribution. The share is unrounded and null when the total is 0. */
export type Slice = {
  name: string;
  count: Count;
  share: number | null;
};

export type Distribution = {
  total: number;
  slices: Slice[];
};

/** How much of what an analytic counts the model has judged: "judged N of M". */
export type Coverage = { judged: number; total: number };

export type ReviewCommentBreakdown = {
  substantive: Count;
  notSubstantive: Count;
  byCommentType: Distribution;
  byTier: Distribution;
  notJudgedYet: Count;
  coverage: Coverage;
};

/** The weighted Feature work of one group; the shares are null when the weighted total is 0. */
export type WeightedWorkLevel = {
  weightedTotal: number;
  weightedAtT2OrAbove: number;
  shareAtT2OrAbove: number | null;
  weightedAtT3: number;
  shareAtT3: number | null;
};

/** The Feature work of the person and of the rest of the repository against the line a share must reach. */
export type WorkLevel = {
  person: WeightedWorkLevel;
  restOfRepository: WeightedWorkLevel;
  line: number;
  coverage: Coverage;
};

export type TestShare = {
  total: number;
  tested: Count;
  untested: Count;
  share: number | null;
  testedByTier: Distribution;
};

export type PersonSummary = { personId: number; displayName: string };

/** The five analytics and the hygiene number of one developer and quarter (F10). */
export type Analytics = {
  person: PersonSummary;
  quarter: string;
  workClassification: { distribution: Distribution; coverage: Coverage };
  workComplexity: { person: Distribution; restOfRepository: Distribution; coverage: Coverage };
  workLevel: WorkLevel;
  reviewParticipation: {
    pullRequestsReviewed: Count;
    authorsReviewed: Count;
    comments: ReviewCommentBreakdown;
    substantivePerPullRequestReviewed: number | null;
  };
  reviewFindingsReceived: {
    pullRequests: Count;
    comments: ReviewCommentBreakdown;
    substantivePerPullRequest: number | null;
  };
  testingContribution: {
    featureAndFixPullRequests: TestShare;
    withoutDigest: Count;
    bugFixPullRequests: TestShare;
    coverage: Coverage;
  };
  hygiene: { total: number; linked: Count; notLinked: Count; share: number | null };
};

export type GateOutcome = "PASS" | "CLOSE_MISS" | "FAR_MISS" | "UNVERIFIED";

export type UnverifiedReason =
  "TOO_FEW_DEVELOPERS" | "NO_PULL_REQUEST_OF_THE_KIND" | "NO_REPOSITORY_PULL_REQUESTS" | "NOT_CALCULATED";

export type GateLimits = { passWithinPoints: number; farMissBeyondPoints: number };

export type GateResult = {
  gate: GateName;
  outcome: GateOutcome;
  unverifiedReason: UnverifiedReason | null;
  personTestedPullRequests: number[];
  personPullRequests: number[];
  repositoryTestedPullRequests: number[];
  repositoryPullRequests: number[];
  personSharePercent: number | null;
  repositorySharePercent: number | null;
  differencePoints: number | null;
  developersInGroup: number[];
  minimumDevelopers: number;
  limits: GateLimits | null;
  pullRequestsWithoutDigest: number[];
  note: string | null;
};

/** An evidence item as the trail lists it. */
export type TrailItem = {
  evidenceItemId: number;
  itemKey: string;
  evidenceType: string;
  tier: Tier | null;
  complexity: string | null;
  weight: number | null;
  pullRequestNumbers: number[];
  sourceUrl: string;
};

export type TierPresence = {
  tier: Tier;
  atOrAbove: number;
  total: number;
  presence: boolean;
};

export type PresenceRule = "SHARE" | "ONE_ITEM";

export type EvidenceTypeTrail = {
  /** The database name of the type, such as `feature_work`. */
  evidenceType: string;
  rule: PresenceRule;
  counted: TrailItem[];
  notJudgedYet: TrailItem[];
  notSubstantive: TrailItem[];
  notReadAtLevel: TrailItem[];
  /** Empty when the type is not read at the level; otherwise one row per tier. */
  presence: TierPresence[];
};

export type ScoringSettings = {
  presenceThreshold: { numerator: number; denominator: number };
  featureWorkWeights: Record<Tier, number>;
  frequentEvidenceTypes: string[];
  t3ItemsNeededAtLevel3: Record<string, number>;
  gateLimits: Partial<Record<GateName, GateLimits>>;
  gateMinimumDevelopers: number;
  gateMinimumPullRequestsPerDeveloper: number;
};

export type LevelThreeRule = { t3Items: number; t3ItemsNeeded: number; met: boolean };

export type DimensionTrail = {
  dimensionKey: string;
  level: Level;
  evidenceTypesVersion: string;
  gatesVersion: string | null;
  settings: ScoringSettings;
  evidenceTypes: EvidenceTypeTrail[];
  levelThreeRule: LevelThreeRule | null;
  workingTier: Tier | null;
  scoreBeforeGates: number;
  gates: GateResult[];
  countedNotTiered: TrailItem[];
  notes: string[];
  finalScore: number;
};

export type DimensionScore = {
  key: string;
  name: string;
  workingTier: Tier | null;
  score: 0 | 1 | 2;
  gates: GateResult[];
  notes: string[];
  trail: DimensionTrail;
};

/** The scores of a developer at a selected level; `scored` is false, with no dimensions, before a scoring run. */
export type DimensionScores = {
  person: PersonSummary;
  quarter: string;
  level: Level;
  scored: boolean;
  jobRunId: number | null;
  calculatedAt: string | null;
  dimensions: DimensionScore[];
};

/** The analytics of one developer and quarter. Never throws. */
export async function fetchAnalytics(
  baseUrl: string | undefined,
  personId: number,
  quarter: string,
  fetchFn: typeof fetch = fetch,
): Promise<BackendResult<Analytics>> {
  const query = new URLSearchParams({ quarter });
  return readBackend<Analytics>(baseUrl, `/api/developers/${personId}/analytics?${query}`, fetchFn);
}

/** The dimension scores of one developer and quarter at a level. Never throws. */
export async function fetchDimensionScores(
  baseUrl: string | undefined,
  personId: number,
  quarter: string,
  level: Level,
  fetchFn: typeof fetch = fetch,
): Promise<BackendResult<DimensionScores>> {
  const query = new URLSearchParams({ quarter, level });
  return readBackend<DimensionScores>(baseUrl, `/api/developers/${personId}/dimension-scores?${query}`, fetchFn);
}
