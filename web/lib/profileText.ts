import { typeTitle } from "./evidenceSections";
import type { DimensionScore, GateResult, Level, Tier, UnverifiedReason, WorkLevel } from "./profile";
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

/** The tier a level expects: L1 expects T1, L2 expects T2, L3 expects T3. */
export function expectedTier(level: Level): Tier {
  return LEVEL_TIER[level];
}

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
 * What the next score needs, written from the tier table and the trail's settings (spec 11.3, decisions 248, 258):
 * the tier when the working tier is short of the target, and every gate that would hold the target score.
 * Null at score 2, which has no next step.
 */
export function nextStepLine(dimension: DimensionScore, level: Level): string | null {
  if (dimension.score === 2) {
    return null;
  }
  const { scoreBeforeGates } = dimension.trail;
  // Gates that lowered the score name the score they denied; otherwise the next score is one above.
  const targetScore = dimension.score < scoreBeforeGates ? scoreBeforeGates : dimension.score + 1;
  const parts: string[] = [];
  const gateParts = dimension.gates
    .filter((gate) => isMiss(gate) && scoreCap(gate) < targetScore)
    .map(gateRequirement(targetScore));
  if (scoreBeforeGates < targetScore) {
    const neededTier = TIERS_ASCENDING[TIERS_ASCENDING.indexOf(LEVEL_TIER[level]) + targetScore - 1];
    if (!neededTier) {
      return `T3 is the top tier in the PoC, so ${dimension.score} is the best score at ${level}.`;
    }
    parts.push(tierRequirement(dimension, neededTier));
  }
  if (gateParts.length > 0) {
    parts.push(gateParts.join(" and "));
  }
  return `To score ${targetScore}: ${parts.join("; and ")}.`;
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

function isMiss(gate: GateResult): boolean {
  return gate.outcome === "FAR_MISS" || gate.outcome === "CLOSE_MISS";
}

/** The highest score a missed gate allows: a far miss gives 0, a close miss caps at 1 (decision 211). */
function scoreCap(gate: GateResult): number {
  return gate.outcome === "FAR_MISS" ? 0 : 1;
}

/** The gates that lowered the score: those whose cap is the score it was held to. */
function gatesThatHeld(dimension: DimensionScore): GateResult[] {
  if (dimension.score >= dimension.trail.scoreBeforeGates) {
    return [];
  }
  return dimension.gates.filter((gate) => isMiss(gate) && scoreCap(gate) === dimension.score);
}

function gateRequirement(targetScore: number): (gate: GateResult) => string {
  // A close miss holds only a 2, a far miss holds any score: the target decides the limit.
  return (gate) => {
    if (!gate.limits) {
      return `${gate.gate} passing`;
    }
    const limit = targetScore >= 2 ? gate.limits.passWithinPoints : gate.limits.farMissBeyondPoints;
    return `${gate.gate} within ${limit} points of the repository`;
  };
}

function tierRequirement(dimension: DimensionScore, neededTier: Tier): string {
  const requirements = dimension.trail.evidenceTypes
    .filter((trail) => trail.presence.length > 0)
    .map((trail) => typeRequirement(dimension, trail.evidenceType, trail.rule, neededTier))
    .filter((requirement) => requirement !== null);
  const needed = requirements.length > 0 ? requirements.join(" or ") : `work at ${neededTier}`;
  return `${needed}${levelThreeAddition(dimension, neededTier)}`;
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

/** A gate share in percent with at most one decimal (decision 260); "no PRs" when there is none. */
export function sharePoints(percentValue: number | null): string {
  return percentValue === null ? NO_PULL_REQUESTS : `${points(percentValue)}%`;
}

/** A snake case name in plain words: "very_high" becomes "Very high"; a work type uses its screen title. */
export function plainName(name: string): string {
  const words = name.replaceAll("_", " ");
  return typeTitle(name) ?? words.charAt(0).toUpperCase() + words.slice(1);
}

/** The line a share must reach, in words: "one-third" for a third, else a whole percent. */
function lineWords(line: number): string {
  return Math.abs(line - 1 / 3) < 1e-9 ? "one-third" : percent(line);
}

/**
 * One sentence that reads the work-level chart: the share at Medium or harder and at High against the line, and what
 * that means for the working tier (decision 285). Says so when no Feature work is judged yet.
 */
export function workLevelSentence(name: string, workLevel: WorkLevel): string {
  const { person, line } = workLevel;
  if (person.shareAtT2OrAbove === null || person.shareAtT3 === null) {
    return `${name} has no judged Feature work in this quarter yet, so there is no level to read.`;
  }
  const words = lineWords(line);
  const atT2 = person.shareAtT2OrAbove >= line;
  const atT3 = person.shareAtT3 >= line;
  return (
    `${percent(person.shareAtT2OrAbove)} of ${name}'s Feature work was Medium or harder, ` +
    `${atT2 ? "at or above" : "below"} the ${words} line, so ${atT2 ? "it counts at T2" : "not yet T2"}. ` +
    `High work is ${percent(person.shareAtT3)}, ${atT3 ? "at or above" : "below"} the line, ` +
    `so ${atT3 ? "it counts at T3" : "not yet T3"}.`
  );
}
