import type { ComplexityName, ProblemVerdict } from "./items";

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

/** The verdicts on the depth of a problem, with the words the screens use. */
export const VERDICTS: readonly { verdict: ProblemVerdict; words: string }[] = [
  { verdict: "right", words: "Depth is right" },
  { verdict: "should_be_routine", words: "Should be routine" },
  { verdict: "should_be_answer_exists", words: "Should be answer exists" },
  { verdict: "should_be_no_ready_answer", words: "Should be no ready answer" },
  { verdict: "not_a_problem", words: "Not a real problem of this work" },
];

/** The body of `PUT /api/evidence-items/{id}/reason-mark`. */
export type ReasonMarkRequest = {
  reasonRight: boolean;
  problemMarks: { name: string; verdict: ProblemVerdict }[];
  note: string | null;
};

/** What the reason mark form shows after a save: nothing yet, the saved mark, or why it was refused. */
export type SaveState = { status: "idle" } | { status: "saved" } | { status: "refused"; message: string };

export const REASON_RIGHT_FIELD = "reasonRight";
export const NOTE_FIELD = "note";

/** The name of the radio group that holds the verdict on the problem at this place of the judgment. */
export function verdictField(problemIndex: number): string {
  return `verdict-${problemIndex}`;
}

/** The request the form data stands for, or what is missing. Every problem needs a verdict, in the judgment's order. */
export function reasonMarkRequest(
  formData: FormData,
  problemNames: readonly string[],
): { ok: true; body: ReasonMarkRequest } | { ok: false; message: string } {
  const reasonRight = formData.get(REASON_RIGHT_FIELD);
  if (reasonRight !== "yes" && reasonRight !== "no") {
    return { ok: false, message: 'Answer "The AI\'s reason is right".' };
  }
  const problemMarks: ReasonMarkRequest["problemMarks"] = [];
  for (const [index, name] of problemNames.entries()) {
    const verdict = VERDICTS.find((each) => each.verdict === formData.get(verdictField(index)))?.verdict;
    if (verdict === undefined) {
      return { ok: false, message: `Give a verdict for the problem "${name}".` };
    }
    problemMarks.push({ name, verdict });
  }
  const note = String(formData.get(NOTE_FIELD) ?? "").trim();
  return { ok: true, body: { reasonRight: reasonRight === "yes", problemMarks, note: note === "" ? null : note } };
}
