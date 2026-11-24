import { describe, expect, it } from "vitest";
import {
  CHART_AXIS_COLOR,
  CHART_GRID_COLOR,
  CHART_REFERENCE_COLOR,
  CHART_SURFACE_COLOR,
  NOT_JUDGED_COLOR,
  orderedColor,
  scoreColor,
  typeColor,
} from "./palette";

describe("palette", () => {
  it("gives every evidence type a colour of its own", () => {
    const types = [
      "feature_work",
      "bug_fix",
      "test_change",
      "refactor_performance",
      "security",
      "infra_release",
      "documentation",
      "review_comment_written",
    ] as const;

    expect(new Set(types.map(typeColor)).size).toBe(types.length);
    expect(typeColor("bug_fix")).toBe("var(--type-bug-fix)");
  });

  it("goes from light to dark over the tiers, the complexities and the scores", () => {
    expect(["T1", "T2", "T3"].map(orderedColor)).toEqual(["var(--step-1)", "var(--step-3)", "var(--step-5)"]);
    expect(["low", "medium", "high", "very_high"].map(orderedColor)).toEqual([
      "var(--step-1)",
      "var(--step-2)",
      "var(--step-4)",
      "var(--step-5)",
    ]);
    expect([0, 1, 2].map((score) => scoreColor(score as 0 | 1 | 2))).toEqual([
      "var(--step-1)",
      "var(--step-3)",
      "var(--step-5)",
    ]);
  });

  it("gives what is not judged yet the neutral colour and has none for an unordered name", () => {
    expect(orderedColor("not_judged_yet")).toBe(NOT_JUDGED_COLOR);
    expect(orderedColor("design")).toBeUndefined();
  });

  it("keeps the chart chrome apart from the data colours", () => {
    expect(new Set([CHART_AXIS_COLOR, CHART_GRID_COLOR, CHART_REFERENCE_COLOR, CHART_SURFACE_COLOR]).size).toBe(4);
  });
});
