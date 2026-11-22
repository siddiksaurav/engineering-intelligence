/** What a read of the backend gave. The pages show "backend down" and "not found" from this. */
export type BackendResult<T> = { status: "ok"; data: T } | { status: "down" } | { status: "not-found" };

const DEFAULT_TIMEOUT_MS = 5000;

/** Reads one path of the backend as JSON. A 404 is "not-found"; every other failure is "down" and logged. Never throws. */
export async function readBackend<T>(
  baseUrl: string | undefined,
  path: string,
  fetchFn: typeof fetch,
  timeoutMs: number = DEFAULT_TIMEOUT_MS,
): Promise<BackendResult<T>> {
  if (!baseUrl) {
    console.warn("Backend request skipped: BACKEND_URL is not set");
    return { status: "down" };
  }
  const url = baseUrl.replace(/\/+$/, "") + path;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetchFn(url, { cache: "no-store", signal: controller.signal });
    if (response.status === 404) {
      return { status: "not-found" };
    }
    if (!response.ok) {
      console.warn(`Backend request failed: HTTP ${response.status} from ${url}`);
      return { status: "down" };
    }
    return { status: "ok", data: (await response.json()) as T };
  } catch (error) {
    console.warn(`Backend request failed: ${error instanceof Error ? error.message : String(error)}`);
    return { status: "down" };
  } finally {
    clearTimeout(timer);
  }
}
