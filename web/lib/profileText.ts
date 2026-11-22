import type { DimensionScore, GateResult, Level, Tier, UnverifiedReason } from "./profile";
import { TIER_TABLE } from "./tierTable";

const TIERS_ASCENDING: Tier[] = ["T1", "T2", "T3"];

const LEVEL_TIER: Record<Level, Tier> = { L1: "T1", L2: "T2", L3: "T3" };

const FRACTION_WORDS: Record<number, string> = { 2: "a half", 3: "a third", 4: "a quarter" };

const NUMBER_WORDS: Record<number, string> = { 1: "one", 2: "two", 3: "three", 4: "four" };

const OUTCOME_WORDS = {
  PASS: "pass",
  CLOSE_MISS: "close miss",
  FAR_MISS: "far miss",
  UNVERIFIED: "unverified",
} as const;

const NO_PULL_REQUESTS = "no PRs";

/** A share as a whole percent; "no PRs" when the share is null (decision 234). */
export function percent(share: number | null): string {
  if (share === null) {
    return NO_PULL_REQUESTS;
  }
  // The precision step removes floating point noise, so 0.145 rounds to 15 and not to 14.
  return `${Math.round(Number((share * 100).toPrecision(12)))}%`;
}

/** The mark of a dimension card: a strength at 2, a gap at 0 (decision 251). */
export function badgeOf(dimension: DimensionScore): "strength" | "gap" | null {
  if (dimension.score === 2) {
    return "strength";
  }
  return dimension.score === 0 ? "gap" : null;
}

/** Why the dimension has this score at the level: the working tier, the level's tier and the gates (decision 236). */
export function whyLine(dimension: DimensionScore, level: Level): string {
  const worksAt = dimension.workingTier ? `Works at ${dimension.workingTier}.` : "No working tier.";
  const { scoreBeforeGates } = dimension.trail;
  const held = gatesThatHeld(dimension)
    .map((gate) => `${gate.gate} (${OUTCOME_WORDS[gate.outcome]})`)
    .join(", ");
  const heldBy = held ? `, held to ${dimension.score} by ${held}` : "";
  return `${worksAt} ${level} expects ${LEVEL_TIER[level]}: ${scoreBeforeGates}${heldBy}.`;
}

/**
 * What the next score needs, written from the tier table and the trail's settings (spec 11.3, decision 248).
 * Null at score 2, which has no next step.
 */
export function nextStepLine(dimension: DimensionScore, level: Level): string | null {
  if (dimension.score === 2) {
    return null;
  }
  const held = gatesThatHeld(dimension);
  if (held.length > 0) {
    return `To score ${dimension.trail.scoreBeforeGates}: ${held.map(gateRequirement(dimension)).join(" and ")}.`;
  }
  const nextScore = dimension.score + 1;
  const neededTier = TIERS_ASCENDING[TIERS_ASCENDING.indexOf(LEVEL_TIER[level]) + nextScore - 1];
  if (!neededTier) {
    return `T3 is the top tier in the PoC, so ${dimension.score} is the best score at ${level}.`;
  }
  const requirements = dimension.trail.evidenceTypes
    .filter((trail) => trail.presence.length > 0)
    .map((trail) => typeRequirement(dimension, trail.evidenceType, trail.rule, neededTier))
    .filter((requirement) => requirement !== null);
  const needed = requirements.length > 0 ? requirements.join(" or ") : `work at ${neededTier}`;
  return `To score ${nextScore}: ${needed}${levelThreeAddition(dimension, neededTier)}.`;
}

/** A gate result in words: the outcome, and the numbers or the reason behind it. */
export function gateWords(gate: GateResult): { outcome: string; detail: string } {
  const outcome = OUTCOME_WORDS[gate.outcome];
  if (gate.outcome === "UNVERIFIED") {
    return { outcome, detail: unverifiedWords(gate) };
  }
  const difference = gate.differencePoints;
  const sharesWords = `The person is at ${sharePoints(gate.personSharePercent)}, the repository at ${sharePoints(gate.repositorySharePercent)}`;
  const differenceWords =
    difference === null ? "" : `: ${points(Math.abs(difference))} points ${difference < 0 ? "above" : "below"} it`;
  const limitWords = gate.limits ? ` ${limitsWords(gate)}` : "";
  return { outcome, detail: `${sharesWords}${differenceWords}.${limitWords}` };
}

function limitsWords(gate: GateResult): string {
  const limits = gate.limits;
  if (!limits) {
    return "";
  }
  const pass = `A pass is within ${limits.passWithinPoints} points below`;
  return gate.outcome === "PASS" ? `${pass}.` : `${pass}, a far miss is beyond ${limits.farMissBeyondPoints}.`;
}

function unverifiedWords(gate: GateResult): string {
  const words: Record<UnverifiedReason, string> = {
    TOO_FEW_DEVELOPERS: `Fewer than ${gate.minimumDevelopers} developers have pull requests of this kind, too few to compare.`,
    NO_PULL_REQUEST_OF_THE_KIND: "The person has no pull request of this kind.",
    NO_REPOSITORY_PULL_REQUESTS: "The repository has no pull request of this kind.",
    NOT_CALCULATED: "This gate is not calculated in the PoC.",
  };
  return gate.unverifiedReason ? words[gate.unverifiedReason] : "The gate has no result.";
}

/** The gates that lowered the score: the far misses when there are any, else the close misses. */
function gatesThatHeld(dimension: DimensionScore): GateResult[] {
  if (dimension.score >= dimension.trail.scoreBeforeGates) {
    return [];
  }
  const farMisses = dimension.gates.filter((gate) => gate.outcome === "FAR_MISS");
  return farMisses.length > 0 ? farMisses : dimension.gates.filter((gate) => gate.outcome === "CLOSE_MISS");
}

function gateRequirement(dimension: DimensionScore): (gate: GateResult) => string {
  // A close miss lowers only a 2, a far miss lowers any score: the target decides the limit.
  const targetScore = dimension.trail.scoreBeforeGates;
  return (gate) => {
    if (!gate.limits) {
      return `${gate.gate} passing`;
    }
    const limit = targetScore >= 2 ? gate.limits.passWithinPoints : gate.limits.farMissBeyondPoints;
    return `${gate.gate} within ${limit} points of the repository`;
  };
}

function typeRequirement(
  dimension: DimensionScore,
  evidenceType: string,
  rule: "SHARE" | "ONE_ITEM",
  tier: Tier,
): string | null {
  const row = TIER_TABLE[evidenceType];
  const wording = row?.tiers[tier];
  if (!row || !wording) {
    return null;
  }
  if (rule === "ONE_ITEM") {
    return `one ${row.name} at ${tier}: ${wording}`;
  }
  return `${fractionWords(dimension)} of ${row.plural} at ${wording} or higher`;
}

function fractionWords(dimension: DimensionScore): string {
  const { numerator, denominator } = dimension.trail.settings.presenceThreshold;
  return numerator === 1 && FRACTION_WORDS[denominator] ? FRACTION_WORDS[denominator] : `${numerator}/${denominator}`;
}

function levelThreeAddition(dimension: DimensionScore, neededTier: Tier): string {
  const rule = dimension.trail.levelThreeRule;
  if (!rule || neededTier !== "T3") {
    return "";
  }
  return `, and ${NUMBER_WORDS[rule.t3ItemsNeeded] ?? rule.t3ItemsNeeded} T3 items`;
}

/** Points with at most one decimal and no trailing zero. */
function points(value: number): string {
  return String(Math.round(value * 10) / 10);
}

function sharePoints(percentValue: number | null): string {
  return percentValue === null ? NO_PULL_REQUESTS : `${points(percentValue)}%`;
}
