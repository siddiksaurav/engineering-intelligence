import type { SupabaseClient } from "@supabase/supabase-js";
import { admin, asUser, makeUser, firstWorkType } from "./session";
import { addLogItem, getOrCreateTodayLog, submitDay } from "@/lib/logs";

// Unique-per-run identifiers so the suite is rerunnable without a db reset
// (auth.admin.createUser and teams.name both reject duplicates).
let counter = 0;
function uniq(prefix: string): string {
  counter += 1;
  return `${prefix}-${Math.random().toString(36).slice(2, 8)}-${counter}`;
}

export async function createTeam(name: string): Promise<string> {
  const { data, error } = await admin
    .from("teams")
    .insert({ name })
    .select("id")
    .single();
  if (error) throw error;
  return data!.id as string;
}

export async function addTeamMember(teamId: string, devId: string): Promise<void> {
  const { error } = await admin
    .from("team_members")
    .insert({ team_id: teamId, developer_id: devId });
  if (error) throw error;
}

export async function addTeamLead(teamId: string, leadId: string): Promise<void> {
  const { error } = await admin
    .from("team_leads")
    .insert({ team_id: teamId, lead_id: leadId });
  if (error) throw error;
}

export interface TeamFixture {
  teamId: string;
  devId: string;
  leadId: string;
  devEmail: string;
  leadEmail: string;
  devSb: SupabaseClient;
  leadSb: SupabaseClient;
  logId: string;
}

// A team with one developer, one lead, and a submitted day carrying a single
// blocked task — the common starting point for approval/notes/blocked tests.
export async function setupTeamWithSubmittedLog(): Promise<TeamFixture> {
  const teamId = await createTeam(uniq("team"));
  const devEmail = `${uniq("dev")}@x.com`;
  const leadEmail = `${uniq("lead")}@x.com`;
  const devId = await makeUser(devEmail, "developer");
  const leadId = await makeUser(leadEmail, "lead");
  await addTeamMember(teamId, devId);
  await addTeamLead(teamId, leadId);

  const devSb = await asUser(devEmail);
  const leadSb = await asUser(leadEmail);

  const log = await getOrCreateTodayLog(devSb, devId);
  const wt = await firstWorkType(devSb);
  await addLogItem(devSb, {
    dailyLogId: log.id,
    workTypeId: wt,
    status: "blocked",
    description: "wire up the ingestion consumer",
    hours: 2,
    blockerNote: "needs prod Kafka credentials",
    technologyIds: [],
  });
  await submitDay(devSb, log.id);

  return { teamId, devId, leadId, devEmail, leadEmail, devSb, leadSb, logId: log.id };
}

// A lead of an unrelated team — used to prove cross-team isolation.
export async function makeLeadWithTeam(prefix: string): Promise<{
  teamId: string;
  leadId: string;
  leadEmail: string;
  leadSb: SupabaseClient;
}> {
  const teamId = await createTeam(uniq(`${prefix}-team`));
  const leadEmail = `${uniq(`${prefix}-lead`)}@x.com`;
  const leadId = await makeUser(leadEmail, "lead");
  await addTeamLead(teamId, leadId);
  const leadSb = await asUser(leadEmail);
  return { teamId, leadId, leadEmail, leadSb };
}
