import { ColorDot, entityInk, entityTint } from "@/components/color-dot";

// Read-only chip for a colored entity (work type or technology) — a category
// or tech tag on a task. Same color source as the distribution bars and
// heatmap, so a task reads the same category color everywhere it appears.
export function EntityChip({
  color,
  children,
}: {
  color: string;
  children: React.ReactNode;
}) {
  return (
    <span
      className="inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-medium"
      style={{ backgroundColor: entityTint(color), color: entityInk(color) }}
    >
      <ColorDot color={color} />
      {children}
    </span>
  );
}
