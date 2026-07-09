import { admin } from "../util/session";
import { setupTeamWithSubmittedLog, makeLeadWithTeam } from "../util/team";
import { approveDay, reopenForDev } from "@/lib/team";

test("lead approves a submitted day in own team; approved day is locked to dev", async () => {
  const { leadSb, devSb, leadId, logId } = await setupTeamWithSubmittedLog();

  await approveDay(leadSb, logId);

  const { data } = await leadSb
    .from("daily_logs")
    .select("status, approved_by, approved_at")
    .eq("id", logId)
    .single();
  expect(data!.status).toBe("approved");
  expect(data!.approved_by).toBe(leadId);
  expect(data!.approved_at).not.toBeNull();

  // The developer can no longer edit an approved day (RLS dl_dev_update).
  const res = await devSb
    .from("daily_logs")
    .update({ status: "draft" })
    .eq("id", logId)
    .select();
  expect(res.data?.length ?? 0).toBe(0);
});

test("lead reopens a day back to draft and clears approval", async () => {
  const { leadSb, logId } = await setupTeamWithSubmittedLog();
  await approveDay(leadSb, logId);

  await reopenForDev(leadSb, logId);

  const { data } = await leadSb
    .from("daily_logs")
    .select("status, approved_by, approved_at")
    .eq("id", logId)
    .single();
  expect(data!.status).toBe("draft");
  expect(data!.approved_by).toBeNull();
  expect(data!.approved_at).toBeNull();
});

test("a lead of a different team cannot approve another team's day", async () => {
  const { logId } = await setupTeamWithSubmittedLog();
  const other = await makeLeadWithTeam("outsider");

  // RLS scopes the update to 0 rows — no error, but no change either.
  await approveDay(other.leadSb, logId);

  const { data } = await admin
    .from("daily_logs")
    .select("status")
    .eq("id", logId)
    .single();
  expect(data!.status).toBe("submitted");
});
