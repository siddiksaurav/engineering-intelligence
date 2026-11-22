import type { GateResult } from "@/lib/profile";
import { sharePoints } from "@/lib/profileText";
import styles from "./profile.module.css";

/** A mark closer than this to an end of the track turns its label inward, so the label stays inside the chart. */
const EDGE_PERCENT = 18;

type Zone = { name: "pass" | "close"; from: number; to: number };

/**
 * A gate as a bullet chart on a track from 0 to 100 percent: a line at the repository's share, a dot at the
 * person's, and the zones a person's share passes or closely misses in, shaded from the limits of the gate.
 * Nothing is drawn for an unverified gate or when a share is missing: zones and a dot would read as an outcome the
 * gate does not have. The gate's words say why.
 */
export function GateChart({ gate }: { gate: GateResult }) {
  const { personSharePercent: person, repositorySharePercent: repository } = gate;
  if (gate.outcome === "UNVERIFIED" || person === null || repository === null) {
    return null;
  }
  return (
    <div
      className={styles.gateChart}
      role="img"
      aria-label={`${gate.gate}: person ${sharePoints(person)}, repository ${sharePoints(repository)}`}
    >
      <div className={styles.gateTrack}>
        {zonesOf(gate, repository).map((zone) => (
          <span
            key={zone.name}
            className={styles.gateZone}
            data-zone={zone.name}
            style={{ left: `${zone.from}%`, width: `${zone.to - zone.from}%` }}
          />
        ))}
      </div>
      <Mark name="repository" percent={repository} label={`Repository ${sharePoints(repository)}`} />
      <Mark name="person" percent={person} label={`Person ${sharePoints(person)}`} />
    </div>
  );
}

/** A pass is within `passWithinPoints` below the repository; a close miss reaches down to `farMissBeyondPoints`. */
function zonesOf(gate: GateResult, repository: number): Zone[] {
  if (!gate.limits) {
    return [];
  }
  const passFrom = Math.max(0, repository - gate.limits.passWithinPoints);
  const closeFrom = Math.max(0, repository - gate.limits.farMissBeyondPoints);
  return [
    { name: "pass", from: passFrom, to: 100 },
    { name: "close", from: closeFrom, to: passFrom },
  ];
}

function Mark({ name, percent, label }: { name: "person" | "repository"; percent: number; label: string }) {
  const edge = percent < EDGE_PERCENT ? "start" : percent > 100 - EDGE_PERCENT ? "end" : undefined;
  return (
    <span className={styles.gateMark} data-mark={name} data-edge={edge} style={{ left: `${percent}%` }}>
      <span className={styles.gateMarkLabel}>{label}</span>
    </span>
  );
}
