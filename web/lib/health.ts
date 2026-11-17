export type BackendStatus = "up" | "down";

/**
 * Asks the backend's health endpoint whether it is up. "Up" means HTTP 200 with
 * `"status":"UP"`, which the backend gives only when it also reaches its database.
 * Every other outcome is "down". Never throws.
 */
export async function checkBackend(
  baseUrl: string | undefined,
  fetchFn: typeof fetch = fetch,
  timeoutMs: number = 2000,
): Promise<BackendStatus> {
  if (!baseUrl) {
    return "down";
  }
  const url = baseUrl.replace(/\/+$/, "") + "/actuator/health";
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetchFn(url, { cache: "no-store", signal: controller.signal });
    if (response.status !== 200) {
      return "down";
    }
    const body: unknown = await response.json();
    const status = typeof body === "object" && body !== null ? (body as { status?: unknown }).status : undefined;
    return status === "UP" ? "up" : "down";
  } catch {
    return "down";
  } finally {
    clearTimeout(timer);
  }
}
