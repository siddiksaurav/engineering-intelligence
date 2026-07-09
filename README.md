# Scrum Master — Daily Dev Work Tracker

A Next.js + Supabase app where developers log daily work as structured tasks
(category, status, description, technologies, optional hours — many tasks per day),
leads approve/lock days and keep private per-developer notes, and leads/managers
view category/technology heatmaps and blockers. Authorization is enforced primarily
by Postgres **Row Level Security**.

- **Stack:** Next.js 15 (App Router, TypeScript), Tailwind + shadcn/ui,
  `@supabase/ssr`, Supabase Postgres + Google OAuth, Vercel.
- **Auth:** Google OAuth **only**, invite-only via an `allowed_emails` allowlist.
- **Roles:** `developer` | `lead` | `manager` (manager is a superset of lead).

## Local development

Prerequisites: Node ≥ 20, npm, Docker (for the Supabase local stack).

```bash
npm install
npx supabase start          # boots local Postgres/Auth; prints URL + keys
cp .env.example .env.local  # then fill with the values supabase start printed
npm run dev                 # http://localhost:3000
```

`.env.local`:

```
NEXT_PUBLIC_SUPABASE_URL=       # e.g. http://127.0.0.1:54321 for local
NEXT_PUBLIC_SUPABASE_ANON_KEY=  # from `supabase start`
SUPABASE_SERVICE_ROLE_KEY=      # from `supabase start` (server-only, never exposed to client)
```

Useful scripts: `npm test` (Vitest), `npm run e2e` (Playwright),
`npm run db:reset` (re-applies migrations + seed to the local DB).

Email/password auth is enabled **only** in the local stack (`supabase/config.toml`)
so the RLS test suite can script sign-ins. Production is Google-only.

## Database migrations

Schema, RLS policies, and seed live in `supabase/migrations/` (`0001`–`0003`).
Applied to a cloud project either with the CLI (`supabase db push`) or, if you
authenticate the Supabase MCP, via its `apply_migration` tool. The seed
(`0003_seed.sql`) bootstraps the first manager by adding their email to
`allowed_emails` — **update this email** to your own before deploying.

---

## Production deployment

### 1. Supabase cloud project

Create a project at [supabase.com](https://supabase.com/dashboard), then link and
push migrations:

```bash
npx supabase link --project-ref <ref>
npx supabase db push        # applies 0001–0003 to the cloud DB
```

Confirm the bootstrap manager email in `0003_seed.sql` is correct.

### 2. Google OAuth setup

The app only allows Google sign-in, so you must create a Google OAuth client and
give it to Supabase.

**A. Create the Google OAuth credentials** — [Google Cloud Console](https://console.cloud.google.com):

1. Create/select a project.
2. **APIs & Services → OAuth consent screen**: choose **External** (or **Internal**
   for a Workspace org), set an app name and support/developer emails.
   - If *External*, the app starts in "Testing" mode — add every Google email that
     will sign in under **Test users** (or publish the consent screen to allow anyone).
3. **APIs & Services → Credentials → Create Credentials → OAuth client ID**:
   - Application type: **Web application**.
   - **Authorized redirect URI** (this is the only redirect Google needs):
     ```
     https://<project-ref>.supabase.co/auth/v1/callback
     ```
4. Copy the generated **Client ID** and **Client Secret**.

**B. Configure Supabase** — dashboard → **Authentication → Providers**:

1. Enable **Google**, paste the Client ID and Client Secret, Save.
2. **Disable the Email provider** (production is Google-only).

> **Note on access:** authenticating with Google is not enough to use the app —
> a `handle_new_user` trigger only provisions users whose email is in
> `allowed_emails`. Others authenticate but stay `pending` until a manager invites
> them from `/admin`. The seeded manager can log in immediately.

**Verifying the config without the app** (optional):

```bash
# Google enabled → 302 redirect to accounts.google.com with your client_id
curl -s -o /dev/null -w "%{http_code} %{redirect_url}\n" \
  "https://<project-ref>.supabase.co/auth/v1/authorize?provider=google"

# Email disabled → 400 {"error_code":"email_provider_disabled"}
curl -s -X POST "https://<project-ref>.supabase.co/auth/v1/signup" \
  -H "apikey: <anon-key>" -H "Content-Type: application/json" \
  -d '{"email":"probe@example.com","password":"probe-only-123"}'
```

### 3. Vercel

Import the git repo into [Vercel](https://vercel.com/new). Set the environment
variables from the **cloud** project:

- `NEXT_PUBLIC_SUPABASE_URL` = `https://<project-ref>.supabase.co`
- `NEXT_PUBLIC_SUPABASE_ANON_KEY`
- `SUPABASE_SERVICE_ROLE_KEY`

Deploy.

### 4. Redirect URLs

In Supabase → **Authentication → URL Configuration**, set the **Site URL** to your
Vercel production domain and add any preview domains under **Redirect URLs**. The
login flow uses `location.origin + "/auth/callback"`, so it works on both preview
and production domains.

### 5. Production smoke test

Sign in with the manager Google account → `/org`. Invite a second developer email
from `/admin`, sign in from that account → provisioned to `/today`, log a day,
submit; approve as lead/manager; confirm the heatmap renders and that private
notes are not visible to the developer they are about.
