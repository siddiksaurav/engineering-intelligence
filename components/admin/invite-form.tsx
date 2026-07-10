"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { inviteMemberAction } from "@/app/actions/admin";
import { nativeSelectClass } from "@/lib/utils";
import type { AppRole } from "@/lib/types";
import type { AdminTeam } from "@/lib/queries";

const ROLES: AppRole[] = ["developer", "lead", "manager"];

// Add an email to the invite allowlist. On first Google sign-in the provisioning
// trigger reads this to create the profile with the chosen role + team.
export function InviteForm({ teams }: { teams: AdminTeam[] }) {
  const router = useRouter();
  const [email, setEmail] = React.useState("");
  const [role, setRole] = React.useState<AppRole>("developer");
  const [teamId, setTeamId] = React.useState("");
  const [message, setMessage] = React.useState<string | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [pending, startTransition] = React.useTransition();

  function submit(e: React.FormEvent) {
    e.preventDefault();
    const clean = email.trim().toLowerCase();
    if (!clean) return;
    setMessage(null);
    setError(null);
    startTransition(async () => {
      try {
        await inviteMemberAction(clean, role, teamId || null);
        setMessage(`Invited ${clean}.`);
        setEmail("");
        router.refresh();
      } catch {
        setError("Could not invite that email.");
      }
    });
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-3">
      <div className="flex flex-wrap items-end gap-3">
        <label className="flex flex-1 flex-col gap-1 text-xs text-muted-foreground">
          Email
          <Input
            type="email"
            required
            placeholder="person@company.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </label>
        <label className="flex flex-col gap-1 text-xs text-muted-foreground">
          Role
          <select
            className={nativeSelectClass}
            value={role}
            onChange={(e) => setRole(e.target.value as AppRole)}
          >
            {ROLES.map((r) => (
              <option key={r} value={r}>
                {r}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-xs text-muted-foreground">
          Team
          <select
            className={nativeSelectClass}
            value={teamId}
            onChange={(e) => setTeamId(e.target.value)}
          >
            <option value="">No team</option>
            {teams.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
        </label>
        <Button type="submit" disabled={pending || !email.trim()}>
          Invite
        </Button>
      </div>
      {message && (
        <p className="text-sm text-[color:color-mix(in_oklch,var(--foreground)_50%,var(--success)_50%)]">
          {message}
        </p>
      )}
      {error && <p className="text-sm text-destructive">{error}</p>}
    </form>
  );
}
