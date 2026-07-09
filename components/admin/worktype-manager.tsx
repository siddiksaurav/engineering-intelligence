"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { upsertWorkTypeAction } from "@/app/actions/admin";
import type { WorkType } from "@/lib/types";

// One editable row per work-type category, plus an add row. Managers rename,
// recolor, reorder, or deactivate categories; deactivated ones drop out of the
// developer's task form but stay for historical rows.
export function WorkTypeManager({ workTypes }: { workTypes: WorkType[] }) {
  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-col gap-2">
        {workTypes.map((wt) => (
          <WorkTypeRow key={wt.id} workType={wt} />
        ))}
      </div>
      <WorkTypeRow />
    </div>
  );
}

function WorkTypeRow({ workType }: { workType?: WorkType }) {
  const router = useRouter();
  const [pending, startTransition] = React.useTransition();
  const [name, setName] = React.useState(workType?.name ?? "");
  const [color, setColor] = React.useState(workType?.color ?? "#4f46e5");
  const [sortOrder, setSortOrder] = React.useState(workType?.sort_order ?? 0);
  const [active, setActive] = React.useState(workType?.active ?? true);
  const isNew = !workType;

  function save() {
    if (!name.trim()) return;
    startTransition(async () => {
      await upsertWorkTypeAction({
        id: workType?.id,
        name,
        color,
        sort_order: Number(sortOrder) || 0,
        active,
      });
      if (isNew) {
        setName("");
        setColor("#4f46e5");
        setSortOrder(0);
      }
      router.refresh();
    });
  }

  return (
    <div className="flex flex-wrap items-center gap-2 rounded-lg border border-border bg-card p-2">
      <input
        type="color"
        aria-label="Color"
        value={color}
        onChange={(e) => setColor(e.target.value)}
        className="h-8 w-8 rounded border border-border bg-background"
      />
      <Input
        aria-label="Category name"
        placeholder={isNew ? "New category…" : undefined}
        value={name}
        onChange={(e) => setName(e.target.value)}
        className="flex-1 min-w-40"
      />
      <Input
        type="number"
        aria-label="Sort order"
        value={sortOrder}
        onChange={(e) => setSortOrder(Number(e.target.value))}
        className="w-20"
      />
      <label className="flex items-center gap-1 text-xs text-muted-foreground">
        <input
          type="checkbox"
          checked={active}
          onChange={(e) => setActive(e.target.checked)}
        />
        Active
      </label>
      <Button
        type="button"
        size="sm"
        variant={isNew ? "default" : "outline"}
        onClick={save}
        disabled={pending || !name.trim()}
      >
        {isNew ? "Add" : "Save"}
      </Button>
    </div>
  );
}
