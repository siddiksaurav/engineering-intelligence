import Link from "next/link";
import { requireRole } from "@/lib/auth";
import { createServerSupabase } from "@/lib/supabase/server";
import {
  getAdminTeams,
  getAllProfiles,
  getAllTechnologies,
  getAllWorkTypes,
} from "@/lib/queries";
import { InviteForm } from "@/components/admin/invite-form";
import { TeamManager } from "@/components/admin/team-manager";
import { WorkTypeManager } from "@/components/admin/worktype-manager";
import { TechManager } from "@/components/admin/tech-manager";

// Manager-only admin: invite allowlist, teams + lead/member assignment, work
// types, and the shared technology list. All writes are RLS-gated (manager-only
// policies) on top of the requireRole check here.
export default async function AdminPage() {
  await requireRole("manager");
  const sb = await createServerSupabase();

  const [teams, profiles, workTypes, technologies] = await Promise.all([
    getAdminTeams(sb),
    getAllProfiles(sb),
    getAllWorkTypes(sb),
    getAllTechnologies(sb),
  ]);

  return (
    <main className="mx-auto max-w-4xl p-6">
      <header className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Admin</h1>
          <p className="text-sm text-muted-foreground">
            Invites, teams, categories &amp; technologies
          </p>
        </div>
        <Link
          href="/org"
          className="text-sm text-muted-foreground underline underline-offset-4 hover:text-foreground"
        >
          Org overview
        </Link>
      </header>

      <div className="flex flex-col gap-8">
        <section>
          <h2 className="mb-3 text-sm font-medium text-muted-foreground">
            Invite a member
          </h2>
          <div className="rounded-lg border border-border bg-card p-4">
            <InviteForm teams={teams} />
          </div>
        </section>

        <section>
          <h2 className="mb-3 text-sm font-medium text-muted-foreground">
            Teams
          </h2>
          <TeamManager teams={teams} profiles={profiles} />
        </section>

        <section>
          <h2 className="mb-3 text-sm font-medium text-muted-foreground">
            Work types
          </h2>
          <WorkTypeManager workTypes={workTypes} />
        </section>

        <section>
          <h2 className="mb-3 text-sm font-medium text-muted-foreground">
            Technologies
          </h2>
          <TechManager technologies={technologies} />
        </section>
      </div>
    </main>
  );
}
