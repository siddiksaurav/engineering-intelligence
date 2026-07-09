import {
  getActivity,
  dayCounts,
  techDistRows,
  workTypeDistRows,
} from "@/lib/queries";
import { buildCalendar, buildDistribution } from "@/lib/aggregate";
import { admin, devSession, me, firstWorkType, today } from "../util/session";

// Exercises the nested-embed getActivity query against real Postgres (the pure
// aggregate tests can't), then runs it through the shapers + aggregators the
// dashboards use.
test("getActivity returns task-level rows that feed heatmap + distributions", async () => {
  const sb = await devSession("dev-activity@x.com");
  const devId = await me(sb);
  const wt = await firstWorkType(sb);

  // Two technologies to tag the task with.
  const { data: techs } = await admin
    .from("technologies")
    .select("id, name")
    .limit(2);
  const [t1, t2] = techs!;

  const { data: log } = await sb
    .from("daily_logs")
    .insert({ developer_id: devId, log_date: today() })
    .select()
    .single();

  const { data: item } = await sb
    .from("log_items")
    .insert({
      daily_log_id: log!.id,
      work_type_id: wt,
      status: "in_progress",
      description: "built the thing",
      hours: 3,
    })
    .select()
    .single();

  await sb.from("log_item_technologies").insert([
    { log_item_id: item!.id, technology_id: t1.id },
    { log_item_id: item!.id, technology_id: t2.id },
  ]);

  const activity = await getActivity(sb, {
    from: today(),
    to: today(),
    developerId: devId,
  });

  expect(activity).toHaveLength(1);
  expect(activity[0].work_type_id).toBe(wt);
  expect(activity[0].hours).toBe(3);
  expect(activity[0].technologies.map((t) => t.id).sort()).toEqual(
    [t1.id, t2.id].sort()
  );

  // Heatmap: today's single day has 1 task → a non-zero level.
  const cells = buildCalendar(dayCounts(activity), today(), today());
  expect(cells).toHaveLength(1);
  expect(cells[0].level).toBeGreaterThan(0);

  // Category distribution: one work type → 100%.
  const wtDist = buildDistribution(workTypeDistRows(activity));
  expect(wtDist).toHaveLength(1);
  expect(Math.round(wtDist[0].pct)).toBe(100);

  // Tech distribution: two techs, one row per task-tech pair → 50/50.
  const techDist = buildDistribution(techDistRows(activity));
  expect(techDist).toHaveLength(2);
  expect(techDist.map((d) => Math.round(d.pct))).toEqual([50, 50]);
});
