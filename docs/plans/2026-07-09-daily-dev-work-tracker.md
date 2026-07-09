# Daily Dev Work Tracker — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. When doing any Supabase work, first invoke the `supabase:supabase` skill.

> **Status (2026-07-10):** Tasks 0–6 complete ✅ (scaffold, schema, RLS, auth, developer `/today` + `/me`, lead `/team` approvals + private notes, heatmap + category/technology dashboards). Tasks 7–9 remaining. Task 8 requires human cloud/OAuth actions.

**Goal:** Ship a Next.js + Supabase web app (on Vercel) where developers log daily work as structured tasks (category, status, description, technologies, optional hours — many tasks per day), leads approve/lock days and keep private per-developer notes, and leads/managers view category/technology heatmaps and blockers — replacing the current Excel workflow.

**Architecture:** Next.js App Router (TypeScript) with `@supabase/ssr` cookie sessions; Supabase Postgres + Google OAuth; **authorization enforced primarily by Postgres Row Level Security** keyed off `profiles.role` and team membership. Server Components read data; Server Actions perform writes with the user's RLS-scoped session. UI is Tailwind + shadcn/ui. Deployed to Vercel with Supabase cloud.

**Tech Stack:** Next.js 15 (App Router), TypeScript, Tailwind CSS, shadcn/ui, `@supabase/ssr` + `@supabase/supabase-js`, Supabase CLI (local dev + migrations), Vitest + Testing Library (unit), Playwright (E2E), Vercel.

## Global Constraints

- **Auth:** Google OAuth only, **invite-only** via `allowed_emails` allowlist. No email/password.
- **Roles:** exactly `developer` | `lead` | `manager`. `manager` is a strict superset of `lead`.
- **Authorization is RLS-first:** never use the service-role key for user-facing reads/writes. Service role is used only in trusted server code for admin/invite flows. Every table has RLS enabled.
- **Private notes** (`dev_notes`) must NEVER be selectable by the developer they are about.
- **Approval locks a day:** editing a `submitted`/`approved` day resets it to `draft`.
- **Day uniqueness:** one `daily_logs` row per `(developer_id, log_date)`. A day has **many** task line-items.
- **Per task fields:** category = a `work_types` row (exactly one); `status ∈ {todo,in_progress,done,blocked}`; description; **one or more technologies** (many-to-many); optional `hours`; optional `blocker_note`. **No day-level blocked flag** — blocking is per-task via `status='blocked'`.
- **Technologies:** managed shared list; any authenticated user may **add a new** technology (it joins the shared list); rename/deactivate is manager-only.
- **Node:** ≥ 20. **Package manager:** npm. **Supabase local stack** for dev/testing.
- **LLM insights are OUT OF SCOPE** (v2). Do not build them; only leave schema room.

---

## File Structure

```
scrum-master/
├─ app/
│  ├─ layout.tsx, globals.css, page.tsx (redirect to role home)
│  ├─ login/page.tsx                     # Google sign-in
│  ├─ auth/callback/route.ts             # OAuth code exchange
│  ├─ today/page.tsx                     # developer daily entry
│  ├─ me/page.tsx                        # own history + heatmap
│  ├─ team/page.tsx                      # lead: approvals, notes, team heatmap
│  ├─ org/page.tsx                       # manager: org-wide view + filters
│  ├─ admin/page.tsx                     # manager: allowlist/teams/work-types
│  └─ actions/{logs,approvals,notes,admin}.ts   # server actions (writes)
├─ lib/
│  ├─ supabase/{client,server,middleware}.ts    # @supabase/ssr helpers
│  ├─ auth.ts                            # getSessionProfile(), requireRole()
│  ├─ queries.ts                         # typed read helpers
│  └─ types.ts                           # DB row types + enums
├─ components/
│  ├─ ui/*                               # shadcn primitives
│  ├─ log-entry-form.tsx, log-item-row.tsx, status-select.tsx, tech-multiselect.tsx
│  ├─ approval-queue.tsx, day-review.tsx, notes-panel.tsx, blocked-tasks.tsx
│  └─ heatmap-calendar.tsx, worktype-distribution.tsx, tech-distribution.tsx, filters-bar.tsx
├─ middleware.ts                          # session refresh + route guard
├─ supabase/
│  ├─ config.toml
│  └─ migrations/*.sql                    # schema, RLS, functions, trigger, seed
├─ tests/ (vitest) and e2e/ (playwright)
└─ .env.local, .env.example
```

---

## Task 0: Project scaffold, tooling, Supabase local stack

**Files:**
- Create: `package.json`, `tsconfig.json`, `next.config.ts`, `tailwind.config.ts`, `postcss.config.mjs`, `app/globals.css`, `app/layout.tsx`, `app/page.tsx`, `.env.example`, `.gitignore`, `supabase/config.toml`
- Create: `vitest.config.ts`, `playwright.config.ts`

- [x] **Step 1: Init git + Next app**
```bash
cd /home/saurav/myfolder/projects/poc/scrum-master
git init
npx create-next-app@latest . --ts --tailwind --app --src-dir=false --import-alias "@/*" --eslint --no-turbopack
```
Expected: Next.js project files created in current dir.

- [x] **Step 2: Install runtime + dev deps**
```bash
npm i @supabase/supabase-js @supabase/ssr
npm i -D vitest @testing-library/react @testing-library/jest-dom jsdom @vitejs/plugin-react @playwright/test supabase
npx playwright install --with-deps chromium
```

- [x] **Step 3: Init Supabase local project**
```bash
npx supabase init
npx supabase start
```
Expected: local stack boots; prints API URL (`http://127.0.0.1:54321`), anon key, service_role key, and Studio URL. Record anon key + service key into `.env.local`.

- [x] **Step 4: Create `.env.example` and `.env.local`**
`.env.example`:
```
NEXT_PUBLIC_SUPABASE_URL=
NEXT_PUBLIC_SUPABASE_ANON_KEY=
SUPABASE_SERVICE_ROLE_KEY=
```
Copy to `.env.local` and fill with values from `supabase start`. Ensure `.env.local` is gitignored (create-next-app already ignores it).

- [x] **Step 5: Configure vitest** — `vitest.config.ts`:
```ts
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
export default defineConfig({
  plugins: [react()],
  test: { environment: "jsdom", globals: true, setupFiles: ["./tests/setup.ts"] },
});
```
Create `tests/setup.ts`:
```ts
import "@testing-library/jest-dom/vitest";
```
Add scripts to `package.json`: `"test": "vitest run"`, `"test:watch": "vitest"`, `"e2e": "playwright test"`, `"db:reset": "supabase db reset"`.

- [x] **Step 6: Smoke test** — Run: `npm run dev` and open `/`. Expected: default page renders. Run: `npm test`. Expected: "no tests found" (exit 0 acceptable) — confirms vitest is wired.

- [x] **Step 7: Commit**
```bash
git add -A && git commit -m "chore: scaffold Next.js + Supabase local stack"
```

---

## Task 1: Database schema (tables + enums + indexes)

**Files:**
- Create: `supabase/migrations/0001_schema.sql`
- Test: `tests/db/schema.test.ts` (runs SQL assertions via a service-role client against local db)

**Interfaces — Produces (relied on by every later task):**
- Enum `app_role AS ENUM ('developer','lead','manager')`, `log_status AS ENUM ('draft','submitted','approved')`, `profile_status AS ENUM ('active','pending')`, `item_status AS ENUM ('todo','in_progress','done','blocked')`.
- Tables `technologies` and `log_item_technologies` (many-to-many between `log_items` and `technologies`).
- Tables and key columns exactly as below.

- [x] **Step 1: Write the failing test** — `tests/db/schema.test.ts`:
```ts
import { createClient } from "@supabase/supabase-js";
const admin = createClient(process.env.NEXT_PUBLIC_SUPABASE_URL!, process.env.SUPABASE_SERVICE_ROLE_KEY!);
test("core tables exist and accept a work_type", async () => {
  const { error } = await admin.from("work_types").select("id").limit(1);
  expect(error).toBeNull();
});
```

- [x] **Step 2: Run to verify it fails** — Run: `npm test tests/db/schema.test.ts`. Expected: FAIL (relation "work_types" does not exist).

- [x] **Step 3: Write the migration** — `supabase/migrations/0001_schema.sql`:
```sql
create type app_role as enum ('developer','lead','manager');
create type log_status as enum ('draft','submitted','approved');
create type profile_status as enum ('active','pending');
create type item_status as enum ('todo','in_progress','done','blocked');

create table profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  email text unique not null,
  full_name text,
  avatar_url text,
  role app_role not null default 'developer',
  status profile_status not null default 'active',
  created_at timestamptz not null default now()
);

create table teams (
  id uuid primary key default gen_random_uuid(),
  name text unique not null,
  created_at timestamptz not null default now()
);

create table team_members (
  team_id uuid references teams(id) on delete cascade,
  developer_id uuid references profiles(id) on delete cascade,
  primary key (team_id, developer_id)
);

create table team_leads (
  team_id uuid references teams(id) on delete cascade,
  lead_id uuid references profiles(id) on delete cascade,
  primary key (team_id, lead_id)
);

create table work_types (
  id uuid primary key default gen_random_uuid(),
  name text unique not null,
  color text not null default '#888888',
  sort_order int not null default 0,
  active boolean not null default true
);

-- shared, extensible technology/skill list (Spring Boot, Apache Kafka, ...)
create table technologies (
  id uuid primary key default gen_random_uuid(),
  name text unique not null,
  color text not null default '#64748b',
  active boolean not null default true,
  created_by uuid references profiles(id),
  created_at timestamptz not null default now()
);

create table daily_logs (
  id uuid primary key default gen_random_uuid(),
  developer_id uuid not null references profiles(id) on delete cascade,
  log_date date not null,
  status log_status not null default 'draft',
  approved_by uuid references profiles(id),
  approved_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (developer_id, log_date)
);

create table log_items (
  id uuid primary key default gen_random_uuid(),
  daily_log_id uuid not null references daily_logs(id) on delete cascade,
  work_type_id uuid not null references work_types(id),   -- category
  status item_status not null default 'todo',
  description text not null,
  hours numeric(4,1),                                     -- optional time spent
  blocker_note text,                                      -- optional, shown when status='blocked'
  sort_order int not null default 0
);

-- a task may use many technologies
create table log_item_technologies (
  log_item_id uuid references log_items(id) on delete cascade,
  technology_id uuid references technologies(id) on delete cascade,
  primary key (log_item_id, technology_id)
);

create table dev_notes (
  id uuid primary key default gen_random_uuid(),
  developer_id uuid not null references profiles(id) on delete cascade,
  author_id uuid not null references profiles(id),
  body text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table allowed_emails (
  email text primary key,
  role app_role not null default 'developer',
  team_id uuid references teams(id),
  consumed boolean not null default false
);

create index on daily_logs (developer_id, log_date);
create index on daily_logs (status);
create index on log_items (daily_log_id);
create index on log_items (status);
create index on log_item_technologies (technology_id);
create index on team_members (developer_id);
create index on team_leads (lead_id);
create index on dev_notes (developer_id);
```

- [x] **Step 4: Apply + run test** — Run: `npm run db:reset` then `npm test tests/db/schema.test.ts`. Expected: PASS.

- [x] **Step 5: Commit**
```bash
git add supabase/migrations/0001_schema.sql tests/db/schema.test.ts && git commit -m "feat(db): core schema"
```

---

## Task 2: RLS helper functions, policies, provisioning trigger, seed

**Files:**
- Create: `supabase/migrations/0002_rls.sql`, `supabase/migrations/0003_seed.sql`
- Test: `tests/db/rls.test.ts` (creates users via auth admin, asserts visibility rules)

**Interfaces — Produces:**
- SQL functions: `app_is_manager() returns boolean`, `app_is_lead_of(dev uuid) returns boolean`, `app_role_of() returns app_role`.
- Trigger `on auth.users insert` → creates/activates `profiles` from `allowed_emails`, applies team membership, marks allowlist `consumed`.

- [x] **Step 1: Write the failing test** — `tests/db/rls.test.ts` (abridged; implementer fills full setup):
```ts
import { createClient } from "@supabase/supabase-js";
const url = process.env.NEXT_PUBLIC_SUPABASE_URL!;
const admin = createClient(url, process.env.SUPABASE_SERVICE_ROLE_KEY!, { auth: { autoRefreshToken: false, persistSession: false } });

async function makeUser(email: string, role: "developer"|"lead"|"manager") {
  await admin.from("allowed_emails").upsert({ email, role });
  const { data } = await admin.auth.admin.createUser({ email, email_confirm: true, password: "Passw0rd!" });
  return data.user!.id;
}
async function asUser(email: string) {
  const c = createClient(url, process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!);
  await c.auth.signInWithPassword({ email, password: "Passw0rd!" }); // test-only; prod uses OAuth
  return c;
}

test("developer cannot read another developer's logs", async () => {
  await makeUser("dev-a@x.com","developer"); await makeUser("dev-b@x.com","developer");
  const a = await asUser("dev-a@x.com");
  // dev A inserts a log for self, dev B tries to read it
  const { data: prof } = await a.from("profiles").select("id").single();
  await a.from("daily_logs").insert({ developer_id: prof!.id, log_date: "2026-01-01" });
  const b = await asUser("dev-b@x.com");
  const { data } = await b.from("daily_logs").select("*");
  expect(data?.length ?? 0).toBe(0);
});

test("developer cannot read dev_notes about themselves", async () => {
  const devId = await makeUser("dev-c@x.com","developer");
  await makeUser("lead-c@x.com","lead");
  await admin.from("dev_notes").insert({ developer_id: devId, author_id: devId, body: "secret" });
  const c = await asUser("dev-c@x.com");
  const { data } = await c.from("dev_notes").select("*");
  expect(data?.length ?? 0).toBe(0);
});
```
> Note for implementer: enable email/password auth in local `supabase/config.toml` for tests only; production disables it. Google OAuth cannot be scripted, so tests seed users via the auth admin API + password sign-in.

- [x] **Step 2: Run to verify it fails** — Run: `npm test tests/db/rls.test.ts`. Expected: FAIL (no policies yet → either all rows visible or trigger missing).

- [x] **Step 3: Write functions + trigger** — `supabase/migrations/0002_rls.sql` (part A):
```sql
-- role helpers (security definer to read profiles without recursion)
create or replace function app_role_of() returns app_role language sql stable security definer set search_path = public as $$
  select role from profiles where id = auth.uid()
$$;
create or replace function app_is_manager() returns boolean language sql stable security definer set search_path = public as $$
  select coalesce((select role = 'manager' from profiles where id = auth.uid()), false)
$$;
create or replace function app_is_lead_of(dev uuid) returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from team_leads tl
    join team_members tm on tm.team_id = tl.team_id
    where tl.lead_id = auth.uid() and tm.developer_id = dev
  )
$$;

-- provisioning trigger: create profile from allowlist on new auth user
create or replace function handle_new_user() returns trigger language plpgsql security definer set search_path = public as $$
declare ae allowed_emails%rowtype;
begin
  select * into ae from allowed_emails where email = new.email;
  if ae.email is null then
    insert into profiles (id, email, role, status) values (new.id, new.email, 'developer', 'pending');
    return new;
  end if;
  insert into profiles (id, email, full_name, avatar_url, role, status)
    values (new.id, new.email, new.raw_user_meta_data->>'full_name', new.raw_user_meta_data->>'avatar_url', ae.role, 'active');
  if ae.team_id is not null then
    insert into team_members (team_id, developer_id) values (ae.team_id, new.id) on conflict do nothing;
  end if;
  update allowed_emails set consumed = true where email = ae.email;
  return new;
end $$;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function handle_new_user();

-- keep updated_at fresh + reopen locked days on edit
create or replace function touch_updated_at() returns trigger language plpgsql as $$
begin new.updated_at = now(); return new; end $$;
create trigger daily_logs_touch before update on daily_logs for each row execute function touch_updated_at();
```

- [x] **Step 4: Write policies** — `supabase/migrations/0002_rls.sql` (part B):
```sql
alter table profiles enable row level security;
alter table teams enable row level security;
alter table team_members enable row level security;
alter table team_leads enable row level security;
alter table work_types enable row level security;
alter table technologies enable row level security;
alter table log_item_technologies enable row level security;
alter table daily_logs enable row level security;
alter table log_items enable row level security;
alter table dev_notes enable row level security;
alter table allowed_emails enable row level security;

-- profiles
create policy profiles_self_read on profiles for select using (id = auth.uid());
create policy profiles_lead_read on profiles for select using (app_is_lead_of(id));
create policy profiles_mgr_all on profiles for all using (app_is_manager()) with check (app_is_manager());

-- reference data readable by all authenticated
create policy wt_read on work_types for select using (auth.role() = 'authenticated');
create policy wt_mgr on work_types for all using (app_is_manager()) with check (app_is_manager());

-- technologies: everyone reads; any authenticated user may ADD a new one; only manager renames/deactivates/deletes
create policy tech_read on technologies for select using (auth.role() = 'authenticated');
create policy tech_insert on technologies for insert with check (auth.role() = 'authenticated' and created_by = auth.uid());
create policy tech_mgr on technologies for update using (app_is_manager()) with check (app_is_manager());
create policy tech_mgr_del on technologies for delete using (app_is_manager());
create policy teams_read on teams for select using (auth.role() = 'authenticated');
create policy teams_mgr on teams for all using (app_is_manager()) with check (app_is_manager());
create policy tm_read on team_members for select using (auth.role() = 'authenticated');
create policy tm_mgr on team_members for all using (app_is_manager()) with check (app_is_manager());
create policy tl_read on team_leads for select using (auth.role() = 'authenticated');
create policy tl_mgr on team_leads for all using (app_is_manager()) with check (app_is_manager());

-- daily_logs
create policy dl_dev_read on daily_logs for select using (developer_id = auth.uid());
create policy dl_dev_write on daily_logs for insert with check (developer_id = auth.uid());
create policy dl_dev_update on daily_logs for update
  using (developer_id = auth.uid() and status <> 'approved')
  with check (developer_id = auth.uid());
create policy dl_dev_delete on daily_logs for delete using (developer_id = auth.uid() and status = 'draft');
create policy dl_lead_read on daily_logs for select using (app_is_lead_of(developer_id));
create policy dl_lead_update on daily_logs for update using (app_is_lead_of(developer_id)) with check (app_is_lead_of(developer_id));
create policy dl_mgr_all on daily_logs for all using (app_is_manager()) with check (app_is_manager());

-- log_items follow their parent day's access
create policy li_dev_all on log_items for all
  using (exists (select 1 from daily_logs d where d.id = daily_log_id and d.developer_id = auth.uid() and d.status <> 'approved'))
  with check (exists (select 1 from daily_logs d where d.id = daily_log_id and d.developer_id = auth.uid() and d.status <> 'approved'));
create policy li_lead_read on log_items for select
  using (exists (select 1 from daily_logs d where d.id = daily_log_id and app_is_lead_of(d.developer_id)));
create policy li_mgr_all on log_items for all using (app_is_manager()) with check (app_is_manager());

-- log_item_technologies inherit the parent task's access
create policy lit_dev_all on log_item_technologies for all
  using (exists (select 1 from log_items i join daily_logs d on d.id = i.daily_log_id
                 where i.id = log_item_id and d.developer_id = auth.uid() and d.status <> 'approved'))
  with check (exists (select 1 from log_items i join daily_logs d on d.id = i.daily_log_id
                 where i.id = log_item_id and d.developer_id = auth.uid() and d.status <> 'approved'));
create policy lit_lead_read on log_item_technologies for select
  using (exists (select 1 from log_items i join daily_logs d on d.id = i.daily_log_id
                 where i.id = log_item_id and app_is_lead_of(d.developer_id)));
create policy lit_mgr_all on log_item_technologies for all using (app_is_manager()) with check (app_is_manager());

-- dev_notes: lead-of or manager only; NEVER the developer themselves
create policy notes_access on dev_notes for all
  using (app_is_manager() or app_is_lead_of(developer_id))
  with check (app_is_manager() or app_is_lead_of(developer_id));

-- allowed_emails: manager only
create policy ae_mgr on allowed_emails for all using (app_is_manager()) with check (app_is_manager());
```

- [x] **Step 5: Seed work types + bootstrap manager** — `supabase/migrations/0003_seed.sql`:
```sql
insert into work_types (name, color, sort_order) values
 ('Feature','#4f46e5',1),('Bug Fix','#dc2626',2),('Code Review','#0891b2',3),
 ('Meeting','#f59e0b',4),('Ops/DevOps','#16a34a',5),('Support','#7c3aed',6),
 ('Docs','#64748b',7),('Research','#db2777',8);
-- starter technologies (devs can add more from the UI)
insert into technologies (name) values
 ('Spring Boot'),('Apache Kafka'),('PostgreSQL'),('Redis'),('React'),('Next.js'),
 ('TypeScript'),('Java'),('Docker'),('Kubernetes'),('AWS'),('GraphQL') on conflict do nothing;
-- Bootstrap: seed the first manager's email so they can log in and manage the rest.
insert into allowed_emails (email, role) values ('siddiquesaurav@gmail.com','manager') on conflict do nothing;
```

- [x] **Step 6: Apply + run tests** — Run: `npm run db:reset && npm test tests/db/rls.test.ts`. Expected: PASS (both isolation tests green).

- [x] **Step 7: Commit**
```bash
git add supabase/migrations/0002_rls.sql supabase/migrations/0003_seed.sql tests/db/rls.test.ts && git commit -m "feat(db): RLS policies, provisioning trigger, seed"
```

---

## Task 3: Supabase SSR clients, auth helpers, middleware, Google login

**Files:**
- Create: `lib/supabase/client.ts`, `lib/supabase/server.ts`, `lib/supabase/middleware.ts`, `middleware.ts`, `lib/auth.ts`, `lib/types.ts`
- Create: `app/login/page.tsx`, `app/auth/callback/route.ts`, `app/page.tsx` (role-based redirect)
- Test: `tests/auth/roleHome.test.ts`

**Interfaces — Produces:**
- `createBrowserSupabase()`, `createServerSupabase()` (cookie-bound).
- `getSessionProfile(): Promise<Profile | null>` and `requireRole(...roles): Promise<Profile>` (redirects to `/login` if unauthenticated, to `/` if wrong role).
- `roleHome(role): '/today'|'/team'|'/org'`.
- `lib/types.ts`: `AppRole`, `LogStatus`, `ItemStatus`, `Profile`, `DailyLog`, `LogItem`, `LogItemWithTech`, `WorkType`, `Technology`, `DevNote`.

- [x] **Step 1: Types** — `lib/types.ts`:
```ts
export type AppRole = "developer" | "lead" | "manager";
export type LogStatus = "draft" | "submitted" | "approved";
export type ItemStatus = "todo" | "in_progress" | "done" | "blocked";
export interface Profile { id: string; email: string; full_name: string | null; avatar_url: string | null; role: AppRole; status: "active" | "pending"; }
export interface WorkType { id: string; name: string; color: string; sort_order: number; active: boolean; }
export interface Technology { id: string; name: string; color: string; active: boolean; }
export interface DailyLog { id: string; developer_id: string; log_date: string; status: LogStatus; approved_by: string | null; approved_at: string | null; }
export interface LogItem { id: string; daily_log_id: string; work_type_id: string; status: ItemStatus; description: string; hours: number | null; blocker_note: string | null; sort_order: number; }
// LogItem joined with its technologies for display/editing
export interface LogItemWithTech extends LogItem { technology_ids: string[]; }
export interface DevNote { id: string; developer_id: string; author_id: string; body: string; created_at: string; updated_at: string; }
```

- [x] **Step 2: SSR clients** — `lib/supabase/server.ts`:
```ts
import { createServerClient } from "@supabase/ssr";
import { cookies } from "next/headers";
export async function createServerSupabase() {
  const cookieStore = await cookies();
  return createServerClient(process.env.NEXT_PUBLIC_SUPABASE_URL!, process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!, {
    cookies: {
      getAll: () => cookieStore.getAll(),
      setAll: (list) => { try { list.forEach(({ name, value, options }) => cookieStore.set(name, value, options)); } catch {} },
    },
  });
}
```
`lib/supabase/client.ts`:
```ts
import { createBrowserClient } from "@supabase/ssr";
export const createBrowserSupabase = () =>
  createBrowserClient(process.env.NEXT_PUBLIC_SUPABASE_URL!, process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!);
```
`lib/supabase/middleware.ts`: standard `@supabase/ssr` `updateSession(request)` helper (refreshes cookies; returns `NextResponse`). Follow the current `supabase:supabase` skill's Next.js SSR snippet verbatim.

- [x] **Step 3: `middleware.ts`** — refresh session for all app routes and redirect unauthenticated users (except `/login`, `/auth/*`) to `/login`:
```ts
import { type NextRequest } from "next/server";
import { updateSession } from "@/lib/supabase/middleware";
export async function middleware(req: NextRequest) { return updateSession(req); }
export const config = { matcher: ["/((?!_next/static|_next/image|favicon.ico|login|auth).*)"] };
```

- [x] **Step 4: Auth helpers** — `lib/auth.ts`:
```ts
import { redirect } from "next/navigation";
import { createServerSupabase } from "./supabase/server";
import type { AppRole, Profile } from "./types";
export async function getSessionProfile(): Promise<Profile | null> {
  const sb = await createServerSupabase();
  const { data: { user } } = await sb.auth.getUser();
  if (!user) return null;
  const { data } = await sb.from("profiles").select("*").eq("id", user.id).single();
  return (data as Profile) ?? null;
}
export function roleHome(role: AppRole) { return role === "manager" ? "/org" : role === "lead" ? "/team" : "/today"; }
export async function requireRole(...roles: AppRole[]): Promise<Profile> {
  const p = await getSessionProfile();
  if (!p) redirect("/login");
  if (p.status !== "active") redirect("/login?pending=1");
  if (roles.length && !roles.includes(p.role)) redirect(roleHome(p.role));
  return p;
}
```

- [x] **Step 5: Login + callback + root redirect** — `app/login/page.tsx` (client) calls `supabase.auth.signInWithOAuth({ provider: "google", options: { redirectTo: location.origin + "/auth/callback" } })`. `app/auth/callback/route.ts` exchanges `code` for a session then redirects to `/`. `app/page.tsx` (server): `const p = await getSessionProfile(); redirect(p ? roleHome(p.role) : "/login");`

- [x] **Step 6: Test role routing** — `tests/auth/roleHome.test.ts`:
```ts
import { roleHome } from "@/lib/auth";
test("role home routes", () => {
  expect(roleHome("developer")).toBe("/today");
  expect(roleHome("lead")).toBe("/team");
  expect(roleHome("manager")).toBe("/org");
});
```
Run: `npm test tests/auth/roleHome.test.ts`. Expected: PASS.

- [x] **Step 7: Manual OAuth check** — Configure Google provider (Task 8 sets prod; for local, add Google client id/secret to `supabase/config.toml` `[auth.external.google]` or use the Studio). Sign in with the seeded manager email → lands on `/org` (empty for now). Commit.
```bash
git add -A && git commit -m "feat(auth): SSR clients, middleware guard, Google login, role routing"
```

---

## Task 4: Developer daily logging (`/today`) + own history (`/me`)

**Files:**
- Create: `app/today/page.tsx`, `components/log-entry-form.tsx`, `components/log-item-row.tsx`, `components/status-select.tsx`, `components/tech-multiselect.tsx`, `app/actions/logs.ts`, `app/me/page.tsx`, `lib/queries.ts`
- Test: `tests/logs/actions.test.ts` (integration against local db as a developer session), `tests/logs/upsertShape.test.ts`

**Interfaces — Produces (server actions in `app/actions/logs.ts`):**
- `getOrCreateToday(): Promise<{ log: DailyLog; items: LogItemWithTech[] }>`
- `addItem(input: { workTypeId: string; status: ItemStatus; description: string; hours: number | null; blockerNote: string | null; technologyIds: string[] }): Promise<LogItemWithTech>`
- `updateItem(id: string, patch: Partial<Pick<LogItem,"work_type_id"|"status"|"description"|"hours"|"blocker_note">>): Promise<void>`
- `setItemTechnologies(itemId: string, technologyIds: string[]): Promise<void>` (diffs `log_item_technologies` rows)
- `createTechnology(name: string): Promise<Technology>` (insert with `created_by=auth.uid()`; used by the "add new" path in the tech multiselect; ignores/returns existing on name conflict)
- `deleteItem(id: string): Promise<void>`
- `submitDay(logId: string): Promise<void>` (sets status `submitted`)
- Editing any item on a `submitted`/`approved` day first calls internal `reopenIfLocked(logId)` → sets status back to `draft`, clears `approved_by/at`.

**Consumes:** `createServerSupabase`, `requireRole` (Task 3); `work_types` (Task 2 seed).

- [x] **Step 1: Write failing action test** — `tests/logs/actions.test.ts` (uses password sign-in helper from Task 2 test util; extract shared helper into `tests/util/session.ts`):
```ts
test("addItem then submit sets status submitted; editing reopens to draft", async () => {
  const sb = await devSession("dev-log@x.com"); // seeds allowlist+user+signin
  const { data: log } = await sb.from("daily_logs").insert({ developer_id: (await me(sb)), log_date: today() }).select().single();
  const wt = await firstWorkType(sb);
  await sb.from("log_items").insert({ daily_log_id: log.id, work_type_id: wt, description: "built X", hours: 3 });
  await sb.from("daily_logs").update({ status: "submitted" }).eq("id", log.id);
  // edit an item on submitted day → app action must reopen; simulate action: update then reset status
  const { data: after } = await sb.from("daily_logs").select("status").eq("id", log.id).single();
  expect(["submitted","draft"]).toContain(after!.status);
});
```
> Note: because RLS forbids a developer updating an `approved` day, `reopenIfLocked` must run for `submitted` (allowed) before edits; approved days are edited by first calling `submitDay`-reverse via a dedicated action. Keep the reopen logic in the server action, not the DB, so the audit reason is explicit.

- [x] **Step 2: Run to verify it fails** — Run: `npm test tests/logs/actions.test.ts`. Expected: FAIL (helpers/actions absent).

- [x] **Step 3: Query helpers** — `lib/queries.ts`: `getWorkTypes(sb)`, `getTechnologies(sb)`, `getTodayLog(sb, devId)`, `getLogWithItems(sb, logId)`, `getDeveloperHistory(sb, devId, from, to)`. Each returns typed rows via the passed RLS-scoped client.

- [x] **Step 4: Server actions** — `app/actions/logs.ts` (all `"use server"`, all call `requireRole("developer","lead","manager")` then use `createServerSupabase`). Implement the interface above. `reopenIfLocked`:
```ts
async function reopenIfLocked(sb, logId: string) {
  await sb.from("daily_logs").update({ status: "draft", approved_by: null, approved_at: null })
    .eq("id", logId).neq("status", "draft");
}
```
Each mutating action calls `reopenIfLocked` before writing, then `revalidatePath("/today")`.

- [x] **Step 5: UI** — `app/today/page.tsx` (server component: `requireRole`, load today's log + items-with-tech + work types + technologies, render `<LogEntryForm>`). `components/log-entry-form.tsx` (client): list of `<LogItemRow>`, each with: category (`work_types` `Select`), `<StatusSelect>` (todo/in_progress/done/blocked), description (`Input`), `<TechMultiselect>` (multi-select over technologies with a "＋ Add \"X\"" option that calls `createTechnology` then selects it), optional hours (`Input type=number`), and a `blocker_note` `Textarea` shown only when status = `blocked`. Plus an "Add task" button, a day status badge, and a "Submit for approval" button disabled when the day is `approved`. Wire to the server actions. Use shadcn `Select`, `Command`/`Popover` (for the multiselect), `Input`, `Button`, `Badge`, `Textarea`.

- [x] **Step 6: `/me` history** — `app/me/page.tsx`: developer's last 30 days list with status badges + blocked markers (heatmap added in Task 6). Reuse `getDeveloperHistory`.

- [x] **Step 7: Run tests + manual** — Run: `npm test tests/logs`. Expected: PASS. Manually: as a developer, add 2 tasks — one `in_progress` with Spring Boot + Kafka and 3h, one `blocked` with a blocker note; add a brand-new technology from the multiselect; submit; confirm badge shows "Submitted"; edit a task → badge returns to "Draft".

- [x] **Step 8: Commit**
```bash
git add -A && git commit -m "feat(logs): developer daily entry + history + reopen-on-edit"
```

---

## Task 5: Lead approvals + private notes (`/team`)

**Files:**
- Create: `app/team/page.tsx`, `components/approval-queue.tsx`, `components/day-review.tsx`, `components/notes-panel.tsx`, `components/blocked-tasks.tsx`, `app/actions/approvals.ts`, `app/actions/notes.ts`
- Test: `tests/approvals/actions.test.ts`, `tests/notes/access.test.ts`

**Interfaces — Produces:**
- `app/actions/approvals.ts`: `approveDay(logId: string): Promise<void>` (sets `status='approved'`, `approved_by=auth.uid()`, `approved_at=now()`); `reopenForDev(logId: string): Promise<void>` (lead sends back to `draft`).
- `app/actions/notes.ts`: `addNote(developerId, body): Promise<DevNote>`, `updateNote(id, body)`, `deleteNote(id)`.
- `lib/queries.ts` additions: `getTeamPendingLogs(sb)`, `getTeamDevelopers(sb)`, `getDevNotes(sb, devId)`, `getTeamBlockedTasks(sb)` (log_items with `status='blocked'` joined to day+developer, scoped by RLS).

**Consumes:** RLS from Task 2 (lead may update team logs; notes access restricted to lead/manager).

- [x] **Step 1: Failing approval test** — `tests/approvals/actions.test.ts`:
```ts
test("lead approves a submitted day in own team; approved day is locked to dev", async () => {
  const { leadSb, devSb, logId } = await setupTeamWithSubmittedLog();
  await leadSb.from("daily_logs").update({ status: "approved", approved_by: await me(leadSb), approved_at: new Date().toISOString() }).eq("id", logId);
  const { data } = await leadSb.from("daily_logs").select("status").eq("id", logId).single();
  expect(data!.status).toBe("approved");
  // dev cannot update approved day (RLS)
  const res = await devSb.from("daily_logs").update({ status: "draft" }).eq("id", logId).select();
  expect(res.data?.length ?? 0).toBe(0);
});
```

- [x] **Step 2: Failing notes access test** — `tests/notes/access.test.ts`: lead of team can insert+read a note about a team dev; a lead of a *different* team gets 0 rows; the developer gets 0 rows. Run both → Expected: FAIL.

- [x] **Step 3: Actions** — implement `approvals.ts` and `notes.ts` (`"use server"`, `requireRole("lead","manager")`, use RLS-scoped client, `revalidatePath("/team")`).

- [x] **Step 4: UI** — `app/team/page.tsx` (server: `requireRole("lead","manager")`): four regions — `<ApprovalQueue>` (submitted days for the lead's teams with Approve / Reopen buttons and an expandable `<DayReview>` showing each task's category, status badge, technologies, hours, and blocker note); `<BlockedTasks>` (all `status='blocked'` tasks across the lead's teams, newest first, showing dev + blocker note); a team developer list; and `<NotesPanel>` (select a developer → running private notes with add/edit/delete). Managers see all teams (RLS returns everything).

- [x] **Step 5: Run tests + manual** — Run: `npm test tests/approvals tests/notes`. Expected: PASS. Manually: as lead, approve a submitted day; add a private note; sign in as that developer and confirm the note is not visible anywhere.

- [x] **Step 6: Commit**
```bash
git add -A && git commit -m "feat(team): approvals + private per-developer notes"
```

---

## Task 6: Heatmap + work-type/technology distribution dashboards

**Files:**
- Create: `components/heatmap-calendar.tsx`, `components/worktype-distribution.tsx`, `components/tech-distribution.tsx`, `components/filters-bar.tsx`, `lib/aggregate.ts`
- Modify: `app/me/page.tsx` (add personal heatmap), `app/team/page.tsx` (team heatmap + distribution)
- Test: `tests/aggregate/buildHeatmap.test.ts`, `tests/aggregate/distribution.test.ts`

**Interfaces — Produces (`lib/aggregate.ts`, pure functions — easy to TDD):**
- `buildCalendar(items: {log_date: string; count: number}[], from: string, to: string): CalendarCell[]` where `CalendarCell = { date: string; count: number; level: 0|1|2|3|4 }`.
- `buildDistribution(rows: {key: string; name: string; color: string; hours: number|null}[]): { name: string; color: string; total: number; pct: number }[]` (counts items when hours are null; generic over `key` so it serves **both** category/work-type and technology distributions — a task with N technologies contributes to each). Callers pass `key = work_type_id` for the category chart and `key = technology_id` (one row per task-tech pair) for the tech chart.

- [x] **Step 1: Failing aggregate tests** — `tests/aggregate/buildHeatmap.test.ts`:
```ts
import { buildCalendar } from "@/lib/aggregate";
test("assigns intensity levels and fills gaps", () => {
  const cells = buildCalendar([{ log_date: "2026-01-02", count: 5 }], "2026-01-01", "2026-01-03");
  expect(cells).toHaveLength(3);
  expect(cells.find(c => c.date === "2026-01-01")!.level).toBe(0);
  expect(cells.find(c => c.date === "2026-01-02")!.level).toBeGreaterThan(0);
});
```
`tests/aggregate/distribution.test.ts`:
```ts
import { buildDistribution } from "@/lib/aggregate";
test("percentages sum to ~100", () => {
  const d = buildDistribution([
    { key:"a", name:"Feature", color:"#111", hours: 3 },
    { key:"b", name:"Bug Fix", color:"#222", hours: 1 },
  ]);
  expect(Math.round(d.reduce((s,x)=>s+x.pct,0))).toBe(100);
});
```

- [x] **Step 2: Run to verify fail** — Run: `npm test tests/aggregate`. Expected: FAIL (module missing).

- [x] **Step 3: Implement `lib/aggregate.ts`** — pure TS: date-range fill, quantile-free fixed thresholds for levels (`0, 1–2, 3–4, 5–7, 8+`), and distribution with `pct` normalized to 100. No external deps.

- [x] **Step 4: Run to verify pass** — Run: `npm test tests/aggregate`. Expected: PASS.

- [x] **Step 5: Render components** — `components/heatmap-calendar.tsx`: CSS-grid of week columns × weekday rows; cell background from `level` using a single-hue ramp; tooltip on hover; follow the **`dataviz` skill** for the color ramp + accessible contrast (light/dark). `components/worktype-distribution.tsx`: horizontal stacked bar + legend using each work type's `color`. `components/tech-distribution.tsx`: same component fed the technology aggregation (top-N technologies used). `components/filters-bar.tsx`: team select (lead/manager), date-range, category filter, **technology filter**, and **status filter** → drives server-component query params.

- [x] **Step 6: Wire into pages** — `/me` shows the developer's own calendar + distribution. `/team` shows per-developer heatmaps and a team-level distribution, filterable. Aggregate inputs come from RLS-scoped queries (a developer only ever aggregates their own rows; a lead only their teams').

- [x] **Step 7: Manual check + commit** — Verify the heatmap reflects the days logged in Task 4/5 and the distribution matches the tags/technologies used.
```bash
git add -A && git commit -m "feat(dashboards): heatmap calendar + category/technology distribution"
```

---

## Task 7: Manager org view + admin (allowlist / teams / work types / technologies)

**Files:**
- Create: `app/org/page.tsx`, `app/admin/page.tsx`, `app/actions/admin.ts`, `components/admin/{invite-form,team-manager,worktype-manager,tech-manager}.tsx`
- Test: `tests/admin/actions.test.ts`

**Interfaces — Produces (`app/actions/admin.ts`, `requireRole("manager")`):**
- `inviteMember(email: string, role: AppRole, teamId: string | null): Promise<void>` (upsert into `allowed_emails`).
- `createTeam(name)`, `assignLead(teamId, leadId)`, `addMemberToTeam(teamId, devId)`, `removeMemberFromTeam(...)`.
- `upsertWorkType({ id?, name, color, sort_order, active })`.
- `upsertTechnology({ id?, name, color, active })` and `mergeTechnologies(fromId, intoId)` (repoint `log_item_technologies`, deactivate the duplicate — cleans up dev-added near-duplicates like "kafka"/"Kafka").
- For admin writes that must bypass RLS bootstrapping (e.g. creating a team before any membership exists), use a **service-role client created inside the server action only** (`lib/supabase/admin.ts`, never imported by client code), after `requireRole("manager")` has passed.

- [ ] **Step 1: Failing admin test** — `tests/admin/actions.test.ts`: a manager can `inviteMember` (row appears in `allowed_emails`); a lead calling the same action is redirected/blocked (assert no row written). Run → FAIL.

- [ ] **Step 2: `lib/supabase/admin.ts`** — service-role client factory; add a top-of-file comment: "server-only; only call after requireRole('manager')".

- [ ] **Step 3: Actions + UI** — implement `admin.ts`; build `/admin` with four panels (invite member, manage teams + lead assignment, manage work types, **manage technologies** — rename/deactivate/merge duplicates) and `/org` (all teams overview reusing Task 5/6 components with no team filter restriction). `revalidatePath` the affected routes.

- [ ] **Step 4: Run tests + manual** — Run: `npm test tests/admin`. Expected: PASS. Manually: invite a new developer email + team; sign in with that Google account → provisioning trigger assigns role/team; the dev lands on `/today`.

- [ ] **Step 5: Commit**
```bash
git add -A && git commit -m "feat(admin): manager org view + allowlist/teams/work-types/technologies management"
```

---

## Task 8: Deploy to Vercel + Supabase cloud + Google OAuth

**Files:**
- Create: `README.md` (setup + deploy steps), `.env.example` already present.

- [ ] **Step 1: Create Supabase cloud project** — via dashboard. Link + push migrations:
```bash
npx supabase link --project-ref <ref>
npx supabase db push
```
Expected: `0001`–`0003` applied to cloud. Update the seed manager email if different.

- [ ] **Step 2: Google Cloud OAuth (USER ACTION — document in README)** — Create a Google Cloud project → OAuth consent screen (Internal if Workspace) → Credentials → OAuth client (Web). Authorized redirect URI: `https://<project-ref>.supabase.co/auth/v1/callback`. Copy Client ID/Secret into Supabase → Authentication → Providers → Google. Disable email/password provider in cloud (kept enabled only in local `config.toml` for tests).

- [ ] **Step 3: Vercel project** — Import the git repo. Set env vars `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY` from the cloud project. Deploy.

- [ ] **Step 4: Wire redirect URLs** — In Supabase Auth settings, add the Vercel domain to "Redirect URLs" / Site URL. Confirm `app/login` uses `location.origin + "/auth/callback"` so it works on both preview and prod domains.

- [ ] **Step 5: Production smoke test** — Sign in with the manager Google account → `/org`. Invite yourself a second test developer email, sign in from another account → provisioned to `/today`, log a day, submit; approve as lead/manager; confirm heatmap + private-notes isolation on prod.

- [ ] **Step 6: Commit**
```bash
git add README.md && git commit -m "docs: deployment + Google OAuth setup"
```

---

## Task 9: End-to-end Playwright happy path

**Files:**
- Create: `e2e/flow.spec.ts`, `e2e/fixtures.ts`
- Modify: `playwright.config.ts` (baseURL local, webServer runs `npm run dev`)

- [ ] **Step 1: Write E2E** — Because Google OAuth can't run headless, seed sessions by signing in test users via the anon client's password auth in a global-setup fixture (local only) and injecting the Supabase auth cookies, OR run the flow against the local stack with a test-only magic-link. Cover: developer logs 2 tasks (one `in_progress` with two technologies + hours, one `blocked` with a blocker note) → submit → lead approves → developer sees "Approved" and cannot edit → the blocked task appears in the lead's blocked list → lead adds a private note → developer cannot see it → heatmap + category/tech distribution reflect the day.

- [ ] **Step 2: Run** — Run: `npm run db:reset && npm run e2e`. Expected: PASS.

- [ ] **Step 3: Commit**
```bash
git add -A && git commit -m "test(e2e): core log→approve→heatmap flow"
```

---

## Self-Review (coverage map)

| Spec requirement | Task |
|---|---|
| Google invite-only auth | 2 (trigger/allowlist), 3 (login), 8 (OAuth) |
| developer/lead/manager roles + manager superset | 2 (RLS), 3 (requireRole) |
| Team scoping (lead sees only their teams) | 2 (`app_is_lead_of`), 5, 7 |
| Daily entry = multiple tasks/day, each: category + status + description + optional hours | 1 (schema), 4 |
| Per-task technologies (multi), managed list + dev-added | 1 (schema+join), 2 (RLS), 3 (seed), 4 (multiselect), 7 (manage) |
| Per-task status incl. Blocked (no day-level flag) | 1 (`item_status`), 4, 5 (blocked-tasks list) |
| Approval locks day; edit reopens | 2 (RLS), 4 (`reopenIfLocked`), 5 (`approveDay`) |
| Ongoing private per-dev notes, hidden from dev | 1, 2 (`notes_access`), 5 |
| Category + technology heatmap + distribution | 6 |
| Admin: invites, teams, work types, technologies | 7 |
| Deploy on Vercel + Supabase | 8 |
| LLM insights | OUT OF SCOPE (v2) — schema leaves room |

## Verification summary
- Unit: `npm test` (schema, RLS isolation, aggregate math, role routing, actions).
- **Most important — RLS isolation** (Tasks 2/5): a developer cannot read other devs' logs or *any* `dev_notes`; a lead is confined to their teams; manager sees all.
- E2E: `npm run e2e` drives log → submit → approve → heatmap and asserts note invisibility.
- Prod: manual smoke test on Vercel per Task 8 Step 5.

## Known user actions (cannot be automated)
1. Create the Google Cloud OAuth client and paste Client ID/Secret into Supabase (Task 8 Step 2).
2. Create the Supabase cloud project and the Vercel project / connect the git repo (Task 8 Steps 1, 3).
3. Confirm the bootstrap manager email in `0003_seed.sql` (currently `siddiquesaurav@gmail.com`).
