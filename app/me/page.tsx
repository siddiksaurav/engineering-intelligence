import Link from "next/link";
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
    <main className="mx-auto max-w-3xl p-6">
      <header className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">My history</h1>
          <p className="text-sm text-muted-foreground">Last 30 days</p>
        </div>
        <Link
          href="/today"
          className="text-sm text-muted-foreground underline underline-offset-4 hover:text-foreground"
        >
          Back to today
        </Link>
      </header>

      <div className="flex flex-col gap-8">
        <section>
          <h2 className="mb-3 text-sm font-medium text-muted-foreground">
            Activity
          </h2>
          <div className="rounded-lg border border-border bg-card p-4">
            <HeatmapCalendar cells={calendar} />
          </div>
        </section>

        <div className="grid gap-8 sm:grid-cols-2">
          <section>
            <h2 className="mb-3 text-sm font-medium text-muted-foreground">
              By category
            </h2>
            <div className="rounded-lg border border-border bg-card p-4">
              <WorkTypeDistribution slices={byWorkType} />
            </div>
          </section>
          <section>
            <h2 className="mb-3 text-sm font-medium text-muted-foreground">
              By technology
            </h2>
            <div className="rounded-lg border border-border bg-card p-4">
              <TechDistribution slices={byTech} />
            </div>
          </section>
        </div>

        <section>
          <h2 className="mb-3 text-sm font-medium text-muted-foreground">
            Days
          </h2>
          {days.length === 0 ? (
            <p className="rounded-lg border border-dashed border-border p-6 text-center text-sm text-muted-foreground">
              Nothing logged in the last 30 days.
            </p>
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
                    className="flex items-center justify-between rounded-lg border border-border bg-card p-3"
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
  );
}
