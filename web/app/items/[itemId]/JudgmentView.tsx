import type { ItemDetail, ItemJudgment } from "@/lib/items";
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
      <p className={styles.tier}>{judgment.tier ?? "No tier"}</p>
      <ul className={styles.values}>
        {valueWords(judgment).map((words) => (
          <li key={words}>{words}</li>
        ))}
      </ul>
      {judgment.reason && <p>{judgment.reason}</p>}
      <Facts facts={judgment.facts} />
      <p className={styles.provenance}>
        Model {judgment.model}, prompt version {judgment.promptVersion}, judged on {judgment.judgedAt?.slice(0, 10)}
      </p>
    </>
  );
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
  return Object.entries(value).map(([name, entry]) => `${capitalised(spaced(name))}: ${spaced(String(entry))}`);
}

/** Very high counts as T3 (spec 6.4), which the reader should see next to the word. */
function complexityWords(complexity: string): string {
  const words = capitalised(spaced(complexity));
  return complexity === "very_high" ? `${words} (counts as T3)` : words;
}

function spaced(name: string): string {
  return name.replaceAll("_", " ");
}

function capitalised(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}
