"use client";

import * as React from "react";
import { ChevronDownIcon, ChevronRightIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { DayReview } from "@/components/day-review";
import { approveDayAction, reopenForDevAction } from "@/app/actions/approvals";
import type { TeamDay } from "@/lib/queries";
import type { Technology, WorkType } from "@/lib/types";

// Submitted days awaiting the lead's decision. Approve locks the day; Reopen
// sends it back to draft for the developer. Either decision removes it from the
// queue (it is no longer "submitted").
export function ApprovalQueue({
  pending,
  workTypes,
  technologies,
}: {
  pending: TeamDay[];
  workTypes: WorkType[];
  technologies: Technology[];
}) {
  const [days, setDays] = React.useState(pending);
  const [expanded, setExpanded] = React.useState<Set<string>>(new Set());
  const [busyId, setBusyId] = React.useState<string | null>(null);
  const [, startTransition] = React.useTransition();

  function toggle(id: string) {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function decide(logId: string, action: (id: string) => Promise<void>) {
    setBusyId(logId);
    startTransition(async () => {
      await action(logId);
      setDays((prev) => prev.filter((d) => d.id !== logId));
      setBusyId(null);
    });
  }

  if (days.length === 0) {
    return (
      <p className="rounded-lg border border-dashed border-border p-6 text-center text-sm text-muted-foreground">
        Nothing to approve right now.
      </p>
    );
  }

  return (
    <ul className="flex flex-col gap-2">
      {days.map((day) => {
        const who = day.developer.full_name || day.developer.email || "Unknown";
        const pretty = new Date(`${day.log_date}T00:00:00`).toLocaleDateString(
          undefined,
          { weekday: "short", month: "short", day: "numeric" }
        );
        const isOpen = expanded.has(day.id);
        const busy = busyId === day.id;
        return (
          <li
            key={day.id}
            className="rounded-lg border border-border bg-card p-3"
          >
            <div className="flex flex-wrap items-center gap-2">
              <button
                type="button"
                onClick={() => toggle(day.id)}
                className="flex items-center gap-2 text-left"
                aria-expanded={isOpen}
              >
                {isOpen ? (
                  <ChevronDownIcon className="size-4 text-muted-foreground" />
                ) : (
                  <ChevronRightIcon className="size-4 text-muted-foreground" />
                )}
                <span className="font-medium">{who}</span>
                <span className="text-sm text-muted-foreground">{pretty}</span>
                <span className="text-sm text-muted-foreground">
                  {day.items.length} {day.items.length === 1 ? "task" : "tasks"}
                </span>
              </button>
              <div className="ml-auto flex items-center gap-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={busy}
                  onClick={() => decide(day.id, reopenForDevAction)}
                >
                  Reopen
                </Button>
                <Button
                  type="button"
                  size="sm"
                  disabled={busy}
                  onClick={() => decide(day.id, approveDayAction)}
                >
                  Approve
                </Button>
              </div>
            </div>

            {isOpen && (
              <div className="mt-3">
                <DayReview
                  items={day.items}
                  workTypes={workTypes}
                  technologies={technologies}
                />
              </div>
            )}
          </li>
        );
      })}
    </ul>
  );
}
