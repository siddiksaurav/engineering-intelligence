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

/** What a read of the backend gave. The pages show "backend down" and "not found" from this. */
export type EvidenceResult<T> = { status: "ok"; data: T } | { status: "down" } | { status: "not-found" };

const DEFAULT_TIMEOUT_MS = 5000;

/** The developers with their counts of current evidence items. Never throws. */
export async function fetchDevelopers(
  baseUrl: string | undefined,
  fetchFn: typeof fetch = fetch,
): Promise<EvidenceResult<Developer[]>> {
  return read<Developer[]>(baseUrl, "/api/developers", fetchFn);
}

/** The current evidence items of one developer, newest first. Never throws. */
export async function fetchEvidenceItems(
  baseUrl: string | undefined,
  personId: number,
  fetchFn: typeof fetch = fetch,
): Promise<EvidenceResult<DeveloperEvidence>> {
  return read<DeveloperEvidence>(baseUrl, `/api/developers/${personId}/evidence-items`, fetchFn);
}

async function read<T>(baseUrl: string | undefined, path: string, fetchFn: typeof fetch): Promise<EvidenceResult<T>> {
  if (!baseUrl) {
    console.warn("Evidence request skipped: BACKEND_URL is not set");
    return { status: "down" };
  }
  const url = baseUrl.replace(/\/+$/, "") + path;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), DEFAULT_TIMEOUT_MS);
  try {
    const response = await fetchFn(url, { cache: "no-store", signal: controller.signal });
    if (response.status === 404) {
      return { status: "not-found" };
    }
    if (!response.ok) {
      console.warn(`Evidence request failed: HTTP ${response.status} from ${url}`);
      return { status: "down" };
    }
    return { status: "ok", data: (await response.json()) as T };
  } catch (error) {
    console.warn(`Evidence request failed: ${error instanceof Error ? error.message : String(error)}`);
    return { status: "down" };
  } finally {
    clearTimeout(timer);
  }
}
