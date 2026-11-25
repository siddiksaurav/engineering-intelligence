import type { FeatureWorkProblem, FeatureWorkProblems, ProblemDepth } from "@/lib/featureWorkProblems";
import type { JudgmentFact } from "@/lib/items";
import { orderedColor } from "@/lib/palette";
import { capitalised, spaced } from "@/lib/words";
import styles from "./item.module.css";

const DEPTH_WORDS: Record<ProblemDepth, string> = {
  routine: "Routine",
  answer_exists: "Answer exists",
  no_ready_answer: "No ready answer",
};

/**
 * The problems a piece of Feature work solved, each with its depth, and the marks of the item (spec 6.2).
 * The facts of the judgment are one per problem, in the same order, and give each problem its source link.
 */
export function ProblemsSolved({ problems, facts }: { problems: FeatureWorkProblems; facts: JudgmentFact[] }) {
  return (
    <>
      <h3>Problems solved</h3>
      <ul className={styles.problems}>
        {problems.problems.map((problem, position) => (
          <Problem key={`${position}-${problem.name}`} problem={problem} link={facts[position]?.link} />
        ))}
      </ul>
      <ul className={styles.marks}>
        {problems.problemsAffectEachOther && (
          <Mark title="The problems affect each other" reason={problems.problemsAffectEachOtherReason} />
        )}
        {problems.mistakesStayHidden && (
          <Mark title="Mistakes stay hidden" reason={problems.mistakesStayHiddenReason} />
        )}
        {problems.thinEvidence && <li>thin evidence</li>}
      </ul>
      {problems.complexityByTable !== problems.complexity && (
        <p className={styles.tableLine}>
          The table of spec 6.2 would give {capitalised(spaced(problems.complexityByTable))}.
        </p>
      )}
    </>
  );
}

function Problem({ problem, link }: { problem: FeatureWorkProblem; link: string | undefined }) {
  return (
    <li className={styles.problem}>
      <div className={styles.problemHead}>
        <strong>{problem.quote ? problem.name : linked(problem.name, link)}</strong>
        <span className={styles.depth} data-depth={problem.depth} style={{ background: orderedColor(problem.depth) }}>
          {DEPTH_WORDS[problem.depth]}
        </span>
      </div>
      <p>{problem.depthReason}</p>
      {problem.quote && <blockquote>{linked(problem.quote, link)}</blockquote>}
      <small>earlier example not checked</small>
    </li>
  );
}

/** The text as a link when the fact at the problem's position has one. */
function linked(text: string, link: string | undefined) {
  return link ? <a href={link}>{text}</a> : text;
}

function Mark({ title, reason }: { title: string; reason: string }) {
  return (
    <li>
      <strong>{title}.</strong> {reason}
    </li>
  );
}
