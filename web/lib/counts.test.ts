import { describe, expect, it } from "vitest";
import { mergeCounts } from "./counts";

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
