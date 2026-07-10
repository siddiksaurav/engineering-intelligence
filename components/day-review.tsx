import { Badge } from "@/components/ui/badge";
import { EntityChip } from "@/components/entity-chip";
import type {
  ItemStatus,
  LogItemWithTech,
  Technology,
  WorkType,
} from "@/lib/types";

const ITEM_STATUS_META: Record<
  ItemStatus,
  { label: string; variant: "secondary" | "default" | "outline" | "destructive" }
> = {
  todo: { label: "To do", variant: "secondary" },
  in_progress: { label: "In progress", variant: "default" },
  done: { label: "Done", variant: "outline" },
  blocked: { label: "Blocked", variant: "destructive" },
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
              <Badge variant={meta.variant}>{meta.label}</Badge>
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
              <p className="mt-2 rounded-md bg-destructive/10 px-2 py-1 text-xs text-destructive">
                Blocked: {item.blocker_note}
              </p>
            )}
          </li>
        );
      })}
    </ul>
  );
}
