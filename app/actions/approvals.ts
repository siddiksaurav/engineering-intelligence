"use server";

// Thin Server Action wrappers around lib/team approval logic. Each authenticates
// as lead/manager, binds an RLS-scoped client to the session, delegates to the
// tested domain logic, then revalidates the team + org views. RLS still has the
// final say on which days the caller may actually touch.
import { revalidatePath } from "next/cache";
import { requireRole } from "@/lib/auth";
import { createServerSupabase } from "@/lib/supabase/server";
import { approveDay, reopenForDev } from "@/lib/team";

export async function approveDayAction(logId: string): Promise<void> {
  await requireRole("lead", "manager");
  const sb = await createServerSupabase();
  await approveDay(sb, logId);
  revalidatePath("/team");
  revalidatePath("/org");
}

export async function reopenForDevAction(logId: string): Promise<void> {
  await requireRole("lead", "manager");
  const sb = await createServerSupabase();
  await reopenForDev(sb, logId);
  revalidatePath("/team");
  revalidatePath("/org");
}
