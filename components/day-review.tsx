import { Badge } from "@/components/ui/badge";
import { EntityChip } from "@/components/entity-chip";
import type {
  ItemStatus,
  LogItemWithTech,
  Technology,
  WorkType,
} from "@/lib/types";

// Same semantic mapping as the day-status pill (log-entry-form.tsx): status
// color means the same thing everywhere it appears. "To do" stays neutral —
// a plain Badge, not a semantic pill — since it isn't an active state yet.
const ITEM_STATUS_META: Record<ItemStatus, { label: string; pill: string | null }> = {
  todo: { label: "To do", pill: null },
  in_progress: { label: "In progress", pill: "pill pill-info" },
  done: { label: "Done", pill: "pill pill-success" },
  blocked: { label: "Blocked", pill: "pill pill-destructive" },
};

// Read-only view of a day's tasks: category, status, technologies, hours, and
// any blocker note. Presentational — safe to render from server or client trees.
export function DayReview({
  items,
  workTypes,
  technologies,
}: {
  items: LogItemWithTech[];
  workTypes: WorkType[];
  technologies: Technology[];
}) {
  const wt = new Map(workTypes.map((w) => [w.id, w] as const));
  const tech = new Map(technologies.map((t) => [t.id, t] as const));

  if (items.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">No tasks on this day.</p>
    );
  }

  return (
    <ul className="flex flex-col gap-2">
      {items.map((item) => {
        const meta = ITEM_STATUS_META[item.status];
        const workType = wt.get(item.work_type_id);
        return (
          <li
            key={item.id}
            className="rounded-md border border-border bg-background p-3"
          >
            <div className="flex flex-wrap items-center gap-2">
              {workType ? (
                <EntityChip color={workType.color}>{workType.name}</EntityChip>
              ) : (
                <Badge variant="outline">—</Badge>
              )}
              {meta.pill ? (
                <span className={meta.pill}>{meta.label}</span>
              ) : (
                <Badge variant="secondary">{meta.label}</Badge>
              )}
              {item.hours != null && (
                <span className="text-xs text-muted-foreground">
                  {item.hours}h
                </span>
              )}
            </div>

            <p className="mt-2 text-sm">{item.description || "—"}</p>

            {item.technology_ids.length > 0 && (
              <div className="mt-2 flex flex-wrap gap-1">
                {item.technology_ids.map((id) => {
                  const t = tech.get(id);
                  return (
                    <EntityChip key={id} color={t?.color ?? "var(--muted-foreground)"}>
                      {t?.name ?? "?"}
                    </EntityChip>
                  );
                })}
              </div>
            )}

            {item.status === "blocked" && item.blocker_note && (
              <p className="mt-2 rounded-md bg-destructive/10 px-2 py-1 text-xs text-[color:var(--destructive-ink)]">
                Blocked: {item.blocker_note}
              </p>
            )}
          </li>
        );
      })}
    </ul>
  );
}
