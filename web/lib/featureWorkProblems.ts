import type { ItemJudgment } from "./items";

export type ProblemDepth = "routine" | "answer_exists" | "no_ready_answer";

/** One problem a piece of Feature work solved, as `feature-work-complexity/v2` stores it in `judgment.value`. */
export type FeatureWorkProblem = {
  name: string;
  depth: ProblemDepth;
  depthReason: string;
  quote: string;
  source: string;
  earlierExample: string;
};

/** The problem part of a `feature-work-complexity/v2` value; a v1 value has none of it. */
export type FeatureWorkProblems = {
  problems: FeatureWorkProblem[];
  problemsAffectEachOther: boolean;
  problemsAffectEachOtherReason: string;
  mistakesStayHidden: boolean;
  mistakesStayHiddenReason: string;
  thinEvidence: boolean;
  complexity: string;
  complexityByTable: string;
};

const PROBLEM_DEPTHS: readonly string[] = ["routine", "answer_exists", "no_ready_answer"];

/** The problem part of the value of a judgment, or null when the judgment lists no problems or a field is wrong. */
export function featureWorkProblems(judgment: ItemJudgment): FeatureWorkProblems | null {
  const value = judgment.value;
  if (judgment.kind !== "feature_work_complexity" || !value || !isProblemsValue(value)) {
    return null;
  }
  return value as unknown as FeatureWorkProblems;
}

function isProblemsValue(value: Record<string, unknown>): boolean {
  const { problems } = value;
  return (
    Array.isArray(problems) &&
    problems.length > 0 &&
    problems.every(isProblem) &&
    ["problemsAffectEachOther", "mistakesStayHidden", "thinEvidence"].every(
      (flag) => typeof value[flag] === "boolean",
    ) &&
    ["problemsAffectEachOtherReason", "mistakesStayHiddenReason", "complexity", "complexityByTable"].every(
      (field) => typeof value[field] === "string",
    )
  );
}

function isProblem(problem: unknown): boolean {
  if (typeof problem !== "object" || problem === null) {
    return false;
  }
  const fields = problem as Record<string, unknown>;
  return (
    typeof fields.depth === "string" &&
    PROBLEM_DEPTHS.includes(fields.depth) &&
    ["name", "depthReason", "quote", "source", "earlierExample"].every((field) => typeof fields[field] === "string")
  );
}
