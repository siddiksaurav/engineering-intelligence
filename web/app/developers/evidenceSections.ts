import type { EvidenceType, TypeCounts } from "@/lib/evidence";

/** A type of evidence item as the developer pages name it, in the order they show it. */
export type EvidenceSection = {
  type: EvidenceType;
  countKey: keyof TypeCounts;
  title: string;
  /** The name of the CSS class that colours the type in both CSS modules. */
  colorClass: string;
};

export const EVIDENCE_SECTIONS: EvidenceSection[] = [
  { type: "feature_work", countKey: "featureWork", title: "Feature work", colorClass: "featureWork" },
  { type: "bug_fix", countKey: "bugFix", title: "Bug fix", colorClass: "bugFix" },
  { type: "test_change", countKey: "testChange", title: "Test change", colorClass: "testChange" },
  {
    type: "refactor_performance",
    countKey: "refactorPerformance",
    title: "Refactor and performance",
    colorClass: "refactorPerformance",
  },
  { type: "security", countKey: "security", title: "Security", colorClass: "security" },
  { type: "infra_release", countKey: "infraRelease", title: "Infra and release", colorClass: "infraRelease" },
  { type: "documentation", countKey: "documentation", title: "Documentation", colorClass: "documentation" },
  {
    type: "review_comment_written",
    countKey: "reviewCommentWritten",
    title: "Review comment written",
    colorClass: "reviewCommentWritten",
  },
];
