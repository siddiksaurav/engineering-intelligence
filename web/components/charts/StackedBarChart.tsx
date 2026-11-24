"use client";

import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { CHART_AXIS_COLOR, CHART_SURFACE_COLOR } from "@/lib/palette";
import { ChartFrame, type LegendItem } from "./ChartFrame";

export type StackSegment = LegendItem;

/** One horizontal bar made of segments; a segment that is absent from `values` counts as 0. */
export type StackedBar = { key: string; label: string; values: Record<string, number> };

const BAR_HEIGHT = 28;
const AXIS_WIDTH = 96;
const AXIS_TICK = { fill: CHART_AXIS_COLOR, fontSize: 12 };

/** Horizontal bars, one per row, each split into segments by count. */
export function StackedBarChart({
  label,
  segments,
  bars,
}: {
  label: string;
  segments: StackSegment[];
  bars: StackedBar[];
}) {
  const rows = bars.map((bar) => ({ name: bar.label, ...bar.values }));
  const summary = bars.map((bar) => {
    const parts = segments.map((segment) => `${segment.label} ${bar.values[segment.key] ?? 0}`);
    const total = segments.reduce((sum, segment) => sum + (bar.values[segment.key] ?? 0), 0);
    return `${bar.label}: ${parts.join(", ")}, total ${total}`;
  });
  return (
    <ChartFrame label={label} summary={summary} legend={segments}>
      <ResponsiveContainer width="100%" height={bars.length * BAR_HEIGHT * 1.6 + 32}>
        <BarChart data={rows} layout="vertical">
          <XAxis type="number" tick={AXIS_TICK} axisLine={false} tickLine={false} allowDecimals={false} />
          <YAxis type="category" dataKey="name" width={AXIS_WIDTH} tick={AXIS_TICK} axisLine={false} tickLine={false} />
          <Tooltip />
          {segments.map((segment) => (
            <Bar
              key={segment.key}
              dataKey={segment.key}
              name={segment.label}
              stackId="stack"
              fill={segment.color}
              stroke={CHART_SURFACE_COLOR}
              strokeWidth={2}
              barSize={BAR_HEIGHT}
            />
          ))}
        </BarChart>
      </ResponsiveContainer>
    </ChartFrame>
  );
}
