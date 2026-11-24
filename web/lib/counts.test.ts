import { describe, expect, it } from "vitest";
import { mergeCounts, sliceCount } from "./counts";

describe("mergeCounts", () => {
  it("adds the counts and lists each item and pull request once", () => {
    const merged = mergeCounts([
      { count: 2, evidenceItemIds: [1, 2], pullRequestNumbers: [10] },
      { count: 2, evidenceItemIds: [2, 3], pullRequestNumbers: [10, 11] },
    ]);

    expect(merged).toEqual({ count: 4, evidenceItemIds: [1, 2, 3], pullRequestNumbers: [10, 11] });
  });

  it("is an empty count for no counts", () => {
    expect(mergeCounts([])).toEqual({ count: 0, evidenceItemIds: [], pullRequestNumbers: [] });
  });
});

describe("sliceCount", () => {
  const distribution = {
    total: 1,
    slices: [{ name: "fix", count: { count: 1, evidenceItemIds: [], pullRequestNumbers: [3] }, share: 1 }],
  };

  it("gives the count of the named slice", () => {
    expect(sliceCount(distribution, "fix").pullRequestNumbers).toEqual([3]);
  });

  it("gives an empty count for a slice the distribution does not have", () => {
    expect(sliceCount(distribution, "docs").count).toBe(0);
  });
});
