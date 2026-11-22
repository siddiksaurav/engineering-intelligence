import type { DimensionTrail, EvidenceTypeTrail, TrailItem } from "@/lib/profile";
import { TIER_TABLE } from "@/lib/tierTable";
import { ItemLink, PullRequestLinks, type SummariesById } from "./ItemReferences";
import styles from "./profile.module.css";

/** How a dimension got its score at the level: every item it read, the presence per tier and the rules (decision 252). */
export function Trail({ trail, summariesById }: { trail: DimensionTrail; summariesById: SummariesById }) {
  const { numerator, denominator } = trail.settings.presenceThreshold;
  return (
    <details className={styles.trail}>
      <summary>Trail</summary>
      {trail.evidenceTypes.map((typeTrail) => (
        <TypeTrail
          key={typeTrail.evidenceType}
          typeTrail={typeTrail}
          threshold={`${numerator}/${denominator}`}
          summariesById={summariesById}
        />
      ))}
      {trail.levelThreeRule && (
        <p>
          {trail.levelThreeRule.t3Items} of {trail.levelThreeRule.t3ItemsNeeded} T3 items needed at L3:{" "}
          {trail.levelThreeRule.met ? "met" : "not met"}
        </p>
      )}
      <TrailItems label="Counted, not tiered" items={trail.countedNotTiered} summariesById={summariesById} />
      <p>
        Score before gates {trail.scoreBeforeGates}, final score {trail.finalScore}.
      </p>
      {trail.notes.length > 0 && (
        <ul>
          {trail.notes.map((note) => (
            <li key={note}>{note}</li>
          ))}
        </ul>
      )}
    </details>
  );
}

function TypeTrail({
  typeTrail,
  threshold,
  summariesById,
}: {
  typeTrail: EvidenceTypeTrail;
  threshold: string;
  summariesById: SummariesById;
}) {
  const needs = typeTrail.rule === "SHARE" ? `needs ${threshold}` : "needs one item";
  return (
    <section className={styles.typeTrail}>
      <h3>{TIER_TABLE[typeTrail.evidenceType]?.name ?? typeTrail.evidenceType}</h3>
      {typeTrail.presence.length === 0 ? (
        <p className={styles.drillEmpty}>Not read at this level.</p>
      ) : (
        <ul>
          {typeTrail.presence.map((row) => (
            <li key={row.tier}>
              {row.tier}: {row.atOrAbove} of {row.total} at or above, {needs}: {row.presence ? "yes" : "no"}
            </li>
          ))}
        </ul>
      )}
      <TrailItems label="Counted" items={typeTrail.counted} summariesById={summariesById} />
      <TrailItems label="Not judged yet" items={typeTrail.notJudgedYet} summariesById={summariesById} />
      <TrailItems label="Not substantive" items={typeTrail.notSubstantive} summariesById={summariesById} />
      <TrailItems label="Not read at this level" items={typeTrail.notReadAtLevel} summariesById={summariesById} />
    </section>
  );
}

function TrailItems({
  label,
  items,
  summariesById,
}: {
  label: string;
  items: TrailItem[];
  summariesById: SummariesById;
}) {
  if (items.length === 0) {
    return <p className={styles.drillEmpty}>{label}: 0</p>;
  }
  return (
    <details className={styles.drill}>
      <summary>
        {label}: {items.length}
      </summary>
      <ul className={styles.drillList}>
        {items.map((item) => (
          <li key={item.evidenceItemId}>
            <ItemLink itemId={item.evidenceItemId} summariesById={summariesById} />
            <span className={styles.itemKind}> {itemFacts(item)}</span>
            <PullRequestLinks numbers={item.pullRequestNumbers} />
          </li>
        ))}
      </ul>
    </details>
  );
}

function itemFacts(item: TrailItem): string {
  const tier = item.tier ?? "no tier";
  return item.weight === null ? tier : `${tier}, weight ${item.weight}`;
}
