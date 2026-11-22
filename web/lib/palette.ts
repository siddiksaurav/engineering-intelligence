import type { EvidenceType } from "./evidence";

/** The colour of what has no judgment yet: neutral, so it never reads as a class. */
export const NOT_JUDGED_COLOR = "var(--not-judged)";

/** The one hue of a list of classes that have no colour each, such as the work types of pull requests. */
export const SINGLE_HUE_COLOR = "var(--azure-600)";

/** Evidence types are identities: a fixed colour each, checked for colour-blind readers in this order (decision 265). */
const TYPE_COLORS: Record<EvidenceType, string> = {
  feature_work: "var(--type-feature-work)",
  bug_fix: "var(--type-bug-fix)",
  test_change: "var(--type-test-change)",
  refactor_performance: "var(--type-refactor-performance)",
  security: "var(--type-security)",
  infra_release: "var(--type-infra-release)",
  documentation: "var(--type-documentation)",
  review_comment_written: "var(--type-review-comment-written)",
};

/** Tiers and complexities are magnitudes: one blue, darker for more. */
const ORDERED_COLORS: Record<string, string> = {
  low: "var(--step-1)",
  medium: "var(--step-2)",
  high: "var(--step-4)",
  very_high: "var(--step-5)",
  T1: "var(--step-1)",
  T2: "var(--step-3)",
  T3: "var(--step-5)",
  not_judged_yet: NOT_JUDGED_COLOR,
};

const SCORE_COLORS = ["var(--step-1)", "var(--step-3)", "var(--step-5)"] as const;

export function typeColor(type: EvidenceType): string {
  return TYPE_COLORS[type];
}

/** The colour of a tier, a complexity or "not judged yet"; undefined for a name that has no place in an order. */
export function orderedColor(name: string): string | undefined {
  return ORDERED_COLORS[name];
}

export function scoreColor(score: 0 | 1 | 2): string {
  return SCORE_COLORS[score];
}
