// Typed read helpers. Each takes an RLS-scoped client so the caller's session
// decides what rows come back.
import type { SupabaseClient } from "@supabase/supabase-js";
import type {
  DailyLog,
  ItemStatus,
  LogItem,
  LogItemWithTech,
  LogStatus,
  Profile,
  Technology,
  WorkType,
} from "./types";

export async function getWorkTypes(sb: SupabaseClient): Promise<WorkType[]> {
  const { data, error } = await sb
    .from("work_types")
    .select("*")
    .eq("active", true)
    .order("sort_order", { ascending: true });
  if (error) throw error;
  return (data ?? []) as WorkType[];
}

export async function getTechnologies(sb: SupabaseClient): Promise<Technology[]> {
  const { data, error } = await sb
    .from("technologies")
    .select("*")
    .eq("active", true)
    .order("name", { ascending: true });
  if (error) throw error;
  return (data ?? []) as Technology[];
}

// Developer's day history over an inclusive [from, to] date range (YYYY-MM-DD).
export async function getDeveloperHistory(
  sb: SupabaseClient,
  devId: string,
  from: string,
  to: string
): Promise<DailyLog[]> {
  const { data, error } = await sb
    .from("daily_logs")
    .select("*")
    .eq("developer_id", devId)
    .gte("log_date", from)
    .lte("log_date", to)
    .order("log_date", { ascending: false });
  if (error) throw error;
  return (data ?? []) as DailyLog[];
}

// Count of tasks per day for the same range — feeds the /me history rows and,
// later, the Task 6 heatmap.
export async function getDeveloperItemCounts(
  sb: SupabaseClient,
  devId: string,
  from: string,
  to: string
): Promise<Record<string, number>> {
  const { data, error } = await sb
    .from("log_items")
    .select("daily_logs!inner(log_date, developer_id)")
    .eq("daily_logs.developer_id", devId)
    .gte("daily_logs.log_date", from)
    .lte("daily_logs.log_date", to);
  if (error) throw error;
  const counts: Record<string, number> = {};
  for (const row of (data ?? []) as unknown as EmbeddedDayRow[]) {
    for (const d of asDays(row)) counts[d] = (counts[d] ?? 0) + 1;
  }
  return counts;
}

// Dates (YYYY-MM-DD) in range that contain at least one blocked task.
export async function getDeveloperBlockedDates(
  sb: SupabaseClient,
  devId: string,
  from: string,
  to: string
): Promise<Set<string>> {
  const { data, error } = await sb
    .from("log_items")
    .select("daily_logs!inner(log_date, developer_id)")
    .eq("status", "blocked")
    .eq("daily_logs.developer_id", devId)
    .gte("daily_logs.log_date", from)
    .lte("daily_logs.log_date", to);
  if (error) throw error;
  const dates = new Set<string>();
  for (const row of (data ?? []) as unknown as EmbeddedDayRow[]) {
    for (const d of asDays(row)) dates.add(d);
  }
  return dates;
}

// A `log_items` row with its `daily_logs` embed. Supabase types the embed as an
// array; at runtime a to-one relation is a single object, so normalize both.
type EmbeddedDayRow = {
  daily_logs: { log_date: string } | { log_date: string }[] | null;
};
function asDays(row: EmbeddedDayRow): string[] {
  const dl = row.daily_logs;
  if (!dl) return [];
  return (Array.isArray(dl) ? dl : [dl]).map((d) => d.log_date);
}

// ── Lead/manager reads (Task 5) ──────────────────────────────────────────────
// RLS does the scoping: a lead only ever sees their own teams' rows; a manager
// sees the whole org. No developer filtering is needed in the queries.

export interface TeamMemberBrief {
  id: string;
  full_name: string | null;
  email: string;
}

export interface TeamDay {
  id: string;
  developer_id: string;
  log_date: string;
  status: LogStatus;
  approved_by: string | null;
  approved_at: string | null;
  developer: TeamMemberBrief;
  items: LogItemWithTech[];
}

export interface BlockedTask {
  id: string;
  description: string;
  blocker_note: string | null;
  log_date: string;
  developer: TeamMemberBrief;
  technology_ids: string[];
}

// Supabase types to-one embeds as arrays; normalize to the single runtime object.
function one<T>(embed: T | T[] | null): T | null {
  if (!embed) return null;
  return Array.isArray(embed) ? (embed[0] ?? null) : embed;
}
function techIds(rows: { technology_id: string }[] | null): string[] {
  return (rows ?? []).map((r) => r.technology_id);
}

// Submitted days awaiting the lead's approval, each with its developer and the
// full task list (for the expandable day review).
export async function getTeamPendingLogs(sb: SupabaseClient): Promise<TeamDay[]> {
  const { data, error } = await sb
    .from("daily_logs")
    .select(
      "id, developer_id, log_date, status, approved_by, approved_at, " +
        "developer:profiles!developer_id(id, full_name, email), " +
        "items:log_items(*, log_item_technologies(technology_id))"
    )
    .eq("status", "submitted")
    .order("log_date", { ascending: false });
  if (error) throw error;

  return (data ?? []).map((row): TeamDay => {
    const r = row as unknown as {
      id: string;
      developer_id: string;
      log_date: string;
      status: LogStatus;
      approved_by: string | null;
      approved_at: string | null;
      developer: TeamMemberBrief | TeamMemberBrief[] | null;
      items:
        | (LogItem & { log_item_technologies: { technology_id: string }[] | null })[]
        | null;
    };
    const dev = one(r.developer);
    const items = (r.items ?? [])
      .map((i): LogItemWithTech => {
        const { log_item_technologies, ...rest } = i;
        return { ...rest, technology_ids: techIds(log_item_technologies) };
      })
      .sort((a, b) => a.sort_order - b.sort_order);
    return {
      id: r.id,
      developer_id: r.developer_id,
      log_date: r.log_date,
      status: r.status,
      approved_by: r.approved_by,
      approved_at: r.approved_at,
      developer: dev ?? { id: r.developer_id, full_name: null, email: "" },
      items,
    };
  });
}

// Developers visible to the caller: for a lead, their team members; for a
// manager, every developer. RLS on `profiles` already limits the rows.
export async function getTeamDevelopers(sb: SupabaseClient): Promise<Profile[]> {
  const { data, error } = await sb
    .from("profiles")
    .select("*")
    .eq("role", "developer")
    .order("email", { ascending: true });
  if (error) throw error;
  return (data ?? []) as Profile[];
}

// Every blocked task across the caller's teams, newest day first.
export async function getTeamBlockedTasks(sb: SupabaseClient): Promise<BlockedTask[]> {
  const { data, error } = await sb
    .from("log_items")
    .select(
      "id, description, blocker_note, log_item_technologies(technology_id), " +
        "daily_logs!inner(log_date, developer_id, developer:profiles!developer_id(id, full_name, email))"
    )
    .eq("status", "blocked");
  if (error) throw error;

  const tasks = (data ?? []).map((row): BlockedTask => {
    const r = row as unknown as {
      id: string;
      description: string;
      blocker_note: string | null;
      log_item_technologies: { technology_id: string }[] | null;
      daily_logs:
        | {
            log_date: string;
            developer_id: string;
            developer: TeamMemberBrief | TeamMemberBrief[] | null;
          }
        | {
            log_date: string;
            developer_id: string;
            developer: TeamMemberBrief | TeamMemberBrief[] | null;
          }[]
        | null;
    };
    const day = one(r.daily_logs);
    const dev = one(day?.developer ?? null);
    return {
      id: r.id,
      description: r.description,
      blocker_note: r.blocker_note,
      log_date: day?.log_date ?? "",
      developer: dev ?? {
        id: day?.developer_id ?? "",
        full_name: null,
        email: "",
      },
      technology_ids: techIds(r.log_item_technologies),
    };
  });
  return tasks.sort((a, b) => b.log_date.localeCompare(a.log_date));
}

// ── Dashboards (Task 6) ──────────────────────────────────────────────────────
// One flat, RLS-scoped read of task-level activity feeds every chart: the
// heatmap (task counts per day) and both the work-type and technology
// distributions. A developer only ever gets their own rows (pass their id); a
// lead/manager omits `developerId` and RLS returns just their teams' rows.

export interface ActivityItem {
  item_id: string;
  developer_id: string;
  developer: TeamMemberBrief;
  log_date: string;
  status: ItemStatus;
  hours: number | null;
  work_type_id: string;
  work_type_name: string;
  work_type_color: string;
  technologies: { id: string; name: string; color: string }[];
}

// A single row shaped for `buildDistribution` (lib/aggregate).
export interface DistRow {
  key: string;
  name: string;
  color: string;
  hours: number | null;
}

type WorkTypeEmbed = { id: string; name: string; color: string };
type TechEmbed = { id: string; name: string; color: string };

export async function getActivity(
  sb: SupabaseClient,
  opts: { from: string; to: string; developerId?: string }
): Promise<ActivityItem[]> {
  let q = sb
    .from("log_items")
    .select(
      "id, status, hours, " +
        "work_types!inner(id, name, color), " +
        "log_item_technologies(technologies(id, name, color)), " +
        "daily_logs!inner(log_date, developer_id, developer:profiles!developer_id(id, full_name, email))"
    )
    .gte("daily_logs.log_date", opts.from)
    .lte("daily_logs.log_date", opts.to);
  if (opts.developerId) q = q.eq("daily_logs.developer_id", opts.developerId);

  const { data, error } = await q;
  if (error) throw error;

  return (data ?? []).map((row): ActivityItem => {
    const r = row as unknown as {
      id: string;
      status: ItemStatus;
      hours: number | null;
      work_types: WorkTypeEmbed | WorkTypeEmbed[] | null;
      log_item_technologies:
        | { technologies: TechEmbed | TechEmbed[] | null }[]
        | null;
      daily_logs:
        | {
            log_date: string;
            developer_id: string;
            developer: TeamMemberBrief | TeamMemberBrief[] | null;
          }
        | {
            log_date: string;
            developer_id: string;
            developer: TeamMemberBrief | TeamMemberBrief[] | null;
          }[]
        | null;
    };
    const wt = one(r.work_types);
    const day = one(r.daily_logs);
    const dev = one(day?.developer ?? null);
    const technologies = (r.log_item_technologies ?? [])
      .map((t) => one(t.technologies))
      .filter((t): t is TechEmbed => t !== null);
    return {
      item_id: r.id,
      developer_id: day?.developer_id ?? "",
      developer: dev ?? {
        id: day?.developer_id ?? "",
        full_name: null,
        email: "",
      },
      log_date: day?.log_date ?? "",
      status: r.status,
      hours: r.hours,
      work_type_id: wt?.id ?? "",
      work_type_name: wt?.name ?? "Unknown",
      work_type_color: wt?.color ?? "#64748b",
      technologies,
    };
  });
}

// ── Pure shapers over ActivityItem[] (no I/O) ────────────────────────────────

// Task counts per day, for `buildCalendar`.
export function dayCounts(
  items: ActivityItem[]
): { log_date: string; count: number }[] {
  const m = new Map<string, number>();
  for (const i of items) m.set(i.log_date, (m.get(i.log_date) ?? 0) + 1);
  return [...m.entries()].map(([log_date, count]) => ({ log_date, count }));
}

// Category (work-type) distribution rows: one per task.
export function workTypeDistRows(items: ActivityItem[]): DistRow[] {
  return items.map((i) => ({
    key: i.work_type_id,
    name: i.work_type_name,
    color: i.work_type_color,
    hours: i.hours,
  }));
}

// Technology distribution rows: one per task-tech pair, so a task using N
// technologies contributes to each.
export function techDistRows(items: ActivityItem[]): DistRow[] {
  const rows: DistRow[] = [];
  for (const i of items) {
    for (const t of i.technologies) {
      rows.push({ key: t.id, name: t.name, color: t.color, hours: i.hours });
    }
  }
  return rows;
}

// Teams the caller can see, each with its developer ids — feeds the /team filter
// bar's team select and the in-memory team filter.
export interface TeamOption {
  id: string;
  name: string;
  developerIds: string[];
}

export async function getVisibleTeams(sb: SupabaseClient): Promise<TeamOption[]> {
  const { data, error } = await sb
    .from("teams")
    .select("id, name, team_members(developer_id)")
    .order("name", { ascending: true });
  if (error) throw error;
  return (data ?? []).map((row): TeamOption => {
    const r = row as unknown as {
      id: string;
      name: string;
      team_members: { developer_id: string }[] | null;
    };
    return {
      id: r.id,
      name: r.name,
      developerIds: (r.team_members ?? []).map((m) => m.developer_id),
    };
  });
}

// ── Admin reads (Task 7, manager-only) ───────────────────────────────────────
// RLS returns the whole org to a manager. Unlike getWorkTypes/getTechnologies,
// these include inactive rows so the admin can re-activate or edit them.

export async function getAllProfiles(sb: SupabaseClient): Promise<Profile[]> {
  const { data, error } = await sb
    .from("profiles")
    .select("*")
    .order("email", { ascending: true });
  if (error) throw error;
  return (data ?? []) as Profile[];
}

export async function getAllWorkTypes(sb: SupabaseClient): Promise<WorkType[]> {
  const { data, error } = await sb
    .from("work_types")
    .select("*")
    .order("sort_order", { ascending: true });
  if (error) throw error;
  return (data ?? []) as WorkType[];
}

export async function getAllTechnologies(
  sb: SupabaseClient
): Promise<Technology[]> {
  const { data, error } = await sb
    .from("technologies")
    .select("*")
    .order("name", { ascending: true });
  if (error) throw error;
  return (data ?? []) as Technology[];
}

// Teams with their lead + member ids, for the admin team manager. The UI resolves
// the ids against getAllProfiles.
export interface AdminTeam {
  id: string;
  name: string;
  leadIds: string[];
  memberIds: string[];
}

export async function getAdminTeams(sb: SupabaseClient): Promise<AdminTeam[]> {
  const { data, error } = await sb
    .from("teams")
    .select("id, name, team_leads(lead_id), team_members(developer_id)")
    .order("name", { ascending: true });
  if (error) throw error;
  return (data ?? []).map((row): AdminTeam => {
    const r = row as unknown as {
      id: string;
      name: string;
      team_leads: { lead_id: string }[] | null;
      team_members: { developer_id: string }[] | null;
    };
    return {
      id: r.id,
      name: r.name,
      leadIds: (r.team_leads ?? []).map((l) => l.lead_id),
      memberIds: (r.team_members ?? []).map((m) => m.developer_id),
    };
  });
}
