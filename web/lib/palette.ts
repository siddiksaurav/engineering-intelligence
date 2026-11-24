import type { EvidenceType } from "./evidence";

/** The colour of what has no judgment yet: neutral, so it never reads as a class. */
export const NOT_JUDGED_COLOR = "var(--not-judged)";

/** The one hue of a list of classes that have no colour each, such as the work types of pull requests. */
export const SINGLE_HUE_COLOR = "var(--azure-600)";

/** The person in a chart that sets them against the rest of the repository; the group is a neutral grey. */
export const PERSON_COLOR = "var(--step-3)";
export const COMPARISON_COLOR = "var(--comparison)";

/** The chart chrome is recessive: axis text, grid lines and the reference line never compete with the data. */
export const CHART_AXIS_COLOR = "var(--ink-3)";
export const CHART_GRID_COLOR = "var(--sky-100)";
export const CHART_REFERENCE_COLOR = "var(--ink-2)";
/** The gap between touching fills is drawn in the colour of the surface the chart sits on. */
export const CHART_SURFACE_COLOR = "var(--surface)";

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

/** Work types of pull requests are identities: a colour each where an evidence type has one, grey for the rest. */
const WORK_TYPE_COLORS: Record<string, string> = {
  feature: "var(--type-feature-work)",
  enhancement: "var(--step-1)",
  fix: "var(--type-bug-fix)",
  refactor: "var(--type-refactor-performance)",
  performance: "var(--type-refactor-performance)",
  test: "var(--type-test-change)",
  docs: "var(--type-documentation)",
  security: "var(--type-security)",
  infra: "var(--type-infra-release)",
};

const OTHER_WORK_TYPE_COLOR = "var(--ink-3)";

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

/** The colour of a work type of a pull request; "not judged yet" is neutral and the types without a colour share one grey. */
export function workTypeColor(name: string): string {
  return WORK_TYPE_COLORS[name] ?? orderedColor(name) ?? OTHER_WORK_TYPE_COLOR;
}
