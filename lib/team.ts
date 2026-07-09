// Lead/manager domain logic: day approvals and private per-developer notes.
// Every function takes an RLS-scoped Supabase client so it runs either inside a
// Server Action (the user's cookie session) or in tests (a signed-in anon
// client). RLS confines a lead to their own teams; a manager sees everything.
// No "use server" here — the thin action wrappers live in app/actions/*.
import type { SupabaseClient } from "@supabase/supabase-js";
import type { DevNote } from "./types";

// Resolve the acting user from the session. approved_by / author_id must be the
// real actor, never a client-supplied argument.
async function actingUserId(sb: SupabaseClient): Promise<string> {
  const { data, error } = await sb.auth.getUser();
  if (error) throw error;
  const id = data.user?.id;
  if (!id) throw new Error("no authenticated user");
  return id;
}

// Lock a submitted day: mark approved and stamp the approver. RLS (dl_lead_update
// / dl_mgr_all) scopes the update to days the caller may approve; a lead outside
// the team matches 0 rows and silently changes nothing.
export async function approveDay(sb: SupabaseClient, logId: string): Promise<void> {
  const approver = await actingUserId(sb);
  const { error } = await sb
    .from("daily_logs")
    .update({
      status: "approved",
      approved_by: approver,
      approved_at: new Date().toISOString(),
    })
    .eq("id", logId);
  if (error) throw error;
}

// Send a day back to the developer for edits, clearing any approval metadata.
export async function reopenForDev(sb: SupabaseClient, logId: string): Promise<void> {
  const { error } = await sb
    .from("daily_logs")
    .update({ status: "draft", approved_by: null, approved_at: null })
    .eq("id", logId);
  if (error) throw error;
}

// Add a private note about a developer. author_id is pinned to the session; the
// notes_access policy also enforces the caller is a lead-of/manager at write time.
export async function addNote(
  sb: SupabaseClient,
  developerId: string,
  body: string
): Promise<DevNote> {
  const author_id = await actingUserId(sb);
  const { data, error } = await sb
    .from("dev_notes")
    .insert({ developer_id: developerId, author_id, body: body.trim() })
    .select("*")
    .single();
  if (error) throw error;
  return data as DevNote;
}

export async function updateNote(
  sb: SupabaseClient,
  id: string,
  body: string
): Promise<void> {
  const { error } = await sb
    .from("dev_notes")
    .update({ body: body.trim(), updated_at: new Date().toISOString() })
    .eq("id", id);
  if (error) throw error;
}

export async function deleteNote(sb: SupabaseClient, id: string): Promise<void> {
  const { error } = await sb.from("dev_notes").delete().eq("id", id);
  if (error) throw error;
}

// A developer's notes, newest first. RLS returns rows only to a lead-of/manager,
// never the developer the notes are about.
export async function getDevNotes(
  sb: SupabaseClient,
  devId: string
): Promise<DevNote[]> {
  const { data, error } = await sb
    .from("dev_notes")
    .select("*")
    .eq("developer_id", devId)
    .order("created_at", { ascending: false });
  if (error) throw error;
  return (data ?? []) as DevNote[];
}
