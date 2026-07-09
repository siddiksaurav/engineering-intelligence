"use server";

// Thin Server Action wrappers around lib/logs. Each authenticates (requireRole),
// binds an RLS-scoped client to the user's cookie session, delegates to the
// tested domain logic, then revalidates the affected routes. Never trust the
// caller's identity from arguments — it always comes from the session.
import { revalidatePath } from "next/cache";
import { requireRole } from "@/lib/auth";
import { createServerSupabase } from "@/lib/supabase/server";
import {
  addLogItem,
  createTechnology,
  deleteLogItem,
  getLogWithItems,
  getOrCreateTodayLog,
  setItemTechnologies,
  submitDay,
  updateLogItem,
  type AddItemInput,
  type ItemPatch,
} from "@/lib/logs";
import type { DailyLog, LogItemWithTech, Technology } from "@/lib/types";

const ALL = ["developer", "lead", "manager"] as const;

export async function getOrCreateToday(): Promise<{
  log: DailyLog;
  items: LogItemWithTech[];
}> {
  const profile = await requireRole(...ALL);
  const sb = await createServerSupabase();
  const log = await getOrCreateTodayLog(sb, profile.id);
  return getLogWithItems(sb, log.id);
}

export async function addItem(
  input: Omit<AddItemInput, "dailyLogId"> & { dailyLogId: string }
): Promise<LogItemWithTech> {
  await requireRole(...ALL);
  const sb = await createServerSupabase();
  const item = await addLogItem(sb, input);
  revalidatePath("/today");
  return item;
}

export async function updateItem(id: string, patch: ItemPatch): Promise<void> {
  await requireRole(...ALL);
  const sb = await createServerSupabase();
  await updateLogItem(sb, id, patch);
  revalidatePath("/today");
}

export async function setItemTechnologiesAction(
  itemId: string,
  technologyIds: string[]
): Promise<void> {
  await requireRole(...ALL);
  const sb = await createServerSupabase();
  await setItemTechnologies(sb, itemId, technologyIds);
  revalidatePath("/today");
}

export async function createTechnologyAction(name: string): Promise<Technology> {
  const profile = await requireRole(...ALL);
  const sb = await createServerSupabase();
  const tech = await createTechnology(sb, name, profile.id);
  revalidatePath("/today");
  return tech;
}

export async function deleteItem(id: string): Promise<void> {
  await requireRole(...ALL);
  const sb = await createServerSupabase();
  await deleteLogItem(sb, id);
  revalidatePath("/today");
}

export async function submitDayAction(logId: string): Promise<void> {
  await requireRole(...ALL);
  const sb = await createServerSupabase();
  await submitDay(sb, logId);
  revalidatePath("/today");
}
