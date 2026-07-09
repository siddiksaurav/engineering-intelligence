import { admin, devSession, me, firstWorkType } from "../util/session";
import {
  getOrCreateTodayLog,
  addLogItem,
  submitDay,
  reopenIfLocked,
  createTechnology,
  setItemTechnologies,
  getLogWithItems,
} from "@/lib/logs";

test("getOrCreateTodayLog + addLogItem returns a task carrying its technologies", async () => {
  const sb = await devSession("dev-log-a@x.com");
  const devId = await me(sb);
  const log = await getOrCreateTodayLog(sb, devId);
  const wt = await firstWorkType(sb);

  const kafka = await createTechnology(sb, "Apache Kafka"); // existing seed → returns it
  const spring = await createTechnology(sb, "Spring Boot");

  const item = await addLogItem(sb, {
    dailyLogId: log.id,
    workTypeId: wt,
    status: "in_progress",
    description: "built the ingestion pipeline",
    hours: 3,
    blockerNote: null,
    technologyIds: [kafka.id, spring.id],
  });

  expect(item.description).toBe("built the ingestion pipeline");
  expect(item.hours).toBe(3);
  expect(item.technology_ids.sort()).toEqual([kafka.id, spring.id].sort());
});

test("createTechnology adds a brand-new tech and is idempotent on the shared name", async () => {
  const sb = await devSession("dev-log-b@x.com");
  const first = await createTechnology(sb, "Elixir");
  const again = await createTechnology(sb, "Elixir");
  expect(first.id).toBe(again.id); // shared list, no duplicate row
  expect(first.name).toBe("Elixir");
});

test("setItemTechnologies diffs rows (adds new, drops removed)", async () => {
  const sb = await devSession("dev-log-c@x.com");
  const devId = await me(sb);
  const log = await getOrCreateTodayLog(sb, devId);
  const wt = await firstWorkType(sb);
  const react = await createTechnology(sb, "React");
  const next = await createTechnology(sb, "Next.js");
  const ts = await createTechnology(sb, "TypeScript");

  const item = await addLogItem(sb, {
    dailyLogId: log.id,
    workTypeId: wt,
    status: "todo",
    description: "ui work",
    hours: null,
    blockerNote: null,
    technologyIds: [react.id],
  });

  await setItemTechnologies(sb, item.id, [next.id, ts.id]); // drop react, add next + ts

  const { items } = await getLogWithItems(sb, log.id);
  const reloaded = items.find((i) => i.id === item.id)!;
  expect(reloaded.technology_ids.sort()).toEqual([next.id, ts.id].sort());
});

test("submitDay locks the day; reopenIfLocked resets to draft and CLEARS approval", async () => {
  const sb = await devSession("dev-log-d@x.com");
  const devId = await me(sb);
  const log = await getOrCreateTodayLog(sb, devId);
  const wt = await firstWorkType(sb);
  await addLogItem(sb, {
    dailyLogId: log.id,
    workTypeId: wt,
    status: "done",
    description: "shipped X",
    hours: 2,
    blockerNote: null,
    technologyIds: [],
  });

  await submitDay(sb, log.id);
  const submitted = await sb
    .from("daily_logs")
    .select("status")
    .eq("id", log.id)
    .single();
  expect(submitted.data!.status).toBe("submitted");

  // Plant approval metadata via admin so the "clears approval" assertion is real.
  await admin
    .from("daily_logs")
    .update({ approved_by: devId, approved_at: new Date().toISOString() })
    .eq("id", log.id);

  // The real reopen logic used by the edit path.
  await reopenIfLocked(sb, log.id);

  const after = await sb
    .from("daily_logs")
    .select("status, approved_by, approved_at")
    .eq("id", log.id)
    .single();
  expect(after.data!.status).toBe("draft");
  expect(after.data!.approved_by).toBeNull();
  expect(after.data!.approved_at).toBeNull();
});
