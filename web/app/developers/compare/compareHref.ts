import type { Level } from "@/lib/profile";

/** The compare page URL: the two developers when chosen, then the quarter and the level. */
export function compareHref(selection: { a?: number; b?: number; quarter: string; level: Level }): string {
  const query = new URLSearchParams();
  if (selection.a !== undefined) {
    query.set("a", String(selection.a));
  }
  if (selection.b !== undefined) {
    query.set("b", String(selection.b));
  }
  query.set("quarter", selection.quarter);
  query.set("level", selection.level);
  return `/developers/compare?${query}`;
}
