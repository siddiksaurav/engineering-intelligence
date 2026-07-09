"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { XIcon } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import {
  addMemberToTeamAction,
  assignLeadAction,
  createTeamAction,
  removeLeadAction,
  removeMemberFromTeamAction,
} from "@/app/actions/admin";
import type { AdminTeam } from "@/lib/queries";
import type { Profile } from "@/lib/types";

const selectCls =
  "h-9 rounded-md border border-border bg-background px-2 text-sm text-foreground";

const label = (p: Profile) => p.full_name || p.email;

// Create teams, then assign leads and developers. Leads may be role lead or
// manager (manager is a superset); members are developers.
export function TeamManager({
  teams,
  profiles,
}: {
  teams: AdminTeam[];
  profiles: Profile[];
}) {
  const router = useRouter();
  const [pending, startTransition] = React.useTransition();
  const [newTeam, setNewTeam] = React.useState("");

  const byId = React.useMemo(
    () => new Map(profiles.map((p) => [p.id, p] as const)),
    [profiles]
  );
  const leadCandidates = profiles.filter(
    (p) => p.role === "lead" || p.role === "manager"
  );
  const developers = profiles.filter((p) => p.role === "developer");

  const run = (fn: () => Promise<unknown>) =>
    startTransition(async () => {
      await fn();
      router.refresh();
    });

  function createTeam(e: React.FormEvent) {
    e.preventDefault();
    const name = newTeam.trim();
    if (!name) return;
    run(async () => {
      await createTeamAction(name);
      setNewTeam("");
    });
  }

  return (
    <div className="flex flex-col gap-4">
      <form onSubmit={createTeam} className="flex items-end gap-2">
        <label className="flex flex-1 flex-col gap-1 text-xs text-muted-foreground">
          New team name
          <Input
            value={newTeam}
            onChange={(e) => setNewTeam(e.target.value)}
            placeholder="Platform"
          />
        </label>
        <Button type="submit" disabled={pending || !newTeam.trim()}>
          Create team
        </Button>
      </form>

      {teams.length === 0 ? (
        <p className="rounded-lg border border-dashed border-border p-6 text-center text-sm text-muted-foreground">
          No teams yet.
        </p>
      ) : (
        <ul className="flex flex-col gap-3">
          {teams.map((team) => {
            const leadIds = new Set(team.leadIds);
            const memberIds = new Set(team.memberIds);
            const addableLeads = leadCandidates.filter((p) => !leadIds.has(p.id));
            const addableDevs = developers.filter((p) => !memberIds.has(p.id));
            return (
              <li
                key={team.id}
                className="rounded-lg border border-border bg-card p-3"
              >
                <p className="font-medium">{team.name}</p>

                <div className="mt-2 grid gap-3 sm:grid-cols-2">
                  <div>
                    <p className="mb-1 text-xs text-muted-foreground">Leads</p>
                    <div className="flex flex-wrap gap-1">
                      {team.leadIds.length === 0 && (
                        <span className="text-xs text-muted-foreground">
                          None
                        </span>
                      )}
                      {team.leadIds.map((id) => (
                        <span
                          key={id}
                          className="inline-flex items-center gap-1 rounded-full border border-border px-2 py-0.5 text-xs"
                        >
                          {byId.get(id) ? label(byId.get(id)!) : "—"}
                          <button
                            type="button"
                            aria-label="Remove lead"
                            className="text-muted-foreground hover:text-destructive"
                            disabled={pending}
                            onClick={() =>
                              run(() => removeLeadAction(team.id, id))
                            }
                          >
                            <XIcon className="size-3" />
                          </button>
                        </span>
                      ))}
                    </div>
                    {addableLeads.length > 0 && (
                      <select
                        className={`${selectCls} mt-2 w-full`}
                        value=""
                        disabled={pending}
                        onChange={(e) =>
                          e.target.value &&
                          run(() => assignLeadAction(team.id, e.target.value))
                        }
                      >
                        <option value="">Add lead…</option>
                        {addableLeads.map((p) => (
                          <option key={p.id} value={p.id}>
                            {label(p)}
                          </option>
                        ))}
                      </select>
                    )}
                  </div>

                  <div>
                    <p className="mb-1 text-xs text-muted-foreground">Members</p>
                    <div className="flex flex-wrap gap-1">
                      {team.memberIds.length === 0 && (
                        <span className="text-xs text-muted-foreground">
                          None
                        </span>
                      )}
                      {team.memberIds.map((id) => (
                        <span
                          key={id}
                          className="inline-flex items-center gap-1 rounded-full border border-border px-2 py-0.5 text-xs"
                        >
                          {byId.get(id) ? label(byId.get(id)!) : "—"}
                          <button
                            type="button"
                            aria-label="Remove member"
                            className="text-muted-foreground hover:text-destructive"
                            disabled={pending}
                            onClick={() =>
                              run(() =>
                                removeMemberFromTeamAction(team.id, id)
                              )
                            }
                          >
                            <XIcon className="size-3" />
                          </button>
                        </span>
                      ))}
                    </div>
                    {addableDevs.length > 0 && (
                      <select
                        className={`${selectCls} mt-2 w-full`}
                        value=""
                        disabled={pending}
                        onChange={(e) =>
                          e.target.value &&
                          run(() =>
                            addMemberToTeamAction(team.id, e.target.value)
                          )
                        }
                      >
                        <option value="">Add member…</option>
                        {addableDevs.map((p) => (
                          <option key={p.id} value={p.id}>
                            {label(p)}
                          </option>
                        ))}
                      </select>
                    )}
                  </div>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
