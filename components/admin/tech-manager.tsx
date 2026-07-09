"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import {
  mergeTechnologiesAction,
  upsertTechnologyAction,
} from "@/app/actions/admin";
import type { Technology } from "@/lib/types";

const selectCls =
  "h-9 rounded-md border border-border bg-background px-2 text-sm text-foreground";

// Manage the shared technology list: rename/recolor/deactivate, add new, and
// merge dev-added near-duplicates (e.g. "kafka" into "Apache Kafka") — merging
// repoints every task and deactivates the duplicate.
export function TechManager({
  technologies,
}: {
  technologies: Technology[];
}) {
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-2">
        {technologies.map((t) => (
          <TechRow key={t.id} tech={t} />
        ))}
        <TechRow />
      </div>
      <MergePanel technologies={technologies} />
    </div>
  );
}

function TechRow({ tech }: { tech?: Technology }) {
  const router = useRouter();
  const [pending, startTransition] = React.useTransition();
  const [name, setName] = React.useState(tech?.name ?? "");
  const [color, setColor] = React.useState(tech?.color ?? "#64748b");
  const [active, setActive] = React.useState(tech?.active ?? true);
  const isNew = !tech;

  function save() {
    if (!name.trim()) return;
    startTransition(async () => {
      await upsertTechnologyAction({ id: tech?.id, name, color, active });
      if (isNew) {
        setName("");
        setColor("#64748b");
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
        aria-label="Technology name"
        placeholder={isNew ? "New technology…" : undefined}
        value={name}
        onChange={(e) => setName(e.target.value)}
        className="flex-1 min-w-40"
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

function MergePanel({ technologies }: { technologies: Technology[] }) {
  const router = useRouter();
  const [pending, startTransition] = React.useTransition();
  const [fromId, setFromId] = React.useState("");
  const [intoId, setIntoId] = React.useState("");

  const canMerge = fromId && intoId && fromId !== intoId;

  function merge() {
    if (!canMerge) return;
    startTransition(async () => {
      await mergeTechnologiesAction(fromId, intoId);
      setFromId("");
      setIntoId("");
      router.refresh();
    });
  }

  return (
    <div className="rounded-lg border border-border bg-card p-3">
      <p className="mb-2 text-xs font-medium text-muted-foreground">
        Merge duplicates
      </p>
      <div className="flex flex-wrap items-end gap-2">
        <label className="flex flex-col gap-1 text-xs text-muted-foreground">
          Merge
          <select
            className={selectCls}
            value={fromId}
            onChange={(e) => setFromId(e.target.value)}
          >
            <option value="">Select…</option>
            {technologies.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
        </label>
        <span className="pb-2 text-sm text-muted-foreground">into</span>
        <label className="flex flex-col gap-1 text-xs text-muted-foreground">
          Keep
          <select
            className={selectCls}
            value={intoId}
            onChange={(e) => setIntoId(e.target.value)}
          >
            <option value="">Select…</option>
            {technologies.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
        </label>
        <Button
          type="button"
          size="sm"
          variant="destructive"
          onClick={merge}
          disabled={pending || !canMerge}
        >
          Merge
        </Button>
      </div>
    </div>
  );
}
