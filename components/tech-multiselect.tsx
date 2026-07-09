"use client";

import * as React from "react";
import { PlusIcon } from "lucide-react";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import type { Technology } from "@/lib/types";

export function TechMultiselect({
  technologies,
  selectedIds,
  onChange,
  onCreate,
  disabled,
}: {
  technologies: Technology[];
  selectedIds: string[];
  onChange: (ids: string[]) => void;
  // Adds a new technology to the shared list and returns it; the multiselect
  // then selects it.
  onCreate: (name: string) => Promise<Technology>;
  disabled?: boolean;
}) {
  const [open, setOpen] = React.useState(false);
  const [query, setQuery] = React.useState("");
  const [creating, setCreating] = React.useState(false);

  const byId = React.useMemo(
    () => new Map(technologies.map((t) => [t.id, t])),
    [technologies]
  );
  const selected = selectedIds
    .map((id) => byId.get(id))
    .filter((t): t is Technology => Boolean(t));

  const trimmed = query.trim();
  const exactExists = technologies.some(
    (t) => t.name.toLowerCase() === trimmed.toLowerCase()
  );

  function toggle(id: string) {
    onChange(
      selectedIds.includes(id)
        ? selectedIds.filter((x) => x !== id)
        : [...selectedIds, id]
    );
  }

  async function handleCreate() {
    if (!trimmed || creating) return;
    setCreating(true);
    try {
      const tech = await onCreate(trimmed);
      if (!selectedIds.includes(tech.id)) onChange([...selectedIds, tech.id]);
      setQuery("");
    } finally {
      setCreating(false);
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-1.5">
      {selected.map((t) => (
        <Badge
          key={t.id}
          variant="secondary"
          className="cursor-pointer"
          onClick={() => !disabled && toggle(t.id)}
          title="Remove"
        >
          {t.name}
          <span className="ml-1 opacity-60">×</span>
        </Badge>
      ))}
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger
          disabled={disabled}
          render={
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="h-7 gap-1"
            />
          }
        >
          <PlusIcon className="size-3.5" />
          Technology
        </PopoverTrigger>
        <PopoverContent align="start" className="w-64 p-0">
          <Command
            // Let us fully control filtering so the "add new" affordance shows.
            filter={(value, search) =>
              value.toLowerCase().includes(search.toLowerCase()) ? 1 : 0
            }
          >
            <CommandInput
              value={query}
              onValueChange={setQuery}
              placeholder="Search or add…"
            />
            <CommandList>
              <CommandEmpty>
                {trimmed ? (
                  <button
                    type="button"
                    onClick={handleCreate}
                    className="flex w-full items-center justify-center gap-1 py-1 text-sm"
                  >
                    <PlusIcon className="size-3.5" /> Add “{trimmed}”
                  </button>
                ) : (
                  "No technology found."
                )}
              </CommandEmpty>
              <CommandGroup>
                {technologies.map((t) => (
                  <CommandItem
                    key={t.id}
                    value={t.name}
                    data-checked={selectedIds.includes(t.id)}
                    onSelect={() => toggle(t.id)}
                  >
                    {t.name}
                  </CommandItem>
                ))}
                {trimmed && !exactExists && (
                  <CommandItem
                    value={`__add__${trimmed}`}
                    onSelect={handleCreate}
                  >
                    <PlusIcon className="size-3.5" /> Add “{trimmed}”
                  </CommandItem>
                )}
              </CommandGroup>
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>
    </div>
  );
}
