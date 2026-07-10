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
import { AppBar } from "@/components/app-bar";
import { SectionHeader } from "@/components/section-header";

// Manager-only admin: invite allowlist, teams + lead/member assignment, work
// types, and the shared technology list. All writes are RLS-gated (manager-only
// policies) on top of the requireRole check here.
export default async function AdminPage() {
  const profile = await requireRole("manager");
  const sb = await createServerSupabase();

  const [teams, profiles, workTypes, technologies] = await Promise.all([
    getAdminTeams(sb),
    getAllProfiles(sb),
    getAllWorkTypes(sb),
    getAllTechnologies(sb),
  ]);

  return (
    <div className="min-h-full">
      <AppBar profile={profile} />
      <main className="mx-auto max-w-6xl px-6 py-8">
        <div className="hero-band mb-10 px-7 pt-8 pb-6">
          <p className="eyebrow text-white/70">Admin</p>
          <h1 className="mt-1.5 text-4xl font-semibold tracking-tight">
            Workspace settings
          </h1>
          <p className="mt-2 text-sm text-white/85">
            Invites, teams, categories &amp; technologies.
          </p>
        </div>

        <div className="flex flex-col gap-12">
          <section>
            <SectionHeader eyebrow="Access" title="Invite a member" />
            <div className="surface p-5">
              <InviteForm teams={teams} />
            </div>
          </section>

          <section>
            <SectionHeader eyebrow="Structure" title="Teams" />
            <TeamManager teams={teams} profiles={profiles} />
          </section>

          <section>
            <SectionHeader eyebrow="Taxonomy" title="Work types" />
            <WorkTypeManager workTypes={workTypes} />
          </section>

          <section>
            <SectionHeader eyebrow="Taxonomy" title="Technologies" />
            <TechManager technologies={technologies} />
          </section>
        </div>
      </main>
    </div>
  );
}
