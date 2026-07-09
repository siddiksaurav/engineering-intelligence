import { createClient, type SupabaseClient } from "@supabase/supabase-js";

const url = process.env.NEXT_PUBLIC_SUPABASE_URL!;
const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!;

// Service-role client — bypasses RLS. Test-only; never ship this pattern.
export const admin = createClient(url, process.env.SUPABASE_SERVICE_ROLE_KEY!, {
  auth: { autoRefreshToken: false, persistSession: false },
});

const PASSWORD = "Passw0rd!";

export async function makeUser(
  email: string,
  role: "developer" | "lead" | "manager"
) {
  const { error: upsertError } = await admin
    .from("allowed_emails")
    .upsert({ email, role });
  if (upsertError) throw upsertError;
  const { data, error } = await admin.auth.admin.createUser({
    email,
    email_confirm: true,
    password: PASSWORD,
  });
  if (error) throw error;
  return data.user!.id;
}

// Sign in as an existing user and return an RLS-scoped anon client.
export async function asUser(email: string): Promise<SupabaseClient> {
  const c = createClient(url, anonKey, {
    auth: { autoRefreshToken: false, persistSession: false },
  });
  const { error } = await c.auth.signInWithPassword({ email, password: PASSWORD }); // test-only; prod uses OAuth
  if (error) throw error;
  return c;
}

// Provision + sign in a developer in one step.
export async function devSession(email: string): Promise<SupabaseClient> {
  await makeUser(email, "developer");
  return asUser(email);
}

export async function me(sb: SupabaseClient): Promise<string> {
  const { data, error } = await sb.from("profiles").select("id").single();
  if (error) throw error;
  return data!.id as string;
}

export async function firstWorkType(sb: SupabaseClient): Promise<string> {
  const { data, error } = await sb.from("work_types").select("id").limit(1).single();
  if (error) throw error;
  return data!.id as string;
}

// Local-date YYYY-MM-DD (matches lib/logs todayISO()).
export function today(): string {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}
