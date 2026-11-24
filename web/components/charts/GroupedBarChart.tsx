"use client";

import { Bar, BarChart, CartesianGrid, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { CHART_AXIS_COLOR, CHART_GRID_COLOR, CHART_REFERENCE_COLOR } from "@/lib/palette";
import { formatValue, type ChartUnit } from "./chartFormat";
import { ChartFrame, type LegendItem } from "./ChartFrame";

export type BarSeries = LegendItem;

/** One group of bars on the x axis; a null value is a series with nothing to show in that group. */
export type BarCategory = { key: string; label: string; values: Record<string, number | null> };

export type ReferenceLineSpec = { value: number; label: string };

const PLOT_HEIGHT = 260;
const AXIS_TICK = { fill: CHART_AXIS_COLOR, fontSize: 12 };

/**
 * Bars side by side per group, one bar per series, with an optional reference line to read them against.
 * Shares are given as 0 to 1 and shown as percentages for the percent unit.
 */
export function GroupedBarChart({
  label,
  series,
  categories,
  unit,
  referenceLine,
}: {
  label: string;
  series: BarSeries[];
  categories: BarCategory[];
  unit: ChartUnit;
  referenceLine?: ReferenceLineSpec;
}) {
  const rows = categories.map((category) => ({ name: category.label, ...category.values }));
  const summary = [
    ...categories.map(
      (category) =>
        `${category.label}: ${series.map((item) => `${item.label} ${formatValue(category.values[item.key] ?? null, unit)}`).join(", ")}`,
    ),
    ...(referenceLine === undefined ? [] : [`${referenceLine.label}: ${formatValue(referenceLine.value, unit)}`]),
  ];
  const format = (value: unknown) => formatValue(typeof value === "number" ? value : null, unit);
  return (
    <ChartFrame label={label} summary={summary} legend={series}>
      <ResponsiveContainer width="100%" height={PLOT_HEIGHT}>
        <BarChart data={rows} barGap={2}>
          <CartesianGrid vertical={false} stroke={CHART_GRID_COLOR} />
          <XAxis dataKey="name" tick={AXIS_TICK} axisLine={false} tickLine={false} />
          <YAxis
            tick={AXIS_TICK}
            axisLine={false}
            tickLine={false}
            tickFormatter={format}
            domain={unit === "percent" ? [0, 1] : [0, "auto"]}
            allowDecimals={false}
          />
          <Tooltip formatter={format} />
          {series.map((item) => (
            <Bar
              key={item.key}
              dataKey={item.key}
              name={item.label}
              fill={item.color}
              radius={[4, 4, 0, 0]}
              maxBarSize={36}
            />
          ))}
          {referenceLine !== undefined && (
            <ReferenceLine
              y={referenceLine.value}
              stroke={CHART_REFERENCE_COLOR}
              strokeDasharray="4 4"
              label={{ value: referenceLine.label, fill: CHART_AXIS_COLOR, fontSize: 12, position: "insideTopRight" }}
            />
          )}
        </BarChart>
      </ResponsiveContainer>
    </ChartFrame>
  );
}
