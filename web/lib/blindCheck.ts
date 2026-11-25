import type { ComplexityName } from "./items";

/** The complexities from the lowest to the highest; the order the check counts steps in (design flow 7). */
export const COMPLEXITIES: readonly ComplexityName[] = ["low", "medium", "high", "very_high"];

export type LabelAgreement = "same" | "one step apart" | "two or more steps apart";

/** How far the user's label is from the AI's complexity; null when the AI gave no known complexity. */
export function labelAgreement(label: ComplexityName, aiComplexity: unknown): LabelAgreement | null {
  const aiPlace = COMPLEXITIES.findIndex((complexity) => complexity === aiComplexity);
  if (aiPlace < 0) {
    return null;
  }
  const steps = Math.abs(COMPLEXITIES.indexOf(label) - aiPlace);
  if (steps === 0) {
    return "same";
  }
  return steps === 1 ? "one step apart" : "two or more steps apart";
}
