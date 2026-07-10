"use client";

import { Trash2Icon } from "lucide-react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { StatusSelect } from "@/components/status-select";
import { TechMultiselect } from "@/components/tech-multiselect";
import { ColorDot } from "@/components/color-dot";
import type {
  ItemStatus,
  LogItemWithTech,
  Technology,
  WorkType,
} from "@/lib/types";
import type { ItemPatch } from "@/lib/logs";

export function LogItemRow({
  item,
  workTypes,
  technologies,
  disabled,
  onUpdate,
  onSetTech,
  onCreateTech,
  onDelete,
}: {
  item: LogItemWithTech;
  workTypes: WorkType[];
  technologies: Technology[];
  disabled?: boolean;
  onUpdate: (id: string, patch: ItemPatch) => void;
  onSetTech: (id: string, ids: string[]) => void;
  onCreateTech: (name: string) => Promise<Technology>;
  onDelete: (id: string) => void;
}) {
  const wtLabel = (id: string) =>
    workTypes.find((w) => w.id === id)?.name ?? "Category";
  const wtColor = (id: string) =>
    workTypes.find((w) => w.id === id)?.color ?? "var(--muted-foreground)";

  return (
    <div className="surface p-4">
      <div className="flex flex-wrap items-center gap-2">
        <Select
          value={item.work_type_id}
          onValueChange={(v) => onUpdate(item.id, { work_type_id: v as string })}
          disabled={disabled}
        >
          <SelectTrigger className="w-40" aria-label="Category">
            <SelectValue placeholder="Category">
              {(v) => (
                <>
                  <ColorDot color={wtColor(v as string)} />
                  {wtLabel(v as string)}
                </>
              )}
            </SelectValue>
          </SelectTrigger>
          <SelectContent>
            {workTypes.map((w) => (
              <SelectItem key={w.id} value={w.id}>
                <ColorDot color={w.color} />
                {w.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <StatusSelect
          value={item.status}
          onChange={(status) => onUpdate(item.id, { status })}
          disabled={disabled}
        />

        <Input
          type="number"
          step="0.5"
          min="0"
          inputMode="decimal"
          placeholder="hrs"
          className="w-20"
          defaultValue={item.hours ?? ""}
          disabled={disabled}
          aria-label="Hours"
          onBlur={(e) => {
            const raw = e.target.value.trim();
            const hours = raw === "" ? null : Number(raw);
            if (hours !== item.hours) onUpdate(item.id, { hours });
          }}
        />

        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="ml-auto text-muted-foreground"
          disabled={disabled}
          aria-label="Delete task"
          onClick={() => onDelete(item.id)}
        >
          <Trash2Icon className="size-4" />
        </Button>
      </div>

      <Input
        placeholder="What did you work on?"
        className="mt-2"
        defaultValue={item.description}
        disabled={disabled}
        aria-label="Description"
        onBlur={(e) => {
          const description = e.target.value;
          if (description !== item.description)
            onUpdate(item.id, { description });
        }}
      />

      <div className="mt-2">
        <TechMultiselect
          technologies={technologies}
          selectedIds={item.technology_ids}
          onChange={(ids) => onSetTech(item.id, ids)}
          onCreate={onCreateTech}
          disabled={disabled}
        />
      </div>

      {item.status === "blocked" && (
        <Textarea
          placeholder="What's blocking this task?"
          className="mt-2"
          defaultValue={item.blocker_note ?? ""}
          disabled={disabled}
          aria-label="Blocker note"
          onBlur={(e) => {
            const blocker_note = e.target.value.trim() || null;
            if (blocker_note !== item.blocker_note)
              onUpdate(item.id, { blocker_note });
          }}
        />
      )}
    </div>
  );
}

// Re-exported for callers that need the union without importing lib/types.
export type { ItemStatus };
