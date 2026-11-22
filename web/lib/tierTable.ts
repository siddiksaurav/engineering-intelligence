import type { Tier } from "./profile";

/** What a tier of one evidence type means. A copy of the spec 6.4 table for the six tiered types (decision 242). */
export type TierTableRow = {
  /** The type as a name. */
  name: string;
  /** The type as a name in a sentence about a share of its items. */
  plural: string;
  /** The wording of each tier; null when the type has no such tier. */
  tiers: Record<Tier, string | null>;
};

/** Keyed by the database name of the evidence type, as the trail gives it. */
export const TIER_TABLE: Record<string, TierTableRow> = {
  feature_work: {
    name: "Feature work",
    plural: "weighted Feature work",
    tiers: { T1: "Low complexity", T2: "Medium complexity", T3: "High complexity" },
  },
  review_comment_written: {
    name: "Review comment written",
    plural: "Review comments written",
    tiers: {
      T1: "A question or a small catch",
      T2: "Logic, edge case or readability",
      T3: "Design, security or performance",
    },
  },
  test_change: {
    name: "Test change",
    plural: "Test changes",
    tiers: {
      T1: "Unit test in own PR",
      T2: "Integration or regression test",
      T3: "A test gate in CI for a module",
    },
  },
  refactor_performance: {
    name: "Refactor and performance item",
    plural: "Refactor and performance items",
    tiers: {
      T1: "Cleanup in own PR",
      T2: "Small refactor or speed-up on own initiative",
      T3: "Planned debt item, or a dependency kept current, or a measured bottleneck removed",
    },
  },
  security: {
    name: "Security item",
    plural: "Security items",
    tiers: {
      T1: "Fix to a reported vulnerability in own code",
      T2: "Dependency fix or access-control fix",
      T3: "Security check added to the team pipeline",
    },
  },
  infra_release: {
    name: "Infra and release item",
    plural: "Infra and release items",
    tiers: {
      T1: null,
      T2: "Pipeline fix, Dockerfile, logging, or cutting a release (tag, branch, hotfix)",
      T3: "Team pipeline or monitoring",
    },
  },
};
