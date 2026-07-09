import { createClient } from "@supabase/supabase-js";
const admin = createClient(process.env.NEXT_PUBLIC_SUPABASE_URL!, process.env.SUPABASE_SERVICE_ROLE_KEY!);
test("core tables exist and accept a work_type", async () => {
  const { error } = await admin.from("work_types").select("id").limit(1);
  expect(error).toBeNull();
});
