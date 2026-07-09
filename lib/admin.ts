// Manager-only admin domain logic: the invite allowlist, teams + lead/member
// assignment, work types, and the shared technology list. Every function takes
// an RLS-scoped Supabase client (the manager's cookie session in a Server
// Action, a signed-in anon client in tests). RLS is the real gate — the
// manager-only policies (ae_mgr, teams_mgr, tm_mgr, tl_mgr, wt_mgr, tech_*,
// lit_mgr_all) reject anyone who is not a manager, so no service-role key is
// needed. The thin "use server" wrappers live in app/actions/admin.ts and add a
// requireRole("manager") gate on top for defense in depth.
import type { SupabaseClient } from "@supabase/supabase-js";
import type { AppRole, Technology, WorkType } from "./types";

async function actingUserId(sb: SupabaseClient): Promise<string> {
  const { data, error } = await sb.auth.getUser();
  if (error) throw error;
  const id = data.user?.id;
  if (!id) throw new Error("no authenticated user");
  return id;
}

// Add (or re-invite) an email to the allowlist. The provisioning trigger reads
// this on first Google sign-in to create the profile with the right role/team.
export async function inviteMember(
  sb: SupabaseClient,
  email: string,
  role: AppRole,
  teamId: string | null
): Promise<void> {
  const { error } = await sb
    .from("allowed_emails")
    .upsert(
      { email: email.trim().toLowerCase(), role, team_id: teamId, consumed: false },
      { onConflict: "email" }
    );
  if (error) throw error;
}

export interface TeamRow {
  id: string;
  name: string;
}

export async function createTeam(
  sb: SupabaseClient,
  name: string
): Promise<TeamRow> {
  const { data, error } = await sb
    .from("teams")
    .insert({ name: name.trim() })
    .select("id, name")
    .single();
  if (error) throw error;
  return data as TeamRow;
}

export async function assignLead(
  sb: SupabaseClient,
  teamId: string,
  leadId: string
): Promise<void> {
  const { error } = await sb
    .from("team_leads")
    .upsert({ team_id: teamId, lead_id: leadId }, { onConflict: "team_id,lead_id" });
  if (error) throw error;
}

export async function removeLead(
  sb: SupabaseClient,
  teamId: string,
  leadId: string
): Promise<void> {
  const { error } = await sb
    .from("team_leads")
    .delete()
    .eq("team_id", teamId)
    .eq("lead_id", leadId);
  if (error) throw error;
}

export async function addMemberToTeam(
  sb: SupabaseClient,
  teamId: string,
  devId: string
): Promise<void> {
  const { error } = await sb
    .from("team_members")
    .upsert(
      { team_id: teamId, developer_id: devId },
      { onConflict: "team_id,developer_id" }
    );
  if (error) throw error;
}

export async function removeMemberFromTeam(
  sb: SupabaseClient,
  teamId: string,
  devId: string
): Promise<void> {
  const { error } = await sb
    .from("team_members")
    .delete()
    .eq("team_id", teamId)
    .eq("developer_id", devId);
  if (error) throw error;
}

// Insert (no id) or update (with id) a work-type category.
export async function upsertWorkType(
  sb: SupabaseClient,
  input: {
    id?: string;
    name: string;
    color: string;
    sort_order: number;
    active: boolean;
  }
): Promise<WorkType> {
  const row = {
    name: input.name.trim(),
    color: input.color,
    sort_order: input.sort_order,
    active: input.active,
  };
  const q = input.id
    ? sb.from("work_types").update(row).eq("id", input.id)
    : sb.from("work_types").insert(row);
  const { data, error } = await q.select("*").single();
  if (error) throw error;
  return data as WorkType;
}

// Insert (no id) or update (with id) a technology. On insert the tech_insert RLS
// policy requires created_by = auth.uid(), so it is pinned to the acting user.
export async function upsertTechnology(
  sb: SupabaseClient,
  input: { id?: string; name: string; color: string; active: boolean }
): Promise<Technology> {
  if (input.id) {
    const { data, error } = await sb
      .from("technologies")
      .update({ name: input.name.trim(), color: input.color, active: input.active })
      .eq("id", input.id)
      .select("*")
      .single();
    if (error) throw error;
    return data as Technology;
  }
  const created_by = await actingUserId(sb);
  const { data, error } = await sb
    .from("technologies")
    .insert({
      name: input.name.trim(),
      color: input.color,
      active: input.active,
      created_by,
    })
    .select("*")
    .single();
  if (error) throw error;
  return data as Technology;
}

// Fold a duplicate technology (`fromId`) into `intoId`: repoint every task that
// used the duplicate, then deactivate it (kept, not deleted, to preserve any
// history). Repointing is insert-then-delete so a task already tagged with both
// technologies doesn't hit the (log_item_id, technology_id) primary key.
export async function mergeTechnologies(
  sb: SupabaseClient,
  fromId: string,
  intoId: string
): Promise<void> {
  if (fromId === intoId) return;

  const { data: links, error: readErr } = await sb
    .from("log_item_technologies")
    .select("log_item_id")
    .eq("technology_id", fromId);
  if (readErr) throw readErr;

  const repointed = (links ?? []).map((l) => ({
    log_item_id: (l as { log_item_id: string }).log_item_id,
    technology_id: intoId,
  }));
  if (repointed.length > 0) {
    const { error: insErr } = await sb
      .from("log_item_technologies")
      .upsert(repointed, {
        onConflict: "log_item_id,technology_id",
        ignoreDuplicates: true,
      });
    if (insErr) throw insErr;
  }

  const { error: delErr } = await sb
    .from("log_item_technologies")
    .delete()
    .eq("technology_id", fromId);
  if (delErr) throw delErr;

  const { error: deactErr } = await sb
    .from("technologies")
    .update({ active: false })
    .eq("id", fromId);
  if (deactErr) throw deactErr;
}
