import type { DistributionSlice } from "@/lib/aggregate";
import { DistributionBar } from "@/components/worktype-distribution";

// Technology share — same stacked bar as the work-type chart, fed the per-tech
// aggregation (a task using N technologies contributes to each).
export function TechDistribution({ slices }: { slices: DistributionSlice[] }) {
  return (
    <DistributionBar
      slices={slices}
      emptyLabel="No technologies tagged in this range."
    />
  );
}
