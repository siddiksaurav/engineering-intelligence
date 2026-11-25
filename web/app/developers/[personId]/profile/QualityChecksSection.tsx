import { Panel } from "@/components/Panel";
import type { GateName, GateResult } from "@/lib/profile";
import { gateWords, isShownNotScored, sharePoints } from "@/lib/profileText";
import { Drill, ofPullRequests } from "./Drill";
import type { SummariesById } from "./ItemReferences";
import styles from "./profile.module.css";

const CHECKS: { gate: GateName; title: string; noun: string }[] = [
  { gate: "G1", title: "Tests with features", noun: "feature PRs have tests" },
  { gate: "G2", title: "Tests with bug fixes", noun: "bug fix PRs change tests" },
];

const OUTCOME_LABELS: Record<GateResult["outcome"], string> = {
  PASS: "✓ Pass",
  CLOSE_MISS: "! Close miss",
  FAR_MISS: "✗ Far miss",
  UNVERIFIED: "Unverified",
};

const UNVERIFIED_LINE = "G4, escaped defects and sprint commitment: unverified, no data in the pilot";

/** Section 4: gates G1 and G2 as pass, close miss or far miss (decision 291); what has no data is one line. */
export function QualityChecksSection({ gates, summariesById }: { gates: GateResult[]; summariesById: SummariesById }) {
  return (
    <Panel title="4 · Quality checks" note="Checks that can lower a score.">
      <div className={styles.checks}>
        {CHECKS.flatMap(({ gate: name, title, noun }) => {
          const gate = gates.find((candidate) => candidate.gate === name);
          return gate ? [<Check key={name} gate={gate} title={title} noun={noun} summariesById={summariesById} />] : [];
        })}
        <p className={styles.unverifiedLine}>{UNVERIFIED_LINE}</p>
      </div>
    </Panel>
  );
}

function Check({
  gate,
  title,
  noun,
  summariesById,
}: {
  gate: GateResult;
  title: string;
  noun: string;
  summariesById: SummariesById;
}) {
  const verified = gate.outcome !== "UNVERIFIED";
  const detail = verified
    ? `${gate.personTestedPullRequests.length} of ${gate.personPullRequests.length} ${noun} · repository ${sharePoints(gate.repositorySharePercent)}`
    : gateWords(gate).detail;
  const pullRequests: [string, number[]][] = [
    ["Person's tested PRs", gate.personTestedPullRequests],
    ["Person's PRs", gate.personPullRequests],
    ["Repository's tested PRs", gate.repositoryTestedPullRequests],
    ["Repository's PRs", gate.repositoryPullRequests],
    ["PRs without a digest", gate.pullRequestsWithoutDigest],
  ];
  return (
    <article className={styles.check} data-outcome={gate.outcome}>
      <div className={styles.checkHead}>
        <h3 className={styles.checkTitle}>{title}</h3>
        <div className={styles.checkResult}>
          <span className={styles.outcome}>{OUTCOME_LABELS[gate.outcome]}</span>
          {isShownNotScored(gate) && <span className={styles.notScored}>not scored</span>}
        </div>
      </div>
      <p className={styles.checkDetail}>{detail}</p>
      {verified &&
        pullRequests.map(([label, numbers]) => (
          <Drill key={label} label={label} count={ofPullRequests(numbers)} summariesById={summariesById} />
        ))}
    </article>
  );
}
