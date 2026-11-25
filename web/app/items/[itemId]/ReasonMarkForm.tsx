"use client";

import { useActionState } from "react";
import { NOTE_FIELD, REASON_RIGHT_FIELD, VERDICTS, verdictField, type SaveState } from "@/lib/blindCheck";
import type { ReasonMark } from "@/lib/items";
import { saveReasonMark } from "./actions";
import styles from "./item.module.css";

const NOT_SAVED: SaveState = { status: "idle" };

/** The user's verdict on the AI's reason: right or wrong as a whole, and the depth of each problem. */
export function ReasonMarkForm({
  itemId,
  problemNames,
  stored,
}: {
  itemId: number;
  problemNames: string[];
  stored: ReasonMark | null;
}) {
  const [state, save, pending] = useActionState(saveReasonMark.bind(null, itemId, problemNames), NOT_SAVED);
  return (
    <form action={save} className={styles.markForm}>
      <h3>Mark the AI&apos;s reason</h3>
      <fieldset className={styles.choices}>
        <legend>The AI&apos;s reason is right</legend>
        <Choice name={REASON_RIGHT_FIELD} value="yes" words="Yes" checked={stored?.reasonRight === true} />
        <Choice name={REASON_RIGHT_FIELD} value="no" words="No" checked={stored?.reasonRight === false} />
      </fieldset>
      {problemNames.map((name, index) => (
        <fieldset key={name} className={styles.choices}>
          <legend>{name}</legend>
          {VERDICTS.map(({ verdict, words }) => (
            <Choice
              key={verdict}
              name={verdictField(index)}
              value={verdict}
              words={words}
              checked={stored?.problemMarks[index]?.verdict === verdict}
            />
          ))}
        </fieldset>
      ))}
      <label className={styles.noteField}>
        Note
        <textarea name={NOTE_FIELD} rows={3} defaultValue={stored?.note ?? ""} />
      </label>
      <button type="submit" disabled={pending} className={styles.save}>
        {pending ? "Saving" : "Save"}
      </button>
      {state.status === "saved" && <p role="status">Saved.</p>}
      {state.status === "refused" && (
        <p role="alert" className={styles.refusal}>
          {state.message}
        </p>
      )}
    </form>
  );
}

function Choice({ name, value, words, checked }: { name: string; value: string; words: string; checked: boolean }) {
  return (
    <label className={styles.choice}>
      <input type="radio" name={name} value={value} defaultChecked={checked} required />
      {words}
    </label>
  );
}
