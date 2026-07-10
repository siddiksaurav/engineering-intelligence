import { requireRole } from "@/lib/auth";
import { createServerSupabase } from "@/lib/supabase/server";
import { todayISO } from "@/lib/logs";
import {
  getActivity,
  getDeveloperHistory,
  techDistRows,
  workTypeDistRows,
} from "@/lib/queries";
import { buildCalendar, buildDistribution } from "@/lib/aggregate";
import { HeatmapCalendar } from "@/components/heatmap-calendar";
import { WorkTypeDistribution } from "@/components/worktype-distribution";
import { TechDistribution } from "@/components/tech-distribution";
import { Badge } from "@/components/ui/badge";
import { AppBar } from "@/components/app-bar";
import { SectionHeader } from "@/components/section-header";
import { EmptyState } from "@/components/empty-state";
import type { LogStatus } from "@/lib/types";

const STATUS_META: Record<
  LogStatus,
  { label: string; variant: "secondary" | "default" | "outline" }
> = {
  draft: { label: "Draft", variant: "secondary" },
  submitted: { label: "Submitted", variant: "default" },
  approved: { label: "Approved", variant: "outline" },
};

function daysAgoISO(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  const p = (x: number) => String(x).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

// Developer's last 30 days: an activity heatmap, category/technology
// distributions, and the day-by-day history list. All rows are the developer's
// own (RLS + explicit developerId filter).
export default async function MePage() {
  const profile = await requireRole("developer", "lead", "manager");
  const sb = await createServerSupabase();

  const to = todayISO();
  const from = daysAgoISO(29);
  const [days, activity] = await Promise.all([
    getDeveloperHistory(sb, profile.id, from, to),
    getActivity(sb, { from, to, developerId: profile.id }),
  ]);

  const calendar = buildCalendar(
    activity.map((i) => ({ log_date: i.log_date, count: 1 })),
    from,
    to
  );
  const byWorkType = buildDistribution(workTypeDistRows(activity));
  const byTech = buildDistribution(techDistRows(activity));

  const counts: Record<string, number> = {};
  for (const i of activity) counts[i.log_date] = (counts[i.log_date] ?? 0) + 1;
  const blocked = new Set(
    activity.filter((i) => i.status === "blocked").map((i) => i.log_date)
  );

  return (
    <div className="min-h-full">
      <AppBar profile={profile} />
      <main className="mx-auto max-w-3xl px-6 py-8">
        <div className="hero-band mb-10 px-7 pt-8 pb-6">
          <p className="eyebrow text-white/70">My history</p>
          <h1 className="mt-1.5 text-4xl font-semibold tracking-tight">
            Last 30 days
          </h1>
          <p className="mt-2 text-sm text-white/85">
            Your activity, category &amp; technology mix, and day-by-day log.
          </p>
        </div>

        <div className="flex flex-col gap-12">
          <section>
            <SectionHeader eyebrow="Activity" title="Contributions" />
            <div className="surface p-5">
              <HeatmapCalendar cells={calendar} />
            </div>
          </section>

          <div className="grid gap-8 sm:grid-cols-2">
            <section>
              <SectionHeader eyebrow="Breakdown" title="By category" />
              <div className="surface p-5">
                <WorkTypeDistribution slices={byWorkType} />
              </div>
            </section>
            <section>
              <SectionHeader eyebrow="Breakdown" title="By technology" />
              <div className="surface p-5">
                <TechDistribution slices={byTech} />
              </div>
            </section>
          </div>

          <section>
            <SectionHeader eyebrow="Timeline" title="Days" />
            {days.length === 0 ? (
              <EmptyState message="Nothing logged in the last 30 days." />
            ) : (
              <ul className="flex flex-col gap-2">
                {days.map((day) => {
                  const meta = STATUS_META[day.status];
                  const count = counts[day.log_date] ?? 0;
                  const prettyDate = new Date(
                    `${day.log_date}T00:00:00`
                  ).toLocaleDateString(undefined, {
                    weekday: "short",
                    month: "short",
                    day: "numeric",
                  });
                  return (
                    <li
                      key={day.id}
                      className="surface flex items-center justify-between p-3.5"
                    >
                      <div className="flex items-center gap-3">
                        <span className="font-medium">{prettyDate}</span>
                        <span className="text-sm text-muted-foreground">
                          {count} {count === 1 ? "task" : "tasks"}
                        </span>
                        {blocked.has(day.log_date) && (
                          <Badge variant="destructive">Blocked</Badge>
                        )}
                      </div>
                      <Badge variant={meta.variant}>{meta.label}</Badge>
                    </li>
                  );
                })}
              </ul>
            )}
          </section>
        </div>
      </main>
    </div>
  );
}
