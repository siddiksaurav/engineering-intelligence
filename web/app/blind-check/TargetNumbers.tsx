import { Panel } from "@/components/Panel";
import { rateWords, type BlindCheckNumbers, type Rate } from "@/lib/blindCheck";
import { percent } from "@/lib/profileText";
import styles from "./blindCheck.module.css";

/** The three numbers of the check, each with its count, its share and its target (design, "The page"). */
export function TargetNumbers({ numbers }: { numbers: BlindCheckNumbers }) {
  const labelsComplete = numbers.labeled === numbers.sampled;
  const judgeable = numbers.sampled - numbers.cannotJudge;
  const labelProgress = `${numbers.labeled} of ${numbers.sampled} labeled so far`;
  const markProgress = `${numbers.reasonMarked} of ${judgeable} marked so far`;
  return (
    <Panel
      title="The three numbers"
      note="A target is judged only when every sampled item counts. With about 30 items the numbers are an indication, not proof."
    >
      <ul className={styles.numbers}>
        <TargetRow
          title="Complexity exact match"
          rate={numbers.exactMatch}
          target={numbers.targets.exactMatch}
          complete={labelsComplete}
          progress={labelProgress}
        />
        <TargetRow
          title="Complexity within one step"
          rate={numbers.withinOneStep}
          target={numbers.targets.withinOneStep}
          complete={labelsComplete}
          progress={labelProgress}
        />
        <TargetRow
          title="The AI's reason is right"
          rate={numbers.reasonRight}
          target={numbers.targets.reasonRight}
          complete={numbers.reasonMarked === judgeable}
          progress={markProgress}
        />
      </ul>
      <p className={styles.aside}>Exact match against the table of spec 6.2: {rateWords(numbers.tableExactMatch)}.</p>
    </Panel>
  );
}

function TargetRow({
  title,
  rate,
  target,
  complete,
  progress,
}: {
  title: string;
  rate: Rate;
  target: number;
  complete: boolean;
  progress: string;
}) {
  const met = rate.share !== null && rate.share >= target;
  return (
    <li className={styles.number}>
      <span className={styles.numberTitle}>{title}</span>
      <strong className={styles.numberValue}>{rateWords(rate)}</strong>
      <span>target {percent(target)}</span>
      {complete ? (
        <span className={styles.verdict} data-met={met}>
          {met ? "target met" : "target not met"}
        </span>
      ) : (
        <span className={styles.progress}>{progress}</span>
      )}
    </li>
  );
}
