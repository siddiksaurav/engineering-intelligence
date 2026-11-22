import { type BackendResult, readBackend } from "./backend";
import type { EvidenceType, ItemLink } from "./evidence";
import type { PersonSummary } from "./profile";

/** An evidence item as the profile's lists show it, with its current tier. */
export type ItemSummary = {
  id: number;
  type: EvidenceType;
  date: string;
  quarter: string;
  title: string;
  sourceUrl: string;
  links: ItemLink[];
  person: PersonSummary;
  tier: "T1" | "T2" | "T3" | "T4" | null;
  /** False when a later evidence-build superseded or withdrew this row (decision 238). */
  current: boolean;
  currentItemId: number | null;
};

export type JudgmentState = "judged" | "not_judged_yet" | "not_tiered";

export type JudgmentFact = { fact: string; link: string };

/** The current judgment of an item's kind; `value` depends on `kind` (design, "What F12 reads"). */
export type ItemJudgment = {
  state: JudgmentState;
  kind: string | null;
  tier: string | null;
  value: Record<string, unknown> | null;
  reason: string | null;
  facts: JudgmentFact[] | null;
  model: string | null;
  promptVersion: string | null;
  judgedAt: string | null;
};

export type ItemDetail = ItemSummary & { judgment: ItemJudgment };

/** The summaries of the items with these ids, in the order asked; unknown ids are left out. No call for none. */
export async function fetchItemSummaries(
  baseUrl: string | undefined,
  ids: number[],
  fetchFn: typeof fetch = fetch,
): Promise<BackendResult<ItemSummary[]>> {
  if (ids.length === 0) {
    return { status: "ok", data: [] };
  }
  return readBackend<ItemSummary[]>(baseUrl, `/api/evidence-items?ids=${ids.join(",")}`, fetchFn);
}

/** One evidence item with its current judgment. Never throws. */
export async function fetchItem(
  baseUrl: string | undefined,
  id: number,
  fetchFn: typeof fetch = fetch,
): Promise<BackendResult<ItemDetail>> {
  return readBackend<ItemDetail>(baseUrl, `/api/evidence-items/${id}`, fetchFn);
}
