import styles from "./Avatar.module.css";

const TINTS = 5;

/** Two letters that stand for a name: the first letters of its first two parts, or its first two letters. */
export function initialsOf(name: string): string {
  const parts = name.split(/[^\p{L}\p{N}]+/u).filter((part) => part.length > 0);
  if (parts.length === 0) {
    return "?";
  }
  const letters = parts.length === 1 ? parts[0].slice(0, 2) : parts[0][0] + parts[1][0];
  return letters.toUpperCase();
}

/** A stable number from a name, so a developer keeps one tint on every page. */
function tintOf(name: string): number {
  let sum = 0;
  for (const character of name) {
    sum += character.codePointAt(0) ?? 0;
  }
  return sum % TINTS;
}

/** The initials of a developer on a tinted disc. Decoration only: the name is always written next to it. */
export function Avatar({ name, size = "regular" }: { name: string; size?: "regular" | "large" }) {
  return (
    <span className={`${styles.avatar} ${styles[size]} ${styles[`tint${tintOf(name)}`]}`} aria-hidden="true">
      {initialsOf(name)}
    </span>
  );
}
