import type { DistributionSlice } from "@/lib/aggregate";

function fmt(total: number): string {
  // Totals are hours-or-counts; drop a trailing ".0" so "4.0" reads "4".
  return Number.isInteger(total) ? String(total) : total.toFixed(1);
}

// Horizontal stacked bar + legend. Each entity brings its own `color` (work
// types and technologies are colored in the DB), so identity follows the entity
// — not a rank-based palette. A 2px surface gap separates fills; the legend
// carries name + share + total so meaning is never color-alone (dataviz skill).
export function DistributionBar({
  slices,
  emptyLabel = "No activity in this range.",
}: {
  slices: DistributionSlice[];
  emptyLabel?: string;
}) {
  if (slices.length === 0) {
    return (
      <p className="rounded-lg border border-dashed border-border p-6 text-center text-sm text-muted-foreground">
        {emptyLabel}
      </p>
    );
  }

  return (
    <figure className="m-0">
      <div className="flex h-3 w-full gap-0.5 overflow-hidden rounded-full bg-muted">
        {slices.map((s) => (
          <div
            key={s.name}
            className="h-full first:rounded-l-full last:rounded-r-full"
            style={{ width: `${s.pct}%`, backgroundColor: s.color }}
            title={`${s.name} · ${Math.round(s.pct)}% (${fmt(s.total)})`}
          />
        ))}
      </div>
      <figcaption className="mt-3 flex flex-col gap-1.5">
        {slices.map((s) => (
          <div
            key={s.name}
            className="flex items-center gap-2 text-sm"
          >
            <span
              className="h-2.5 w-2.5 shrink-0 rounded-full"
              style={{ backgroundColor: s.color }}
              aria-hidden
            />
            <span className="flex-1 truncate text-foreground">{s.name}</span>
            <span className="tabular-nums text-muted-foreground">
              {Math.round(s.pct)}%
            </span>
            <span className="w-10 text-right tabular-nums text-xs text-muted-foreground">
              {fmt(s.total)}
            </span>
          </div>
        ))}
      </figcaption>
    </figure>
  );
}

// Category (work-type) share of tasks/hours.
export function WorkTypeDistribution({ slices }: { slices: DistributionSlice[] }) {
  return (
    <DistributionBar
      slices={slices}
      emptyLabel="No tasks logged in this range."
    />
  );
}
