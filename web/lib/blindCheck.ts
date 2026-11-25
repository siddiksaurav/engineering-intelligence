import { readBackend, sendBackend, writeBackend, type BackendResult } from "./backend";
import type { ComplexityName, ProblemVerdict } from "./items";
import { percent } from "./profileText";

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

/** A count of how many, with its share; the share is unrounded and null when nothing counts. */
export type Rate = { count: number; of: number; share: number | null };

export type BlindCheckNumbers = {
  sampled: number;
  labeled: number;
  cannotJudge: number;
  reasonMarked: number;
  exactMatch: Rate;
  withinOneStep: Rate;
  tableExactMatch: Rate;
  reasonRight: Rate;
  confusion: { label: ComplexityName; ai: ComplexityName; count: number }[];
  problemVerdicts: Record<ProblemVerdict, number>;
  thinEvidence: { items: number; exactMatch: Rate; reasonRight: Rate };
  targets: { exactMatch: number; withinOneStep: number; reasonRight: number };
  reasonsToMark: { itemId: number; title: string }[];
  disagreements: {
    itemId: number;
    title: string;
    developer: string;
    userComplexity: ComplexityName;
    aiComplexity: ComplexityName;
    complexityByTable: ComplexityName;
    userNote: string | null;
    aiReason: string;
    reasonRight: boolean | null;
    markNote: string | null;
  }[];
  cannotJudgeItems: { itemId: number; title: string; note: string | null }[];
};

/** The numbers of the blind check, counted by the backend on every call. Never throws. */
export async function fetchBlindCheck(
  baseUrl: string | undefined,
  fetchFn: typeof fetch = fetch,
): Promise<BackendResult<BlindCheckNumbers>> {
  return readBackend<BlindCheckNumbers>(baseUrl, "/api/blind-check", fetchFn);
}

/** A rate in words: "21 of 28, 75%"; without a share only the count. */
export function rateWords(rate: Rate): string {
  return rate.share === null ? `${rate.count} of ${rate.of}` : `${rate.count} of ${rate.of}, ${percent(rate.share)}`;
}

/** One wrong row of an uploaded file, as the backend names it. */
export type WrongRow = { line: number; itemId: string | null; value: string; problem: string };

/** What the upload form shows after an upload: nothing yet, the counts of the import, or why the file was refused. */
export type UploadState =
  | { status: "idle" }
  | { status: "imported"; labeledNow: number; alreadyLabeled: number; notLabeledYet: number }
  | { status: "refused"; message: string; problems: WrongRow[] };

/** The wrong rows in the body of a refused import; none when the body holds no list of them. */
export function wrongRowsOf(body: unknown): WrongRow[] {
  const problems = typeof body === "object" && body !== null ? (body as { problems?: unknown }).problems : undefined;
  return Array.isArray(problems) ? (problems as WrongRow[]) : [];
}

/** The file of the sample, or why there is none. Picks the sample first, which changes nothing once it exists. */
export type SampleFile =
  { status: "ok"; csv: string } | { status: "refused"; httpStatus: number; message: string } | { status: "down" };

export async function fetchSampleFile(baseUrl: string | undefined, fetchFn: typeof fetch = fetch): Promise<SampleFile> {
  const picked = await writeBackend<unknown>(baseUrl, "/api/blind-check/sample", { method: "POST" }, fetchFn);
  if (picked.status !== "ok") {
    return picked;
  }
  const file = await sendBackend(baseUrl, "/api/blind-check/sample.csv", {}, fetchFn);
  if (file.status === "down") {
    return file;
  }
  if (file.httpStatus < 200 || file.httpStatus > 299) {
    return { status: "refused", httpStatus: 502, message: `The backend answered HTTP ${file.httpStatus} for the file` };
  }
  return { status: "ok", csv: file.body };
}
