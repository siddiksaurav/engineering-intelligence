import { requireRole } from "@/lib/auth";
import { createServerSupabase } from "@/lib/supabase/server";
import { getOrCreateTodayLog, getLogWithItems } from "@/lib/logs";
import { getTechnologies, getWorkTypes } from "@/lib/queries";
import { LogEntryForm } from "@/components/log-entry-form";

// The developer's daily entry. The day row is created on first visit so item
// writes always have a parent; RLS scopes everything to the signed-in user.
export default async function TodayPage() {
  const profile = await requireRole("developer", "lead", "manager");
  const sb = await createServerSupabase();

  const log = await getOrCreateTodayLog(sb, profile.id);
  const [{ items }, workTypes, technologies] = await Promise.all([
    getLogWithItems(sb, log.id),
    getWorkTypes(sb),
    getTechnologies(sb),
  ]);

  const pretty = new Date(`${log.log_date}T00:00:00`).toLocaleDateString(
    undefined,
    { weekday: "long", month: "long", day: "numeric" }
  );

  return (
    <main className="mx-auto max-w-3xl p-6">
      <header className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight">Today</h1>
        <p className="text-sm text-muted-foreground">{pretty}</p>
      </header>
      <LogEntryForm
        log={log}
        initialItems={items}
        workTypes={workTypes}
        technologies={technologies}
      />
    </main>
  );
}
