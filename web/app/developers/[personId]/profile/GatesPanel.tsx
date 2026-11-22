import { Panel } from "@/components/Panel";
import type { GateResult } from "@/lib/profile";
import { gateWords } from "@/lib/profileText";
import { Drill, ofPullRequests } from "./Drill";
import { GateChart } from "./GateChart";
import type { SummariesById } from "./ItemReferences";
import styles from "./profile.module.css";

/** The quality gates of the Engineering standards dimension, once on the page (decision 240). */
export function GatesPanel({ gates, summariesById }: { gates: GateResult[]; summariesById: SummariesById }) {
  return (
    <Panel
      title="Quality gates"
      note="The person's share against the repository's. The green zone passes, the amber zone is a close miss."
    >
      <div className={styles.gates}>
        {gates.map((gate) => (
          <Gate key={gate.gate} gate={gate} summariesById={summariesById} />
        ))}
      </div>
    </Panel>
  );
}

function Gate({ gate, summariesById }: { gate: GateResult; summariesById: SummariesById }) {
  const { outcome, detail } = gateWords(gate);
  const pullRequests: [string, number[]][] = [
    ["Person's tested PRs", gate.personTestedPullRequests],
    ["Person's PRs", gate.personPullRequests],
    ["Repository's tested PRs", gate.repositoryTestedPullRequests],
    ["Repository's PRs", gate.repositoryPullRequests],
    ["PRs without a digest", gate.pullRequestsWithoutDigest],
  ];
  return (
    <article className={styles.gate} data-outcome={gate.outcome}>
      <h3 className={styles.gateHead}>
        <span className={styles.gateName}>{gate.gate}</span>
        <span className={styles.gateOutcome}>{gate.outcome === "UNVERIFIED" ? "gate unverified" : outcome}</span>
      </h3>
      <GateChart gate={gate} />
      <p className={styles.gateDetail}>{detail}</p>
      {gate.outcome !== "UNVERIFIED" && (
        <>
          <p className={styles.gateDetail}>
            {gate.developersInGroup.length} developers in the comparison group, at least {gate.minimumDevelopers}{" "}
            needed.
          </p>
          {pullRequests.map(([label, numbers]) => (
            <Drill key={label} label={label} count={ofPullRequests(numbers)} summariesById={summariesById} />
          ))}
        </>
      )}
    </article>
  );
}
