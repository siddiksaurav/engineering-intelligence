import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

// Shared styling for plain native <select> elements (progressive-enhancement
// forms that submit without client JS, e.g. GET filter bars and admin forms)
// — matches the Input/Select component's border, radius, and focus-ring
// language so a native select never looks or behaves like an unstyled
// browser default.
export const nativeSelectClass =
  "h-9 rounded-lg border border-input bg-background px-2.5 text-sm text-foreground shadow-sm transition-colors focus:border-ring focus:outline-none focus:ring-2 focus:ring-ring/25";
