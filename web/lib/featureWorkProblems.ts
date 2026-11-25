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

/** The problem part of the value of a judgment, or null when the judgment lists no problems. */
export function featureWorkProblems(judgment: ItemJudgment): FeatureWorkProblems | null {
  const problems = judgment.value?.problems;
  if (judgment.kind !== "feature_work_complexity" || !Array.isArray(problems) || problems.length === 0) {
    return null;
  }
  return judgment.value as unknown as FeatureWorkProblems;
}
