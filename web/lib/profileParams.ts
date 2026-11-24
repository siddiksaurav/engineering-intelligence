import type {
  Analytics,
  Count,
  DimensionScores,
  Distribution,
  Level,
  ReviewCommentBreakdown,
  TestShare,
} from "./profile";

/** The slice name of a distribution that holds the items with no judgment yet. */
const NOT_JUDGED_YET = "not_judged_yet";

const QUARTER_PATTERN = /^\d{4}-Q[1-4]$/;
const LEVELS: readonly string[] = ["L1", "L2", "L3"];
const DEFAULT_LEVEL: Level = "L2";

export type ProfileParams = { valid: true; quarter: string; level: Level } | { valid: false; reason: string };

type SearchParams = Record<string, string | string[] | undefined>;

/**
 * Reads the quarter and the level of the profile URL. A missing quarter takes the default quarter, a missing
 * level takes L2; a wrong value is invalid and the page answers not found (decisions 237, 246, 247).
 */
export function profileParams(searchParams: SearchParams, defaultQuarter: string | undefined): ProfileParams {
  const quarter = valueOf(searchParams.quarter) ?? defaultQuarter;
  if (quarter === undefined) {
    return { valid: false, reason: "The URL has no quarter and DEFAULT_QUARTER is not set" };
  }
  if (!QUARTER_PATTERN.test(quarter)) {
    return { valid: false, reason: `Unknown quarter "${quarter}"` };
  }
  const level = valueOf(searchParams.level) ?? DEFAULT_LEVEL;
  if (!LEVELS.includes(level)) {
    return { valid: false, reason: `Unknown level "${level}"` };
  }
  return { valid: true, quarter, level: level as Level };
}

function valueOf(parameter: string | string[] | undefined): string | undefined {
  return Array.isArray(parameter) ? parameter.join(",") : parameter;
}

/**
 * The person's pull requests without a digest and items not judged yet, each counted once (decision 253). The
 * repository's unjudged Feature work is left out; its bar shows it.
 * The page shows the notice when either is above zero.
 */
export function notJudgedYet(analytics: Analytics, scores: DimensionScores): { pullRequests: number; items: number } {
  const pullRequests = new Set<number>([
    ...notJudgedSlices(analytics.workClassification.distribution).flatMap((count) => count.pullRequestNumbers),
    ...analytics.testingContribution.withoutDigest.pullRequestNumbers,
    ...scores.dimensions.flatMap((dimension) =>
      [...dimension.gates, ...dimension.trail.gates].flatMap((gate) => gate.pullRequestsWithoutDigest),
    ),
  ]);
  const items = new Set<number>([
    ...notJudgedIds(analytics.workComplexity.person),
    ...notJudgedIds(analytics.testingContribution.featureAndFixPullRequests.testedByTier),
    ...notJudgedIds(analytics.testingContribution.bugFixPullRequests.testedByTier),
    ...analytics.reviewParticipation.comments.notJudgedYet.evidenceItemIds,
    ...analytics.reviewFindingsReceived.comments.notJudgedYet.evidenceItemIds,
    ...scores.dimensions.flatMap((dimension) =>
      dimension.trail.evidenceTypes.flatMap((trail) => trail.notJudgedYet.map((item) => item.evidenceItemId)),
    ),
  ]);
  return { pullRequests: pullRequests.size, items: items.size };
}

/** Every evidence item id the page can open, each once, in the order the page meets them (decision 254). */
export function itemIdsOnPage(analytics: Analytics, scores: DimensionScores): number[] {
  const trailIds = scores.dimensions.flatMap((dimension) => [
    ...dimension.trail.evidenceTypes.flatMap((trail) =>
      [...trail.counted, ...trail.notJudgedYet, ...trail.notSubstantive, ...trail.notReadAtLevel].map(
        (item) => item.evidenceItemId,
      ),
    ),
    ...dimension.trail.countedNotTiered.map((item) => item.evidenceItemId),
  ]);
  return [...new Set([...analyticsCounts(analytics).flatMap((count) => count.evidenceItemIds), ...trailIds])];
}

function notJudgedSlices(distribution: Distribution): Count[] {
  return distribution.slices.filter((slice) => slice.name === NOT_JUDGED_YET).map((slice) => slice.count);
}

function notJudgedIds(distribution: Distribution): number[] {
  return notJudgedSlices(distribution).flatMap((count) => count.evidenceItemIds);
}

function analyticsCounts(analytics: Analytics): Count[] {
  const { reviewParticipation, reviewFindingsReceived, testingContribution, hygiene } = analytics;
  return [
    ...sliceCounts(analytics.workClassification.distribution),
    ...sliceCounts(analytics.workComplexity.person),
    ...sliceCounts(analytics.workComplexity.restOfRepository),
    reviewParticipation.pullRequestsReviewed,
    reviewParticipation.authorsReviewed,
    ...breakdownCounts(reviewParticipation.comments),
    reviewFindingsReceived.pullRequests,
    ...breakdownCounts(reviewFindingsReceived.comments),
    ...testShareCounts(testingContribution.featureAndFixPullRequests),
    testingContribution.withoutDigest,
    ...testShareCounts(testingContribution.bugFixPullRequests),
    hygiene.linked,
    hygiene.notLinked,
  ];
}

function sliceCounts(distribution: Distribution): Count[] {
  return distribution.slices.map((slice) => slice.count);
}

function breakdownCounts(breakdown: ReviewCommentBreakdown): Count[] {
  return [
    breakdown.substantive,
    breakdown.notSubstantive,
    ...sliceCounts(breakdown.byCommentType),
    ...sliceCounts(breakdown.byTier),
    breakdown.notJudgedYet,
  ];
}

function testShareCounts(share: TestShare): Count[] {
  return [share.tested, share.untested, ...sliceCounts(share.testedByTier)];
}
