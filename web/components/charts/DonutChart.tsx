"use client";

import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import { CHART_SURFACE_COLOR } from "@/lib/palette";
import { ChartFrame } from "./ChartFrame";
import styles from "./charts.module.css";

export type DonutSlice = { key: string; label: string; value: number; color: string };

const PLOT_HEIGHT = 220;

/** One whole split into slices; each slice is listed with its count and share. A total of 0 says no data. */
export function DonutChart({
  label,
  slices,
  centerLabel,
}: {
  label: string;
  slices: DonutSlice[];
  centerLabel?: string;
}) {
  const total = slices.reduce((sum, slice) => sum + slice.value, 0);
  if (total === 0) {
    return (
      <ChartFrame label={label} summary={[`${label}: no data`]}>
        <p className={styles.noData}>No data</p>
      </ChartFrame>
    );
  }
  const drawn = slices.filter((slice) => slice.value > 0);
  const summary = slices.map((slice) => `${slice.label}: ${slice.value} (${Math.round((slice.value / total) * 100)}%)`);
  return (
    <ChartFrame label={label} summary={summary} legend={drawn}>
      <div className={styles.donut}>
        <ResponsiveContainer width="100%" height={PLOT_HEIGHT}>
          <PieChart>
            <Pie
              data={drawn}
              dataKey="value"
              nameKey="label"
              innerRadius="62%"
              outerRadius="92%"
              paddingAngle={2}
              stroke={CHART_SURFACE_COLOR}
              strokeWidth={2}
            >
              {drawn.map((slice) => (
                <Cell key={slice.key} fill={slice.color} />
              ))}
            </Pie>
            <Tooltip />
          </PieChart>
        </ResponsiveContainer>
        {centerLabel !== undefined && <span className={styles.donutCenter}>{centerLabel}</span>}
      </div>
    </ChartFrame>
  );
}
