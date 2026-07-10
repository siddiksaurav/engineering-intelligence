import { cn } from "@/lib/utils";

// Small solid swatch for an entity's own color (work types, technologies are
// colored in the DB — see worktype-distribution.tsx). Decorative only; the
// entity name alongside it always carries the meaning too.
export function ColorDot({
  color,
  className,
}: {
  color: string;
  className?: string;
}) {
  return (
    <span
      aria-hidden
      className={cn("inline-block size-2 shrink-0 rounded-full", className)}
      style={{ backgroundColor: color }}
    />
  );
}

// Soft-filled background for an entity-colored chip. Kept separate from the
// text color formula below: the tint just needs to be faint, not legible.
export function entityTint(color: string): string {
  return `color-mix(in oklch, ${color} 14%, var(--card))`;
}

// Text color for an entity chip. Work-type/technology colors are picked
// freehand by admins (a native <input type="color">, see worktype-manager.tsx)
// so we can't assume any of them meet 4.5:1 against a light tint on their own.
// Majority-blending toward the theme's own --foreground (which is already
// guaranteed to contrast against --card) keeps every chip legible while still
// visibly tinted by the entity's hue, in both light and dark mode.
export function entityInk(color: string): string {
  return `color-mix(in oklch, var(--foreground) 72%, ${color} 28%)`;
}
