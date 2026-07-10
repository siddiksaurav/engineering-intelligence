import { AppBar } from "@/components/app-bar";
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
import { SectionHeader } from "@/components/section-header";
import { EmptyState } from "@/components/empty-state";

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
  const profile = await requireRole("manager");
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

  const stats = [
    { label: "Developers", value: developers.length },
    { label: "Teams", value: teams.length },
    { label: "Awaiting approval", value: pending.length, accent: pending.length > 0 },
    { label: "Blocked", value: blocked.length, alert: blocked.length > 0 },
  ];

  return (
    <div className="min-h-full">
      <AppBar profile={profile} />

      <main className="mx-auto max-w-6xl px-6 py-8">
        {/* Gradient hero with the telemetry strip embedded — the whole org
            in one colored read. */}
        <div className="hero-band mb-10 px-7 pt-8 pb-6">
          <p className="eyebrow text-white/70">Org overview</p>
          <h1 className="mt-1.5 text-4xl font-semibold tracking-tight">
            Organization
          </h1>
          <p className="mt-2 text-sm text-white/85">
            Activity, approvals, and blockers across every team — from{" "}
            <span className="font-mono">{filters.from}</span> to{" "}
            <span className="font-mono">{filters.to}</span>.
          </p>

          <div className="relative mt-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
            {stats.map((s) => (
              <div
                key={s.label}
                className="rounded-xl border border-white/15 bg-white/10 p-4 backdrop-blur-sm"
              >
                <p className="eyebrow text-white/70">{s.label}</p>
                <p className="mt-1.5 font-mono text-3xl font-semibold tabular-nums tracking-tight text-white">
                  {s.value}
                </p>
              </div>
            ))}
          </div>
        </div>

        <div className="flex flex-col gap-12">
          <section>
            <SectionHeader eyebrow="Dashboard" title="Activity" />
            <div className="flex flex-col gap-5">
              <FiltersBar
                action="/org"
                filters={filters}
                teams={teams}
                workTypes={workTypes}
                technologies={technologies}
              />

              <div className="grid gap-4 sm:grid-cols-2">
                <div className="surface p-5">
                  <p className="eyebrow mb-4">By category</p>
                  <WorkTypeDistribution slices={byWorkType} />
                </div>
                <div className="surface p-5">
                  <p className="eyebrow mb-4">By technology</p>
                  <TechDistribution slices={byTech} />
                </div>
              </div>

              <div>
                <p className="eyebrow mb-4">Activity by developer</p>
                {devHeatmaps.length === 0 ? (
                  <EmptyState message="No activity for these filters." />
                ) : (
                  <div className="grid gap-4 sm:grid-cols-2">
                    {devHeatmaps.map((d) => (
                      <div key={d.id} className="surface p-5">
                        <p className="mb-4 text-sm font-medium text-foreground">
                          {d.name}
                        </p>
                        <HeatmapCalendar cells={d.cells} />
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </section>

          <section>
            <SectionHeader eyebrow="Review queue" title="Awaiting approval" />
            <ApprovalQueue
              pending={pending}
              workTypes={workTypes}
              technologies={technologies}
            />
          </section>

          <section>
            <SectionHeader eyebrow="Attention" title="Blocked tasks" />
            <BlockedTasks tasks={blocked} technologies={technologies} />
          </section>
        </div>
      </main>
    </div>
  );
}
