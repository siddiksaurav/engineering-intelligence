import Link from "next/link";
import { Panel } from "@/components/Panel";
import type { BlindCheckNumbers } from "@/lib/blindCheck";
import { capitalised, spaced } from "@/lib/words";
import styles from "./blindCheck.module.css";

const words = (complexity: string) => capitalised(spaced(complexity));

/** Every item where the label differs or the reason is marked wrong, and the items that could not be judged. */
export function Disagreements({ numbers }: { numbers: BlindCheckNumbers }) {
  const { disagreements, cannotJudgeItems } = numbers;
  return (
    <>
      <Panel
        title="Disagreements"
        note="Items where your label differs from the AI's, or where you marked its reason wrong."
      >
        {disagreements.length === 0 ? (
          <p className={styles.aside}>None so far.</p>
        ) : (
          <ul className={styles.disagreements}>
            {disagreements.map((item) => (
              <li key={item.itemId} className={styles.disagreement}>
                <Link href={`/items/${item.itemId}`}>{item.title}</Link>
                <span className={styles.aside}>{item.developer}</span>
                <p>
                  You: <strong>{words(item.userComplexity)}</strong>. AI: <strong>{words(item.aiComplexity)}</strong>.
                  Table: <strong>{words(item.complexityByTable)}</strong>.
                </p>
                {item.userNote && <p className={styles.quote}>Your note: {item.userNote}</p>}
                <p className={styles.quote}>AI&apos;s reason: {item.aiReason}</p>
                <p>{reasonMarkWords(item.reasonRight)}</p>
                {item.markNote && <p className={styles.quote}>Your note on the reason: {item.markNote}</p>}
              </li>
            ))}
          </ul>
        )}
      </Panel>
      {cannotJudgeItems.length > 0 && (
        <Panel title="Items you could not judge" note="Left out of every number.">
          <ul className={styles.itemLinks}>
            {cannotJudgeItems.map((item) => (
              <li key={item.itemId}>
                <Link href={`/items/${item.itemId}`}>{item.title}</Link>
                {item.note && <span className={styles.aside}> {item.note}</span>}
              </li>
            ))}
          </ul>
        </Panel>
      )}
    </>
  );
}

function reasonMarkWords(reasonRight: boolean | null): string {
  if (reasonRight === null) {
    return "Reason not marked yet.";
  }
  return reasonRight ? "You marked the reason right." : "You marked the reason wrong.";
}
