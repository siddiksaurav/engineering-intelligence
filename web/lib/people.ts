/** The people ordered by display name, in a new list. */
export function sortedByName<T extends { displayName: string }>(people: T[]): T[] {
  return [...people].sort((first, second) => first.displayName.localeCompare(second.displayName));
}
