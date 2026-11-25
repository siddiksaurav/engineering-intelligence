/** A snake case name with spaces: "very_high" becomes "very high". */
export function spaced(name: string): string {
  return name.replaceAll("_", " ");
}

/** The text with its first letter in upper case. */
export function capitalised(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}
