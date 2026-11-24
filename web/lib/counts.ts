import type { Count, Distribution } from "./profile";

/** The counts added up: the items and pull requests behind each, every one once. */
export function mergeCounts(counts: Count[]): Count {
  const evidenceItemIds = [...new Set(counts.flatMap((count) => count.evidenceItemIds))];
  const pullRequestNumbers = [...new Set(counts.flatMap((count) => count.pullRequestNumbers))];
  const count = counts.reduce((sum, each) => sum + each.count, 0);
  return { count, evidenceItemIds, pullRequestNumbers };
}

const NO_ITEMS: Count = { count: 0, evidenceItemIds: [], pullRequestNumbers: [] };

/** The count of the slice with this name; an empty count when the distribution has no such slice. */
export function sliceCount(distribution: Distribution, name: string): Count {
  return distribution.slices.find((slice) => slice.name === name)?.count ?? NO_ITEMS;
}
