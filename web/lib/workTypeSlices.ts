import type { DonutSlice } from "@/components/charts/DonutChart";
import { workTypeColor } from "./palette";
import type { Distribution } from "./profile";
import { plainName } from "./profileText";

/** The work types of a distribution as the slices of a donut, each in the colour of its type. */
export function workTypeSlices(distribution: Distribution): DonutSlice[] {
  return distribution.slices.map((slice) => ({
    key: slice.name,
    label: plainName(slice.name),
    value: slice.count.count,
    color: workTypeColor(slice.name),
  }));
}
