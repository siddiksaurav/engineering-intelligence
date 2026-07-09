"use server";

// Thin Server Action wrappers around lib/team note logic. Private per-developer
// notes are readable/writable only by a lead-of-team or manager (RLS
// notes_access) — never by the developer the notes are about.
import { revalidatePath } from "next/cache";
import { requireRole } from "@/lib/auth";
import { createServerSupabase } from "@/lib/supabase/server";
import { addNote, deleteNote, getDevNotes, updateNote } from "@/lib/team";
import type { DevNote } from "@/lib/types";

export async function listNotesAction(developerId: string): Promise<DevNote[]> {
  await requireRole("lead", "manager");
  const sb = await createServerSupabase();
  return getDevNotes(sb, developerId);
}

export async function addNoteAction(
  developerId: string,
  body: string
): Promise<DevNote> {
  await requireRole("lead", "manager");
  const sb = await createServerSupabase();
  const note = await addNote(sb, developerId, body);
  revalidatePath("/team");
  return note;
}

export async function updateNoteAction(id: string, body: string): Promise<void> {
  await requireRole("lead", "manager");
  const sb = await createServerSupabase();
  await updateNote(sb, id, body);
  revalidatePath("/team");
}

export async function deleteNoteAction(id: string): Promise<void> {
  await requireRole("lead", "manager");
  const sb = await createServerSupabase();
  await deleteNote(sb, id);
  revalidatePath("/team");
}
