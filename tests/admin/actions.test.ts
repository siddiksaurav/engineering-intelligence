import {
  inviteMember,
  createTeam,
  upsertWorkType,
  upsertTechnology,
  mergeTechnologies,
} from "@/lib/admin";
import { admin, asUser, makeUser } from "../util/session";

function uniq(prefix: string): string {
  return `${prefix}-${Math.random().toString(36).slice(2, 8)}`;
}

test("manager can invite a member; a lead is blocked by RLS", async () => {
  const mgrSb = await (async () => {
    const email = `${uniq("mgr")}@x.com`;
    await makeUser(email, "manager");
    return asUser(email);
  })();

  const invitee = `${uniq("invitee")}@x.com`;
  await inviteMember(mgrSb, invitee, "developer", null);

  const { data: rows } = await admin
    .from("allowed_emails")
    .select("email, role")
    .eq("email", invitee);
  expect(rows?.length).toBe(1);
  expect(rows![0].role).toBe("developer");

  // A lead has no manager rights: RLS rejects the insert, nothing is written.
  const leadEmail = `${uniq("lead")}@x.com`;
  await makeUser(leadEmail, "lead");
  const leadSb = await asUser(leadEmail);
  const blocked = `${uniq("blocked")}@x.com`;
  await expect(
    inviteMember(leadSb, blocked, "developer", null)
  ).rejects.toBeTruthy();

  const { data: none } = await admin
    .from("allowed_emails")
    .select("email")
    .eq("email", blocked);
  expect(none?.length ?? 0).toBe(0);
});

test("manager creates a team, work type, technology, and merges duplicates", async () => {
  const email = `${uniq("mgr")}@x.com`;
  await makeUser(email, "manager");
  const sb = await asUser(email);
  const devEmail = `${uniq("dev")}@x.com`;
  const devId = await makeUser(devEmail, "developer");
  const devSb = await asUser(devEmail);

  const team = await createTeam(sb, uniq("team"));
  expect(team.id).toBeTruthy();

  const wt = await upsertWorkType(sb, {
    name: uniq("Spike"),
    color: "#123456",
    sort_order: 99,
    active: true,
  });
  expect(wt.name).toContain("Spike");

  // Two near-duplicate technologies, one used by a task, then merged.
  const keep = await upsertTechnology(sb, {
    name: uniq("Kafka"),
    color: "#64748b",
    active: true,
  });
  const dupe = await upsertTechnology(sb, {
    name: uniq("kafka"),
    color: "#64748b",
    active: true,
  });

  const { data: log } = await devSb
    .from("daily_logs")
    .insert({ developer_id: devId, log_date: "2026-02-01" })
    .select("id")
    .single();
  const { data: item } = await devSb
    .from("log_items")
    .insert({ daily_log_id: log!.id, work_type_id: wt.id, description: "x" })
    .select("id")
    .single();
  await devSb
    .from("log_item_technologies")
    .insert({ log_item_id: item!.id, technology_id: dupe.id });

  await mergeTechnologies(sb, dupe.id, keep.id);

  // The task's tech link now points at the kept technology…
  const { data: links } = await admin
    .from("log_item_technologies")
    .select("technology_id")
    .eq("log_item_id", item!.id);
  expect(links?.map((l) => l.technology_id)).toEqual([keep.id]);

  // …and the duplicate is deactivated, not deleted.
  const { data: dupeRow } = await admin
    .from("technologies")
    .select("active")
    .eq("id", dupe.id)
    .single();
  expect(dupeRow!.active).toBe(false);
});
