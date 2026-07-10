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
  "h-9 rounded-lg border border-input bg-background px-2.5 text-sm text-foreground shadow-sm transition-colors focus:border-ring focus:outline-none focus:ring-2 focus:ring-ring/25";

const labelClass =
  "flex flex-col gap-1.5 text-xs font-medium uppercase tracking-wide text-muted-foreground";

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
      className="surface flex flex-wrap items-end gap-x-3 gap-y-4 p-4"
    >
      <label className={labelClass}>
        From
        <input
          type="date"
          name="from"
          defaultValue={filters.from}
          className={selectClass}
        />
      </label>
      <label className={labelClass}>
        To
        <input
          type="date"
          name="to"
          defaultValue={filters.to}
          className={selectClass}
        />
      </label>

      {teams.length > 0 && (
        <label className={labelClass}>
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

      <label className={labelClass}>
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

      <label className={labelClass}>
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

      <label className={labelClass}>
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

      <div className="ml-auto flex items-center gap-1.5">
        <a
          href={action}
          className="h-9 rounded-lg px-3 text-sm font-medium leading-9 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
        >
          Clear
        </a>
        <button
          type="submit"
          className="h-9 rounded-lg bg-primary px-4 text-sm font-medium text-primary-foreground shadow-sm transition-opacity hover:opacity-90"
        >
          Apply
        </button>
      </div>
    </form>
  );
}
