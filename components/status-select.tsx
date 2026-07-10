"use client";

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ColorDot } from "@/components/color-dot";
import type { ItemStatus } from "@/lib/types";

const STATUS_LABELS: Record<ItemStatus, string> = {
  todo: "To do",
  in_progress: "In progress",
  done: "Done",
  blocked: "Blocked",
};

// Same semantic mapping as the pill system (day-review.tsx, log-entry-form.tsx)
// — status color means the same thing everywhere. Neutral for "to do" since
// it isn't an active state yet.
const STATUS_COLOR: Record<ItemStatus, string> = {
  todo: "var(--muted-foreground)",
  in_progress: "var(--info)",
  done: "var(--success)",
  blocked: "var(--destructive)",
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
        <SelectValue>
          {(v) => (
            <>
              <ColorDot color={STATUS_COLOR[v as ItemStatus]} />
              {STATUS_LABELS[v as ItemStatus]}
            </>
          )}
        </SelectValue>
      </SelectTrigger>
      <SelectContent>
        {ORDER.map((s) => (
          <SelectItem key={s} value={s}>
            <ColorDot color={STATUS_COLOR[s]} />
            {STATUS_LABELS[s]}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
