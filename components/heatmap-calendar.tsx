import type { CalendarCell } from "@/lib/aggregate";

// GitHub-style contribution calendar: one square per day, columns = weeks,
// rows = weekday (Sun→Sat). Intensity is a single-hue sky sequential ramp
// (dataviz skill) via the token-derived `.heat-*` classes in globals.css —
// level 0 recedes to a neutral surface square; 1→4 mix more of the sky accent
// into the card. Dark mode inverts automatically (dark card + bright accent),
// so higher activity stays brighter against the dark surface.
const LEVEL_BG: Record<CalendarCell["level"], string> = {
  0: "heat-0",
  1: "heat-1",
  2: "heat-2",
  3: "heat-3",
  4: "heat-4",
};

const LEGEND_LEVELS: CalendarCell["level"][] = [0, 1, 2, 3, 4];

function weekday(date: string): number {
  // date is YYYY-MM-DD; read it as UTC so the weekday never drifts by timezone.
  const [y, m, d] = date.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d)).getUTCDay(); // 0 = Sun
}

function pretty(date: string): string {
  return new Date(`${date}T00:00:00`).toLocaleDateString(undefined, {
    weekday: "short",
    month: "short",
    day: "numeric",
  });
}

export function HeatmapCalendar({ cells }: { cells: CalendarCell[] }) {
  if (cells.length === 0) {
    return (
      <p className="rounded-lg border border-dashed border-border p-6 text-center text-sm text-muted-foreground">
        No activity in this range.
      </p>
    );
  }

  // Pad the first column so the earliest day lands on its real weekday row.
  const lead = weekday(cells[0].date);

  return (
    <figure className="m-0">
      <div className="overflow-x-auto">
        <div
          className="grid grid-flow-col grid-rows-7 gap-1"
          style={{ width: "max-content" }}
          role="img"
          aria-label="Daily task activity heatmap"
        >
          {Array.from({ length: lead }).map((_, i) => (
            <div key={`pad-${i}`} className="h-3 w-3" aria-hidden />
          ))}
          {cells.map((c) => (
            <div
              key={c.date}
              className={`h-3 w-3 rounded-[3px] ${LEVEL_BG[c.level]}`}
              title={`${pretty(c.date)} · ${c.count} ${
                c.count === 1 ? "task" : "tasks"
              }`}
            />
          ))}
        </div>
      </div>
      <figcaption className="mt-3 flex items-center gap-2 text-xs text-muted-foreground">
        <span>Less</span>
        {LEGEND_LEVELS.map((lvl) => (
          <span
            key={lvl}
            className={`h-3 w-3 rounded-[3px] ${LEVEL_BG[lvl]}`}
            aria-hidden
          />
        ))}
        <span>More</span>
      </figcaption>
    </figure>
  );
}
