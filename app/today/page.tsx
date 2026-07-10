import { requireRole } from "@/lib/auth";
import { createServerSupabase } from "@/lib/supabase/server";
import { getOrCreateTodayLog, getLogWithItems } from "@/lib/logs";
import { getTechnologies, getWorkTypes } from "@/lib/queries";
import { LogEntryForm } from "@/components/log-entry-form";
import { AppBar } from "@/components/app-bar";

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
    <div className="min-h-full">
      <AppBar profile={profile} />
      <main className="mx-auto max-w-3xl px-6 py-8">
        <div className="hero-band mb-8 px-7 py-8">
          <p className="eyebrow text-white/70">Daily log</p>
          <h1 className="mt-1.5 text-4xl font-semibold tracking-tight">
            Today
          </h1>
          <p className="mt-2 text-sm text-white/85">
            <span className="font-mono">{pretty}</span> · Log what you shipped,
            then send it for approval.
          </p>
        </div>
        <LogEntryForm
          log={log}
          initialItems={items}
          workTypes={workTypes}
          technologies={technologies}
        />
      </main>
    </div>
  );
}
