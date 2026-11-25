import { Panel } from "@/components/Panel";
import { rateWords, VERDICTS, type BlindCheckNumbers } from "@/lib/blindCheck";
import styles from "./blindCheck.module.css";

/** Your verdicts on the depth of the problems, and the numbers of the items with thin evidence. */
export function ProblemVerdicts({ numbers }: { numbers: BlindCheckNumbers }) {
  const { thinEvidence } = numbers;
  return (
    <Panel title="Verdicts on the problems" note="Counted over every problem you marked.">
      <dl className={styles.verdicts}>
        {VERDICTS.map(({ verdict, words }) => (
          <div key={verdict} className={styles.verdictRow}>
            <dt>{words}</dt>
            <dd>{numbers.problemVerdicts[verdict]}</dd>
          </div>
        ))}
      </dl>
      <h3 className={styles.subtitle}>Thin evidence</h3>
      <p className={styles.aside}>
        {thinEvidence.items} items have thin evidence. Exact match: {rateWords(thinEvidence.exactMatch)}. The AI&apos;s
        reason is right: {rateWords(thinEvidence.reasonRight)}.
      </p>
    </Panel>
  );
}
