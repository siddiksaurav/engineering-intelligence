import type { ItemStatus, Technology, WorkType } from "@/lib/types";
import type { TeamOption } from "@/lib/queries";

export interface DashboardFilters {
  from: string;
  to: string;
  team: string; // "" = all
  workType: string; // "" = all
  technology: string; // "" = all
  status: string; // "" = all
}

const STATUS_LABELS: Record<ItemStatus, string> = {
  todo: "To do",
  in_progress: "In progress",
  done: "Done",
  blocked: "Blocked",
};

const selectClass =
  "h-9 rounded-md border border-border bg-background px-2 text-sm text-foreground";

// A plain GET form: submitting reloads the (server-rendered) page with the
// chosen filters as query params, so no client JS is needed. `action` targets
// the page the bar lives on (e.g. "/team").
export function FiltersBar({
  action,
  filters,
  teams,
  workTypes,
  technologies,
}: {
  action: string;
  filters: DashboardFilters;
  teams: TeamOption[];
  workTypes: WorkType[];
  technologies: Technology[];
}) {
  return (
    <form
      method="get"
      action={action}
      className="flex flex-wrap items-end gap-3 rounded-lg border border-border bg-card p-3"
    >
      <label className="flex flex-col gap-1 text-xs text-muted-foreground">
        From
        <input
          type="date"
          name="from"
          defaultValue={filters.from}
          className={selectClass}
        />
      </label>
      <label className="flex flex-col gap-1 text-xs text-muted-foreground">
        To
        <input
          type="date"
          name="to"
          defaultValue={filters.to}
          className={selectClass}
        />
      </label>

      {teams.length > 0 && (
        <label className="flex flex-col gap-1 text-xs text-muted-foreground">
          Team
          <select name="team" defaultValue={filters.team} className={selectClass}>
            <option value="">All teams</option>
            {teams.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
        </label>
      )}

      <label className="flex flex-col gap-1 text-xs text-muted-foreground">
        Category
        <select name="wt" defaultValue={filters.workType} className={selectClass}>
          <option value="">All categories</option>
          {workTypes.map((w) => (
            <option key={w.id} value={w.id}>
              {w.name}
            </option>
          ))}
        </select>
      </label>

      <label className="flex flex-col gap-1 text-xs text-muted-foreground">
        Technology
        <select
          name="tech"
          defaultValue={filters.technology}
          className={selectClass}
        >
          <option value="">All technologies</option>
          {technologies.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
      </label>

      <label className="flex flex-col gap-1 text-xs text-muted-foreground">
        Status
        <select
          name="status"
          defaultValue={filters.status}
          className={selectClass}
        >
          <option value="">Any status</option>
          {(Object.keys(STATUS_LABELS) as ItemStatus[]).map((s) => (
            <option key={s} value={s}>
              {STATUS_LABELS[s]}
            </option>
          ))}
        </select>
      </label>

      <div className="flex items-center gap-2">
        <button
          type="submit"
          className="h-9 rounded-md bg-primary px-3 text-sm font-medium text-primary-foreground hover:opacity-90"
        >
          Apply
        </button>
        <a
          href={action}
          className="text-sm text-muted-foreground underline underline-offset-4 hover:text-foreground"
        >
          Clear
        </a>
      </div>
    </form>
  );
}
