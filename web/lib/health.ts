export type BackendStatus = "up" | "down";

const HEALTH_PATH = "/actuator/health";
const DEFAULT_TIMEOUT_MS = 2000;

/**
 * Asks the backend's health endpoint whether it is up. "Up" means HTTP 200 with
 * `"status":"UP"`, which the backend gives only when it also reaches its database.
 * Every other outcome is "down" and its reason is logged. Never throws.
 */
export async function checkBackend(
  baseUrl: string | undefined,
  fetchFn: typeof fetch = fetch,
  timeoutMs: number = DEFAULT_TIMEOUT_MS,
): Promise<BackendStatus> {
  if (!baseUrl) {
    console.warn("Backend health check skipped: BACKEND_URL is not set");
    return "down";
  }
  const url = baseUrl.replace(/\/+$/, "") + HEALTH_PATH;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetchFn(url, { cache: "no-store", signal: controller.signal });
    const failure = await failureOf(response, url);
    if (failure) {
      console.warn(`Backend health check failed: ${failure}`);
      return "down";
    }
    return "up";
  } catch (error) {
    console.warn(`Backend health check failed: ${error instanceof Error ? error.message : String(error)}`);
    return "down";
  } finally {
    clearTimeout(timer);
  }
}

/** Why the answer does not mean "up", or undefined when it does. */
async function failureOf(response: Response, url: string): Promise<string | undefined> {
  if (response.status !== 200) {
    return `HTTP ${response.status} from ${url}`;
  }
  const body: unknown = await response.json();
  const status = typeof body === "object" && body !== null ? (body as { status?: unknown }).status : undefined;
  return status === "UP" ? undefined : `status is ${JSON.stringify(status)}`;
}
