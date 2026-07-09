// Pure aggregation helpers for the dashboards (Task 6). No I/O, no deps — the
// data always arrives from RLS-scoped queries, so these functions never decide
// what a caller may see, only how to shape rows they already fetched.

export interface CalendarCell {
  date: string; // YYYY-MM-DD
  count: number;
  level: 0 | 1 | 2 | 3 | 4;
}

// Fixed (quantile-free) intensity thresholds so a single quiet week doesn't wash
// out the ramp: 0 → 0, 1–2 → 1, 3–4 → 2, 5–7 → 3, 8+ → 4.
function levelFor(count: number): CalendarCell["level"] {
  if (count <= 0) return 0;
  if (count <= 2) return 1;
  if (count <= 4) return 2;
  if (count <= 7) return 3;
  return 4;
}

// Parse a YYYY-MM-DD date as UTC midnight so day-stepping never drifts across a
// DST boundary or local timezone.
function parseISO(date: string): number {
  const [y, m, d] = date.split("-").map(Number);
  return Date.UTC(y, m - 1, d);
}

function toISO(ms: number): string {
  const d = new Date(ms);
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getUTCFullYear()}-${p(d.getUTCMonth() + 1)}-${p(d.getUTCDate())}`;
}

const DAY_MS = 24 * 60 * 60 * 1000;

// Every date in the inclusive [from, to] range, gap-filled with count 0, in
// ascending order. Duplicate dates in `items` are summed.
export function buildCalendar(
  items: { log_date: string; count: number }[],
  from: string,
  to: string
): CalendarCell[] {
  const counts = new Map<string, number>();
  for (const it of items) {
    counts.set(it.log_date, (counts.get(it.log_date) ?? 0) + it.count);
  }

  const cells: CalendarCell[] = [];
  const end = parseISO(to);
  for (let t = parseISO(from); t <= end; t += DAY_MS) {
    const date = toISO(t);
    const count = counts.get(date) ?? 0;
    cells.push({ date, count, level: levelFor(count) });
  }
  return cells;
}

export interface DistributionSlice {
  name: string;
  color: string;
  total: number;
  pct: number;
}

// Generic distribution over a grouping `key` — feeds both the work-type/category
// chart (key = work_type_id) and the technology chart (key = technology_id, one
// row per task-tech pair). A row contributes its `hours`, or 1 when hours is
// null (i.e. an untimed task counts once). Slices are returned largest-first and
// `pct` values are normalized to sum to 100.
export function buildDistribution(
  rows: { key: string; name: string; color: string; hours: number | null }[]
): DistributionSlice[] {
  const groups = new Map<string, { name: string; color: string; total: number }>();
  for (const row of rows) {
    const value = row.hours ?? 1;
    const g = groups.get(row.key);
    if (g) {
      g.total += value;
    } else {
      groups.set(row.key, { name: row.name, color: row.color, total: value });
    }
  }

  const grand = [...groups.values()].reduce((s, g) => s + g.total, 0);
  return [...groups.values()]
    .map((g) => ({
      name: g.name,
      color: g.color,
      total: g.total,
      pct: grand > 0 ? (g.total / grand) * 100 : 0,
    }))
    .sort((a, b) => b.total - a.total);
}
