import type { GateResult } from "@/lib/profile";
import { gateWords } from "@/lib/profileText";
import { Drill, ofPullRequests } from "./Drill";
import type { SummariesById } from "./ItemReferences";
import styles from "./profile.module.css";

/** The quality gates of the Engineering standards dimension, once on the page (decision 240). */
export function GatesPanel({ gates, summariesById }: { gates: GateResult[]; summariesById: SummariesById }) {
  return (
    <section className={styles.card}>
      <h2 className={styles.panelTitle}>Quality gates</h2>
      {gates.map((gate) => (
        <Gate key={gate.gate} gate={gate} summariesById={summariesById} />
      ))}
    </section>
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
    <div className={styles.gate}>
      <h3>
        {gate.gate}: {gate.outcome === "UNVERIFIED" ? "gate unverified" : outcome}
      </h3>
      <p>{detail}</p>
      {gate.outcome !== "UNVERIFIED" && (
        <>
          <p>
            {gate.developersInGroup.length} developers in the comparison group, at least {gate.minimumDevelopers}{" "}
            needed.
          </p>
          {pullRequests.map(([label, numbers]) => (
            <Drill key={label} label={label} count={ofPullRequests(numbers)} summariesById={summariesById} />
          ))}
        </>
      )}
    </div>
  );
}
