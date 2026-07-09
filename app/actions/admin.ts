"use server";

// Thin Server Action wrappers around lib/admin logic. Each gates on
// requireRole("manager") then binds an RLS-scoped client to the manager's
// session — RLS still independently rejects any non-manager. Writes revalidate
// /admin (and /org, whose overview reflects teams/work-types/technologies).
import { revalidatePath } from "next/cache";
import { requireRole } from "@/lib/auth";
import { createServerSupabase } from "@/lib/supabase/server";
import {
  addMemberToTeam,
  assignLead,
  createTeam,
  inviteMember,
  mergeTechnologies,
  removeLead,
  removeMemberFromTeam,
  upsertTechnology,
  upsertWorkType,
  type TeamRow,
} from "@/lib/admin";
import type { AppRole, Technology, WorkType } from "@/lib/types";

function revalidateAdmin() {
  revalidatePath("/admin");
  revalidatePath("/org");
}

export async function inviteMemberAction(
  email: string,
  role: AppRole,
  teamId: string | null
): Promise<void> {
  await requireRole("manager");
  const sb = await createServerSupabase();
  await inviteMember(sb, email, role, teamId);
  revalidateAdmin();
}

export async function createTeamAction(name: string): Promise<TeamRow> {
  await requireRole("manager");
  const sb = await createServerSupabase();
  const team = await createTeam(sb, name);
  revalidateAdmin();
  return team;
}

export async function assignLeadAction(
  teamId: string,
  leadId: string
): Promise<void> {
  await requireRole("manager");
  const sb = await createServerSupabase();
  await assignLead(sb, teamId, leadId);
  revalidateAdmin();
}

export async function removeLeadAction(
  teamId: string,
  leadId: string
): Promise<void> {
  await requireRole("manager");
  const sb = await createServerSupabase();
  await removeLead(sb, teamId, leadId);
  revalidateAdmin();
}

export async function addMemberToTeamAction(
  teamId: string,
  devId: string
): Promise<void> {
  await requireRole("manager");
  const sb = await createServerSupabase();
  await addMemberToTeam(sb, teamId, devId);
  revalidateAdmin();
}

export async function removeMemberFromTeamAction(
  teamId: string,
  devId: string
): Promise<void> {
  await requireRole("manager");
  const sb = await createServerSupabase();
  await removeMemberFromTeam(sb, teamId, devId);
  revalidateAdmin();
}

export async function upsertWorkTypeAction(input: {
  id?: string;
  name: string;
  color: string;
  sort_order: number;
  active: boolean;
}): Promise<WorkType> {
  await requireRole("manager");
  const sb = await createServerSupabase();
  const wt = await upsertWorkType(sb, input);
  revalidateAdmin();
  revalidatePath("/today");
  return wt;
}

export async function upsertTechnologyAction(input: {
  id?: string;
  name: string;
  color: string;
  active: boolean;
}): Promise<Technology> {
  await requireRole("manager");
  const sb = await createServerSupabase();
  const tech = await upsertTechnology(sb, input);
  revalidateAdmin();
  revalidatePath("/today");
  return tech;
}

export async function mergeTechnologiesAction(
  fromId: string,
  intoId: string
): Promise<void> {
  await requireRole("manager");
  const sb = await createServerSupabase();
  await mergeTechnologies(sb, fromId, intoId);
  revalidateAdmin();
  revalidatePath("/today");
}
