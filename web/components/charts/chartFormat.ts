import { percent } from "@/lib/profileText";

export type ChartUnit = "count" | "percent";

/** A value as the chart and its text summary show it: a count as it is, a share as a whole percentage. */
export function formatValue(value: number | null, unit: ChartUnit): string {
  if (value === null) {
    return "no data";
  }
  return unit === "percent" ? percent(value) : String(value);
}
