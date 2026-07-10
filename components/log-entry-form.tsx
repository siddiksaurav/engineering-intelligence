"use client";

import * as React from "react";
import { PlusIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { LogItemRow } from "@/components/log-item-row";
import {
  addItem,
  updateItem,
  setItemTechnologiesAction,
  createTechnologyAction,
  deleteItem,
  submitDayAction,
} from "@/app/actions/logs";
import type { ItemPatch } from "@/lib/logs";
import type {
  DailyLog,
  LogItemWithTech,
  LogStatus,
  Technology,
  WorkType,
} from "@/lib/types";

const STATUS_META: Record<LogStatus, { label: string; pill: string }> = {
  draft: { label: "Draft", pill: "pill pill-warning" },
  submitted: { label: "Submitted", pill: "pill pill-info" },
  approved: { label: "Approved", pill: "pill pill-success" },
};

export function LogEntryForm({
  log,
  initialItems,
  workTypes,
  technologies: initialTechnologies,
}: {
  log: DailyLog;
  initialItems: LogItemWithTech[];
  workTypes: WorkType[];
  technologies: Technology[];
}) {
  const [items, setItems] = React.useState(initialItems);
  const [technologies, setTechnologies] = React.useState(initialTechnologies);
  const [status, setStatus] = React.useState<LogStatus>(log.status);
  const [pending, startTransition] = React.useTransition();

  const locked = status === "approved"; // developers cannot edit an approved day
  const meta = STATUS_META[status];

  // Any edit reopens a submitted/approved day; the server clears approval and
  // we mirror that here.
  const markReopened = () => setStatus((s) => (s === "draft" ? s : "draft"));

  function handleAdd() {
    if (!workTypes.length) return;
    startTransition(async () => {
      const item = await addItem({
        dailyLogId: log.id,
        workTypeId: workTypes[0].id,
        status: "todo",
        description: "",
        hours: null,
        blockerNote: null,
        technologyIds: [],
      });
      setItems((prev) => [...prev, item]);
      markReopened();
    });
  }

  function handleUpdate(id: string, patch: ItemPatch) {
    setItems((prev) =>
      prev.map((i) => (i.id === id ? { ...i, ...patch } : i))
    );
    startTransition(async () => {
      await updateItem(id, patch);
      markReopened();
    });
  }

  function handleSetTech(id: string, ids: string[]) {
    setItems((prev) =>
      prev.map((i) => (i.id === id ? { ...i, technology_ids: ids } : i))
    );
    startTransition(async () => {
      await setItemTechnologiesAction(id, ids);
      markReopened();
    });
  }

  async function handleCreateTech(name: string): Promise<Technology> {
    const tech = await createTechnologyAction(name);
    setTechnologies((prev) =>
      prev.some((t) => t.id === tech.id) ? prev : [...prev, tech]
    );
    return tech;
  }

  function handleDelete(id: string) {
    setItems((prev) => prev.filter((i) => i.id !== id));
    startTransition(async () => {
      await deleteItem(id);
      markReopened();
    });
  }

  function handleSubmit() {
    startTransition(async () => {
      await submitDayAction(log.id);
      setStatus("submitted");
    });
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="surface flex items-center justify-between gap-3 px-4 py-3">
        <div className="flex items-center gap-2.5">
          <span className="eyebrow">Day status</span>
          <span className={meta.pill}>{meta.label}</span>
        </div>
        <button
          type="button"
          onClick={handleSubmit}
          disabled={locked || pending || items.length === 0}
          className="btn-primary h-9 px-4 text-sm"
        >
          Submit for approval
        </button>
      </div>

      <div className="flex flex-col gap-3">
        {items.length === 0 && (
          <p className="rounded-xl border border-dashed border-border bg-card/40 p-8 text-center text-sm text-muted-foreground">
            No tasks yet. Add what you worked on today.
          </p>
        )}
        {items.map((item) => (
          <LogItemRow
            key={item.id}
            item={item}
            workTypes={workTypes}
            technologies={technologies}
            disabled={locked}
            onUpdate={handleUpdate}
            onSetTech={handleSetTech}
            onCreateTech={handleCreateTech}
            onDelete={handleDelete}
          />
        ))}
      </div>

      <div>
        <Button
          type="button"
          variant="outline"
          onClick={handleAdd}
          disabled={locked || pending}
          className="gap-1"
        >
          <PlusIcon className="size-4" /> Add task
        </Button>
      </div>
    </div>
  );
}
