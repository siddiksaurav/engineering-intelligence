import { describe, expect, it } from "vitest";
import { workTypeColor } from "./palette";
import type { Distribution } from "./profile";
import { workTypeSlices } from "./workTypeSlices";

describe("workTypeSlices", () => {
  it("gives each work type its plain name, count and colour", () => {
    const distribution: Distribution = {
      total: 3,
      slices: [
        { name: "feature", count: { count: 2, evidenceItemIds: [], pullRequestNumbers: [1, 2] }, share: 2 / 3 },
        { name: "not_judged_yet", count: { count: 1, evidenceItemIds: [], pullRequestNumbers: [3] }, share: 1 / 3 },
      ],
    };

    expect(workTypeSlices(distribution)).toEqual([
      { key: "feature", label: "Feature", value: 2, color: workTypeColor("feature") },
      { key: "not_judged_yet", label: "Not judged yet", value: 1, color: workTypeColor("not_judged_yet") },
    ]);
  });
});
