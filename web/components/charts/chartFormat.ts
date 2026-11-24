import { percent } from "@/lib/profileText";

export type ChartUnit = "count" | "percent";

/** A value as the chart and its text summary show it: a count as it is, a share as a whole percentage. */
export function formatValue(value: number | null, unit: ChartUnit): string {
  if (value === null) {
    return "no data";
  }
  return unit === "percent" ? percent(value) : String(value);
}

const PERCENT_TICKS = [0, 0.25, 0.5, 0.75, 1];

/** The ticks of a value axis: every quarter for shares; undefined leaves the choice to the chart. */
export function axisTicks(unit: ChartUnit): number[] | undefined {
  return unit === "percent" ? PERCENT_TICKS : undefined;
}
