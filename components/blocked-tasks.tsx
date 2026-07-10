import { Badge } from "@/components/ui/badge";
import { EntityChip } from "@/components/entity-chip";
import type { BlockedTask } from "@/lib/queries";
import type { Technology } from "@/lib/types";

// Every currently-blocked task across the lead's teams, newest day first. Surfaces
// who is blocked and why so a lead can unblock without hunting through days.
export function BlockedTasks({
  tasks,
  technologies,
}: {
  tasks: BlockedTask[];
  technologies: Technology[];
}) {
  const tech = new Map(technologies.map((t) => [t.id, t] as const));

  if (tasks.length === 0) {
    return (
      <p className="rounded-lg border border-dashed border-border p-6 text-center text-sm text-muted-foreground">
        No blocked tasks. 🎉
      </p>
    );
  }

  return (
    <ul className="flex flex-col gap-2">
      {tasks.map((task) => {
        const who = task.developer.full_name || task.developer.email || "Unknown";
        const pretty = task.log_date
          ? new Date(`${task.log_date}T00:00:00`).toLocaleDateString(undefined, {
              month: "short",
              day: "numeric",
            })
          : "";
        return (
          <li
            key={task.id}
            className="rounded-lg border border-border bg-card p-3"
          >
            <div className="flex items-center justify-between gap-2">
              <span className="font-medium">{who}</span>
              <div className="flex items-center gap-2">
                {pretty && (
                  <span className="text-xs text-muted-foreground">{pretty}</span>
                )}
                <Badge variant="destructive">Blocked</Badge>
              </div>
            </div>
            <p className="mt-1 text-sm">{task.description || "—"}</p>
            {task.blocker_note && (
              <p className="mt-2 rounded-md bg-destructive/10 px-2 py-1 text-xs text-[color:var(--destructive-ink)]">
                {task.blocker_note}
              </p>
            )}
            {task.technology_ids.length > 0 && (
              <div className="mt-2 flex flex-wrap gap-1">
                {task.technology_ids.map((id) => {
                  const t = tech.get(id);
                  return (
                    <EntityChip key={id} color={t?.color ?? "var(--muted-foreground)"}>
                      {t?.name ?? "?"}
                    </EntityChip>
                  );
                })}
              </div>
            )}
          </li>
        );
      })}
    </ul>
  );
}
