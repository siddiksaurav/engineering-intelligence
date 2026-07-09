"use client";

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { ItemStatus } from "@/lib/types";

const STATUS_LABELS: Record<ItemStatus, string> = {
  todo: "To do",
  in_progress: "In progress",
  done: "Done",
  blocked: "Blocked",
};

const ORDER: ItemStatus[] = ["todo", "in_progress", "done", "blocked"];

export function StatusSelect({
  value,
  onChange,
  disabled,
}: {
  value: ItemStatus;
  onChange: (status: ItemStatus) => void;
  disabled?: boolean;
}) {
  return (
    <Select
      value={value}
      onValueChange={(v) => onChange(v as ItemStatus)}
      disabled={disabled}
    >
      <SelectTrigger className="w-36" aria-label="Task status">
        <SelectValue>{(v) => STATUS_LABELS[v as ItemStatus]}</SelectValue>
      </SelectTrigger>
      <SelectContent>
        {ORDER.map((s) => (
          <SelectItem key={s} value={s}>
            {STATUS_LABELS[s]}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
