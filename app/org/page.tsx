import Link from "next/link";
import { requireRole } from "@/lib/auth";
import { createServerSupabase } from "@/lib/supabase/server";
import { todayISO } from "@/lib/logs";
import {
  getActivity,
  getTeamBlockedTasks,
  getTeamDevelopers,
  getTeamPendingLogs,
  getTechnologies,
  getVisibleTeams,
  getWorkTypes,
  techDistRows,
  workTypeDistRows,
  type ActivityItem,
  type TeamMemberBrief,
} from "@/lib/queries";
import { buildCalendar, buildDistribution } from "@/lib/aggregate";
import { ApprovalQueue } from "@/components/approval-queue";
import { BlockedTasks } from "@/components/blocked-tasks";
import { FiltersBar, type DashboardFilters } from "@/components/filters-bar";
import { HeatmapCalendar } from "@/components/heatmap-calendar";
import { WorkTypeDistribution } from "@/components/worktype-distribution";
import { TechDistribution } from "@/components/tech-distribution";

function daysAgoISO(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  const p = (x: number) => String(x).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

// Manager home: the whole organization at a glance. RLS returns every team's
// rows for a manager, so the same team components serve as an org-wide view with
// no per-team restriction. Filters narrow by team/category/technology/status.
export default async function OrgPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  await requireRole("manager");
  const sb = await createServerSupabase();

  const sp = await searchParams;
  const first = (v: string | string[] | undefined) =>
    (Array.isArray(v) ? v[0] : v) ?? "";
  const filters: DashboardFilters = {
    from: first(sp.from) || daysAgoISO(29),
    to: first(sp.to) || todayISO(),
    team: first(sp.team),
    workType: first(sp.wt),
    technology: first(sp.tech),
    status: first(sp.status),
  };

  const [pending, developers, blocked, workTypes, technologies, teams, activity] =
    await Promise.all([
      getTeamPendingLogs(sb),
      getTeamDevelopers(sb),
      getTeamBlockedTasks(sb),
      getWorkTypes(sb),
      getTechnologies(sb),
      getVisibleTeams(sb),
      getActivity(sb, { from: filters.from, to: filters.to }),
    ]);

  const teamDevIds = filters.team
    ? new Set(teams.find((t) => t.id === filters.team)?.developerIds ?? [])
    : null;
  const filtered = activity.filter(
    (i) =>
      (!teamDevIds || teamDevIds.has(i.developer_id)) &&
      (!filters.workType || i.work_type_id === filters.workType) &&
      (!filters.technology ||
        i.technologies.some((t) => t.id === filters.technology)) &&
      (!filters.status || i.status === filters.status)
  );

  const byWorkType = buildDistribution(workTypeDistRows(filtered));
  const byTech = buildDistribution(techDistRows(filtered));

  const byDev = new Map<
    string,
    { developer: TeamMemberBrief; items: ActivityItem[] }
  >();
  for (const i of filtered) {
    const e = byDev.get(i.developer_id) ?? { developer: i.developer, items: [] };
    e.items.push(i);
    byDev.set(i.developer_id, e);
  }
  const devHeatmaps = [...byDev.values()]
    .map((e) => ({
      id: e.developer.id,
      name: e.developer.full_name || e.developer.email || "Unknown",
      cells: buildCalendar(
        e.items.map((i) => ({ log_date: i.log_date, count: 1 })),
        filters.from,
        filters.to
      ),
    }))
    .sort((a, b) => a.name.localeCompare(b.name));

  return (
    <main className="mx-auto max-w-5xl p-6">
      <header className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Organization</h1>
          <p className="text-sm text-muted-foreground">
            {developers.length} developers · {teams.length} teams
          </p>
        </div>
        <nav className="flex items-center gap-4 text-sm">
          <Link
            href="/team"
            className="text-muted-foreground underline underline-offset-4 hover:text-foreground"
          >
            Team notes
          </Link>
          <Link
            href="/admin"
            className="rounded-md bg-primary px-3 py-1.5 font-medium text-primary-foreground hover:opacity-90"
          >
            Admin
          </Link>
        </nav>
      </header>

      <div className="flex flex-col gap-8">
        <section>
          <h2 className="mb-3 text-sm font-medium text-muted-foreground">
            Dashboard
          </h2>
          <div className="flex flex-col gap-4">
            <FiltersBar
              action="/org"
              filters={filters}
              teams={teams}
              workTypes={workTypes}
              technologies={technologies}
            />

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="rounded-lg border border-border bg-card p-4">
                <p className="mb-3 text-xs font-medium text-muted-foreground">
                  By category
                </p>
                <WorkTypeDistribution slices={byWorkType} />
              </div>
              <div className="rounded-lg border border-border bg-card p-4">
                <p className="mb-3 text-xs font-medium text-muted-foreground">
                  By technology
                </p>
                <TechDistribution slices={byTech} />
              </div>
            </div>

            <div>
              <p className="mb-3 text-xs font-medium text-muted-foreground">
                Activity by developer
              </p>
              {devHeatmaps.length === 0 ? (
                <p className="rounded-lg border border-dashed border-border p-6 text-center text-sm text-muted-foreground">
                  No activity for these filters.
                </p>
              ) : (
                <div className="grid gap-4 sm:grid-cols-2">
                  {devHeatmaps.map((d) => (
                    <div
                      key={d.id}
                      className="rounded-lg border border-border bg-card p-4"
                    >
                      <p className="mb-3 text-sm font-medium">{d.name}</p>
                      <HeatmapCalendar cells={d.cells} />
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </section>

        <section>
          <h2 className="mb-3 text-sm font-medium text-muted-foreground">
            Awaiting approval
          </h2>
          <ApprovalQueue
            pending={pending}
            workTypes={workTypes}
            technologies={technologies}
          />
        </section>

        <section>
          <h2 className="mb-3 text-sm font-medium text-muted-foreground">
            Blocked tasks
          </h2>
          <BlockedTasks tasks={blocked} technologies={technologies} />
        </section>
      </div>
    </main>
  );
}
