import { TierChip } from "@/components/TierChip";
import { featureWorkProblems } from "@/lib/featureWorkProblems";
import type { ItemDetail, ItemJudgment } from "@/lib/items";
import { capitalised, spaced } from "@/lib/words";
import { ProblemsSolved } from "./ProblemsSolved";
import styles from "./item.module.css";

/** The current judgment of an item in words: its state, tier, value, reason, facts and where it came from. */
export function JudgmentView({ item }: { item: ItemDetail }) {
  const { judgment } = item;
  if (judgment.state === "not_tiered") {
    return <p className={styles.state}>counted, not tiered</p>;
  }
  if (judgment.state === "not_judged_yet") {
    return <p className={styles.state}>not judged yet</p>;
  }
  return (
    <>
      <div className={styles.verdict}>
        {judgment.tier ? (
          <TierChip tier={judgment.tier} size="large" />
        ) : (
          <span className={styles.noTier}>No tier</span>
        )}
        <ul className={styles.values}>
          {valueWords(judgment).map((words) => (
            <li key={words}>{words}</li>
          ))}
        </ul>
      </div>
      {judgment.reason && <p className={styles.reason}>{judgment.reason}</p>}
      <JudgmentEvidence judgment={judgment} />
      <p className={styles.provenance}>
        Model {judgment.model}, prompt version {judgment.promptVersion}, judged on {judgment.judgedAt?.slice(0, 10)}
      </p>
    </>
  );
}

/** The problems of a v2 Feature work judgment already carry the facts, so the plain list would show them twice. */
function JudgmentEvidence({ judgment }: { judgment: ItemJudgment }) {
  const problems = featureWorkProblems(judgment);
  if (problems) {
    return <ProblemsSolved problems={problems} facts={judgment.facts ?? []} />;
  }
  return <Facts facts={judgment.facts} />;
}

function Facts({ facts }: { facts: ItemJudgment["facts"] }) {
  if (!facts || facts.length === 0) {
    return null;
  }
  return (
    <>
      <h3>Facts</h3>
      <ul className={styles.facts}>
        {facts.map((fact) => (
          <li key={`${fact.link}|${fact.fact}`}>
            <a href={fact.link}>{fact.fact}</a>
          </li>
        ))}
      </ul>
    </>
  );
}

function valueWords(judgment: ItemJudgment): string[] {
  const value = judgment.value ?? {};
  if (judgment.kind === "feature_work_complexity") {
    return [`Complexity: ${complexityWords(String(value.complexity))}`];
  }
  if (judgment.kind === "review_comment") {
    return [`Comment type: ${spaced(String(value.commentType))}`, `Substantive: ${value.substantive ? "yes" : "no"}`];
  }
  return Object.entries(value).map(([name, entry]) => `${capitalised(worded(name))}: ${spaced(String(entry))}`);
}

/** Very high counts as T3 (spec 6.4), which the reader should see next to the word. */
function complexityWords(complexity: string): string {
  const words = capitalised(spaced(complexity));
  return complexity === "very_high" ? `${words} (counts as T3)` : words;
}

/** A field name of the judgment in words: "testKind" becomes "test kind". */
function worded(fieldName: string): string {
  return spaced(fieldName.replace(/([a-z])([A-Z])/g, "$1 $2")).toLowerCase();
}
