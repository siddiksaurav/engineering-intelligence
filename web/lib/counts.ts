import type { Count } from "./profile";

/** The counts added up: the items and pull requests behind each, every one once. */
export function mergeCounts(counts: Count[]): Count {
  const evidenceItemIds = [...new Set(counts.flatMap((count) => count.evidenceItemIds))];
  const pullRequestNumbers = [...new Set(counts.flatMap((count) => count.pullRequestNumbers))];
  const count = counts.reduce((sum, each) => sum + each.count, 0);
  return { count, evidenceItemIds, pullRequestNumbers };
}
