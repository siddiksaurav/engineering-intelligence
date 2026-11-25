import { Panel } from "@/components/Panel";
import { UploadLabels } from "./UploadLabels";
import styles from "./blindCheck.module.css";

/** The steps of the labeling and the download of the file. */
export function GetTheFile() {
  return (
    <Panel title="Get the file">
      <ol className={styles.steps}>
        <li>
          <a href="/blind-check/sample.csv" download>
            Download the file
          </a>
          .
        </li>
        <li>
          Fill in the <code>complexity</code> column: low, medium, high, very_high or cannot_judge.
        </li>
        <li>Upload the file here.</li>
      </ol>
      <UploadLabels />
      <p className={styles.warning}>
        Do not open profiles, the developers list or item pages before your labels are imported: they show the AI&apos;s
        answer.
      </p>
    </Panel>
  );
}
