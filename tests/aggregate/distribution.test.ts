import { buildDistribution } from "@/lib/aggregate";

test("percentages sum to ~100", () => {
  const d = buildDistribution([
    { key: "a", name: "Feature", color: "#111", hours: 3 },
    { key: "b", name: "Bug Fix", color: "#222", hours: 1 },
  ]);
  expect(Math.round(d.reduce((s, x) => s + x.pct, 0))).toBe(100);
});

test("groups by key and sums hours per group", () => {
  const d = buildDistribution([
    { key: "a", name: "Feature", color: "#111", hours: 3 },
    { key: "a", name: "Feature", color: "#111", hours: 1 },
    { key: "b", name: "Bug Fix", color: "#222", hours: 4 },
  ]);
  expect(d).toHaveLength(2);
  const feature = d.find((x) => x.name === "Feature")!;
  expect(feature.total).toBe(4);
  expect(feature.pct).toBe(50);
});

test("counts each item as 1 when hours are null", () => {
  const d = buildDistribution([
    { key: "a", name: "Feature", color: "#111", hours: null },
    { key: "a", name: "Feature", color: "#111", hours: null },
    { key: "b", name: "Bug Fix", color: "#222", hours: null },
  ]);
  const feature = d.find((x) => x.name === "Feature")!;
  expect(feature.total).toBe(2);
  expect(Math.round(d.reduce((s, x) => s + x.pct, 0))).toBe(100);
});

test("sorts slices by total descending", () => {
  const d = buildDistribution([
    { key: "a", name: "Small", color: "#111", hours: 1 },
    { key: "b", name: "Big", color: "#222", hours: 9 },
  ]);
  expect(d.map((x) => x.name)).toEqual(["Big", "Small"]);
});

test("empty input yields empty output", () => {
  expect(buildDistribution([])).toEqual([]);
});
