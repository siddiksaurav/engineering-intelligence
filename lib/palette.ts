// Default color for newly created entities (technologies) that don't get an
// explicit color at creation time. Cycling through the DESIGN.md chart
// palette (sky/teal/amber/rose/slate + two extra hues) instead of a single
// flat gray keeps distribution charts readable without requiring an admin to
// manually recolor every entry (see DESIGN.md's No-Gray-Only Rule).
const ENTITY_COLOR_PALETTE = [
  "#0085cb", // sky
  "#00b1aa", // teal
  "#efa810", // amber
  "#ec3d5f", // rose
  "#5b748e", // slate
  "#8451c9", // violet
  "#89c240", // lime
];

// Preferred when the entity's position among its siblings is known (e.g. the
// admin list's current row count) — round-robins evenly instead of risking
// hash collisions clustering several items onto the same color.
export function paletteColorForIndex(index: number): string {
  const i = ((index % ENTITY_COLOR_PALETTE.length) + ENTITY_COLOR_PALETTE.length) %
    ENTITY_COLOR_PALETTE.length;
  return ENTITY_COLOR_PALETTE[i];
}

// Fallback for call sites without a cheap index (e.g. a one-off insert that
// doesn't want an extra count query) — deterministic from the name alone.
export function paletteColorFor(name: string): string {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = (hash * 31 + name.charCodeAt(i)) | 0;
  }
  return paletteColorForIndex(Math.abs(hash));
}

export { ENTITY_COLOR_PALETTE };
