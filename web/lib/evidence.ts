import { type BackendResult, readBackend } from "./backend";

export type EvidenceType =
  | "feature_work"
  | "bug_fix"
  | "review_comment_written"
  | "test_change"
  | "refactor_performance"
  | "security"
  | "infra_release"
  | "documentation";

export type TypeCounts = {
  featureWork: number;
  bugFix: number;
  reviewCommentWritten: number;
  testChange: number;
  refactorPerformance: number;
  security: number;
  infraRelease: number;
  documentation: number;
};

/** The current tier of an item and the reason the judgment gave for it. */
export type ItemTier = {
  tier: "T1" | "T2" | "T3" | "T4";
  reason: string;
};

export type Developer = {
  personId: number;
  displayName: string;
  counts: TypeCounts;
};

export type ItemLink = {
  label: string;
  url: string;
};

export type PullRequestFields = {
  number: number;
  title: string;
  url: string;
};

export type EvidenceItem = {
  id: number;
  type: EvidenceType;
  date: string;
  quarter: string;
  title: string;
  sourceUrl: string;
  links: ItemLink[];
  /** Null when the item has no tier: Documentation, and an item whose tier is not judged yet. */
  tier: ItemTier | null;
  /** The type-specific JSON of the item; a Review comment written has `pullRequest` and `comment`. */
  fields: {
    pullRequest?: PullRequestFields;
    comment?: { url: string; path: string; isReply: boolean };
  };
};

export type DeveloperEvidence = {
  person: { personId: number; displayName: string };
  items: EvidenceItem[];
};

/** The developers of a quarter with their counts of current evidence items in it. Never throws. */
export async function fetchDevelopers(
  baseUrl: string | undefined,
  quarter: string,
  fetchFn: typeof fetch = fetch,
): Promise<BackendResult<Developer[]>> {
  const query = new URLSearchParams({ quarter });
  return readBackend<Developer[]>(baseUrl, `/api/developers?${query}`, fetchFn);
}

/** The current evidence items of one developer in a quarter, newest first. Never throws. */
export async function fetchEvidenceItems(
  baseUrl: string | undefined,
  personId: number,
  quarter: string,
  fetchFn: typeof fetch = fetch,
): Promise<BackendResult<DeveloperEvidence>> {
  const query = new URLSearchParams({ quarter });
  return readBackend<DeveloperEvidence>(baseUrl, `/api/developers/${personId}/evidence-items?${query}`, fetchFn);
}
