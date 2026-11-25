"use client";

import { useActionState } from "react";
import type { UploadState } from "@/lib/blindCheck";
import { uploadLabels } from "./actions";
import styles from "./blindCheck.module.css";

const NOT_UPLOADED: UploadState = { status: "idle" };

/** The form that sends the filled in file to the backend and shows the counts or the wrong rows. */
export function UploadLabels() {
  const [state, upload, pending] = useActionState(uploadLabels, NOT_UPLOADED);
  return (
    <form action={upload} className={styles.upload}>
      <label className={styles.fileField}>
        The file you filled in
        <input type="file" name="labels" accept=".csv,text/csv" required />
      </label>
      <button type="submit" disabled={pending} className={styles.button}>
        {pending ? "Uploading" : "Upload"}
      </button>
      <UploadOutcome state={state} />
    </form>
  );
}

/** What the last upload did: its counts, or every wrong row with the line, the item and the value. */
export function UploadOutcome({ state }: { state: UploadState }) {
  if (state.status === "idle") {
    return null;
  }
  if (state.status === "imported") {
    return (
      <p role="status" className={styles.outcome}>
        {state.labeledNow} labeled now, {state.alreadyLabeled} already labeled, {state.notLabeledYet} not labeled yet.
      </p>
    );
  }
  return (
    <div role="alert" className={styles.refusal}>
      <p>{state.message}</p>
      {state.problems.length > 0 && (
        <ul className={styles.wrongRows}>
          {state.problems.map((row) => (
            <li key={`${row.line}|${row.value}`}>
              <strong>Line {row.line}</strong>
              {row.itemId !== null && <span>, item {row.itemId}</span>}: <code>{row.value}</code>. {row.problem}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
