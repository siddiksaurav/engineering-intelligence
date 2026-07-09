import { buildCalendar } from "@/lib/aggregate";

test("assigns intensity levels and fills gaps", () => {
  const cells = buildCalendar(
    [{ log_date: "2026-01-02", count: 5 }],
    "2026-01-01",
    "2026-01-03"
  );
  expect(cells).toHaveLength(3);
  expect(cells.find((c) => c.date === "2026-01-01")!.level).toBe(0);
  expect(cells.find((c) => c.date === "2026-01-02")!.level).toBeGreaterThan(0);
});

test("fixed thresholds map counts to levels 0..4", () => {
  const cells = buildCalendar(
    [
      { log_date: "2026-01-01", count: 0 },
      { log_date: "2026-01-02", count: 1 },
      { log_date: "2026-01-03", count: 2 },
      { log_date: "2026-01-04", count: 3 },
      { log_date: "2026-01-05", count: 4 },
      { log_date: "2026-01-06", count: 5 },
      { log_date: "2026-01-07", count: 7 },
      { log_date: "2026-01-08", count: 8 },
      { log_date: "2026-01-09", count: 20 },
    ],
    "2026-01-01",
    "2026-01-09"
  );
  const levelFor = (d: string) => cells.find((c) => c.date === d)!.level;
  expect(levelFor("2026-01-01")).toBe(0); // 0
  expect(levelFor("2026-01-02")).toBe(1); // 1-2
  expect(levelFor("2026-01-03")).toBe(1);
  expect(levelFor("2026-01-04")).toBe(2); // 3-4
  expect(levelFor("2026-01-05")).toBe(2);
  expect(levelFor("2026-01-06")).toBe(3); // 5-7
  expect(levelFor("2026-01-07")).toBe(3);
  expect(levelFor("2026-01-08")).toBe(4); // 8+
  expect(levelFor("2026-01-09")).toBe(4);
});

test("returns cells in ascending date order across a month boundary", () => {
  const cells = buildCalendar([], "2026-01-30", "2026-02-02");
  expect(cells.map((c) => c.date)).toEqual([
    "2026-01-30",
    "2026-01-31",
    "2026-02-01",
    "2026-02-02",
  ]);
  expect(cells.every((c) => c.count === 0 && c.level === 0)).toBe(true);
});
