// Log-domain logic. Every function takes an RLS-scoped Supabase client so it can
// run either inside a Server Action (with the user's cookie session) or in tests
// (with a password-signed anon client). No "use server" here — these are plain
// functions; the thin action wrappers live in app/actions/logs.ts.
import type { SupabaseClient } from "@supabase/supabase-js";
import type {
  DailyLog,
  ItemStatus,
  LogItem,
  LogItemWithTech,
  Technology,
} from "./types";

// Local-date YYYY-MM-DD (the developer's "today", not UTC).
export function todayISO(): string {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

type ItemRow = LogItem & {
  log_item_technologies: { technology_id: string }[] | null;
};

function toWithTech(row: ItemRow): LogItemWithTech {
  const { log_item_technologies, ...rest } = row;
  return {
    ...rest,
    technology_ids: (log_item_technologies ?? []).map((t) => t.technology_id),
  };
}

export async function getOrCreateTodayLog(
  sb: SupabaseClient,
  devId: string
): Promise<DailyLog> {
  const log_date = todayISO();
  const { data: existing, error: readErr } = await sb
    .from("daily_logs")
    .select("*")
    .eq("developer_id", devId)
    .eq("log_date", log_date)
    .maybeSingle();
  if (readErr) throw readErr;
  if (existing) return existing as DailyLog;

  const { data, error } = await sb
    .from("daily_logs")
    .insert({ developer_id: devId, log_date })
    .select("*")
    .single();
  if (error) throw error;
  return data as DailyLog;
}

export async function getLogWithItems(
  sb: SupabaseClient,
  logId: string
): Promise<{ log: DailyLog; items: LogItemWithTech[] }> {
  const { data: log, error: logErr } = await sb
    .from("daily_logs")
    .select("*")
    .eq("id", logId)
    .single();
  if (logErr) throw logErr;

  const { data: rows, error: itemsErr } = await sb
    .from("log_items")
    .select("*, log_item_technologies(technology_id)")
    .eq("daily_log_id", logId)
    .order("sort_order", { ascending: true });
  if (itemsErr) throw itemsErr;

  return {
    log: log as DailyLog,
    items: (rows as ItemRow[]).map(toWithTech),
  };
}

// Reopen a submitted/approved day back to draft before an edit, clearing any
// approval metadata. Kept in app code (not a DB trigger) so the reopen is an
// explicit, auditable step. No-op when already draft. RLS confines a developer
// to their own not-yet-approved days; a lead/manager client can reopen approved.
export async function reopenIfLocked(sb: SupabaseClient, logId: string) {
  const { error } = await sb
    .from("daily_logs")
    .update({ status: "draft", approved_by: null, approved_at: null })
    .eq("id", logId)
    .neq("status", "draft");
  if (error) throw error;
}

export interface AddItemInput {
  dailyLogId: string;
  workTypeId: string;
  status: ItemStatus;
  description: string;
  hours: number | null;
  blockerNote: string | null;
  technologyIds: string[];
}

export async function addLogItem(
  sb: SupabaseClient,
  input: AddItemInput
): Promise<LogItemWithTech> {
  await reopenIfLocked(sb, input.dailyLogId);

  // Append after the current last task.
  const { data: last } = await sb
    .from("log_items")
    .select("sort_order")
    .eq("daily_log_id", input.dailyLogId)
    .order("sort_order", { ascending: false })
    .limit(1)
    .maybeSingle();
  const sort_order = (last?.sort_order ?? -1) + 1;

  const { data: item, error } = await sb
    .from("log_items")
    .insert({
      daily_log_id: input.dailyLogId,
      work_type_id: input.workTypeId,
      status: input.status,
      description: input.description,
      hours: input.hours,
      blocker_note: input.blockerNote,
      sort_order,
    })
    .select("*")
    .single();
  if (error) throw error;

  if (input.technologyIds.length) {
    await setItemTechnologies(sb, (item as LogItem).id, input.technologyIds);
  }

  return { ...(item as LogItem), technology_ids: input.technologyIds };
}

export type ItemPatch = Partial<
  Pick<
    LogItem,
    "work_type_id" | "status" | "description" | "hours" | "blocker_note"
  >
>;

export async function updateLogItem(
  sb: SupabaseClient,
  id: string,
  patch: ItemPatch
): Promise<void> {
  // Reopen the parent day first, then edit.
  const { data: item, error: readErr } = await sb
    .from("log_items")
    .select("daily_log_id")
    .eq("id", id)
    .single();
  if (readErr) throw readErr;
  await reopenIfLocked(sb, (item as { daily_log_id: string }).daily_log_id);

  const { error } = await sb.from("log_items").update(patch).eq("id", id);
  if (error) throw error;
}

// Diff the join table: insert missing pairs, delete removed ones.
export async function setItemTechnologies(
  sb: SupabaseClient,
  itemId: string,
  technologyIds: string[]
): Promise<void> {
  const { data: existingRows, error: readErr } = await sb
    .from("log_item_technologies")
    .select("technology_id")
    .eq("log_item_id", itemId);
  if (readErr) throw readErr;

  const existing = new Set(
    (existingRows ?? []).map((r) => r.technology_id as string)
  );
  const next = new Set(technologyIds);

  const toAdd = technologyIds.filter((id) => !existing.has(id));
  const toRemove = [...existing].filter((id) => !next.has(id));

  if (toAdd.length) {
    const { error } = await sb
      .from("log_item_technologies")
      .insert(toAdd.map((technology_id) => ({ log_item_id: itemId, technology_id })));
    if (error) throw error;
  }
  if (toRemove.length) {
    const { error } = await sb
      .from("log_item_technologies")
      .delete()
      .eq("log_item_id", itemId)
      .in("technology_id", toRemove);
    if (error) throw error;
  }
}

// Add a technology to the shared list, or return the existing row on name
// conflict. RLS (tech_insert) requires created_by = auth.uid(), so when the
// caller doesn't pass it we resolve the acting user from the session.
export async function createTechnology(
  sb: SupabaseClient,
  name: string,
  createdBy?: string
): Promise<Technology> {
  const trimmed = name.trim();
  let actor = createdBy;
  if (!actor) {
    const { data: auth } = await sb.auth.getUser();
    actor = auth.user?.id;
  }
  const insert: Record<string, unknown> = { name: trimmed, created_by: actor };

  const { data, error } = await sb
    .from("technologies")
    .insert(insert)
    .select("*")
    .single();
  if (!error) return data as Technology;

  // Unique-name conflict → return the shared row.
  const { data: existing, error: readErr } = await sb
    .from("technologies")
    .select("*")
    .eq("name", trimmed)
    .single();
  if (readErr) throw error; // surface the original insert error if lookup fails too
  return existing as Technology;
}

export async function deleteLogItem(
  sb: SupabaseClient,
  id: string
): Promise<void> {
  const { data: item, error: readErr } = await sb
    .from("log_items")
    .select("daily_log_id")
    .eq("id", id)
    .single();
  if (readErr) throw readErr;
  await reopenIfLocked(sb, (item as { daily_log_id: string }).daily_log_id);

  const { error } = await sb.from("log_items").delete().eq("id", id);
  if (error) throw error;
}

export async function submitDay(
  sb: SupabaseClient,
  logId: string
): Promise<void> {
  const { error } = await sb
    .from("daily_logs")
    .update({ status: "submitted" })
    .eq("id", logId);
  if (error) throw error;
}
