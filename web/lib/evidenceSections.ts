import type { EvidenceType, TypeCounts } from "./evidence";

/** A type of evidence item as the pages name it, in the order they show it. */
export type EvidenceSection = {
  type: EvidenceType;
  countKey: keyof TypeCounts;
  title: string;
};

export const EVIDENCE_SECTIONS: EvidenceSection[] = [
  { type: "feature_work", countKey: "featureWork", title: "Feature work" },
  { type: "bug_fix", countKey: "bugFix", title: "Bug fix" },
  { type: "test_change", countKey: "testChange", title: "Test change" },
  { type: "refactor_performance", countKey: "refactorPerformance", title: "Refactor and performance" },
  { type: "security", countKey: "security", title: "Security" },
  { type: "infra_release", countKey: "infraRelease", title: "Infra and release" },
  { type: "documentation", countKey: "documentation", title: "Documentation" },
  { type: "review_comment_written", countKey: "reviewCommentWritten", title: "Review comment written" },
];

/** The screen title of an evidence type; undefined for a name that is no evidence type. */
export function typeTitle(type: string): string | undefined {
  return EVIDENCE_SECTIONS.find((section) => section.type === type)?.title;
}
