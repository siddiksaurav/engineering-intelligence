import { createClient } from "@supabase/supabase-js";

const url = process.env.NEXT_PUBLIC_SUPABASE_URL!;
const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!;
const admin = createClient(url, process.env.SUPABASE_SERVICE_ROLE_KEY!, {
  auth: { autoRefreshToken: false, persistSession: false },
});

const PASSWORD = "Passw0rd!";

async function makeUser(email: string, role: "developer" | "lead" | "manager") {
  const { error: upsertError } = await admin.from("allowed_emails").upsert({ email, role });
  if (upsertError) throw upsertError;
  const { data, error } = await admin.auth.admin.createUser({
    email,
    email_confirm: true,
    password: PASSWORD,
  });
  if (error) throw error;
  return data.user!.id;
}

async function asUser(email: string) {
  const c = createClient(url, anonKey, {
    auth: { autoRefreshToken: false, persistSession: false },
  });
  const { error } = await c.auth.signInWithPassword({ email, password: PASSWORD }); // test-only; prod uses OAuth
  if (error) throw error;
  return c;
}

test("developer cannot read another developer's logs", async () => {
  await makeUser("dev-a@x.com", "developer");
  await makeUser("dev-b@x.com", "developer");
  const a = await asUser("dev-a@x.com");
  // dev A inserts a log for self, dev B tries to read it
  const { data: prof } = await a.from("profiles").select("id").single();
  const { error: insertError } = await a
    .from("daily_logs")
    .insert({ developer_id: prof!.id, log_date: "2026-01-01" });
  expect(insertError).toBeNull();
  const b = await asUser("dev-b@x.com");
  const { data } = await b.from("daily_logs").select("*");
  expect(data?.length ?? 0).toBe(0);
});

test("developer cannot read dev_notes about themselves", async () => {
  const devId = await makeUser("dev-c@x.com", "developer");
  await makeUser("lead-c@x.com", "lead");
  const { error: insertError } = await admin
    .from("dev_notes")
    .insert({ developer_id: devId, author_id: devId, body: "secret" });
  expect(insertError).toBeNull();
  const c = await asUser("dev-c@x.com");
  const { data } = await c.from("dev_notes").select("*");
  expect(data?.length ?? 0).toBe(0);
});

test("authorship is pinned to the acting user", async () => {
  const managerId = await makeUser("mgr-d@x.com", "manager");
  const devId = await makeUser("dev-d@x.com", "developer");
  const manager = await asUser("mgr-d@x.com");

  const { error: spoofedError } = await manager
    .from("dev_notes")
    .insert({ developer_id: devId, author_id: devId, body: "spoofed author" });
  expect(spoofedError).not.toBeNull();

  const { error: honestError } = await manager
    .from("dev_notes")
    .insert({ developer_id: devId, author_id: managerId, body: "honest author" });
  expect(honestError).toBeNull();
});

test("developer can read own log items on an approved day", async () => {
  const devId = await makeUser("dev-e@x.com", "developer");
  const dev = await asUser("dev-e@x.com");

  const { data: log, error: logError } = await dev
    .from("daily_logs")
    .insert({ developer_id: devId, log_date: "2026-01-02" })
    .select("id")
    .single();
  expect(logError).toBeNull();

  const { data: workType } = await admin.from("work_types").select("id").limit(1).single();

  const { error: itemError } = await dev
    .from("log_items")
    .insert({ daily_log_id: log!.id, work_type_id: workType!.id, description: "did stuff" });
  expect(itemError).toBeNull();

  const { error: approveError } = await admin
    .from("daily_logs")
    .update({ status: "approved" })
    .eq("id", log!.id);
  expect(approveError).toBeNull();

  const { data: items, error: readError } = await dev
    .from("log_items")
    .select("*")
    .eq("daily_log_id", log!.id);
  expect(readError).toBeNull();
  expect(items?.length ?? 0).toBeGreaterThanOrEqual(1);
});
