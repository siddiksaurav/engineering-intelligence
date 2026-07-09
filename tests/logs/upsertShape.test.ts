import { devSession, me } from "../util/session";
import { getOrCreateTodayLog } from "@/lib/logs";

test("getOrCreateTodayLog is idempotent — one row per (developer, day)", async () => {
  const sb = await devSession("dev-upsert@x.com");
  const devId = await me(sb);

  const first = await getOrCreateTodayLog(sb, devId);
  const second = await getOrCreateTodayLog(sb, devId);

  expect(second.id).toBe(first.id);
  expect(first.developer_id).toBe(devId);
  expect(first.status).toBe("draft");

  const { data } = await sb
    .from("daily_logs")
    .select("id")
    .eq("developer_id", devId)
    .eq("log_date", first.log_date);
  expect(data?.length).toBe(1);
});
