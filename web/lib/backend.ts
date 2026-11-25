/** What a read of the backend gave. The pages show "backend down" and "not found" from this. */
export type BackendResult<T> = { status: "ok"; data: T } | { status: "down" } | { status: "not-found" };

/** What a write to the backend gave. A refusal carries the backend's own words, which the page shows. */
export type WriteResult<T> =
  | { status: "ok"; data: T }
  | { status: "refused"; httpStatus: number; message: string; body: unknown }
  | { status: "down" };

/** What the backend answered, or that it could not be reached. The body is read as text. */
export type BackendAnswer = { status: "answered"; httpStatus: number; body: string; url: string } | { status: "down" };

export type BackendRequest = { method?: string; headers?: Record<string, string>; body?: string };

const DEFAULT_TIMEOUT_MS = 5000;

/** Sends one request to a path of the backend. Never throws: a missing URL, a failed connection and a timeout are "down" and logged. */
export async function sendBackend(
  baseUrl: string | undefined,
  path: string,
  request: BackendRequest,
  fetchFn: typeof fetch,
  timeoutMs: number = DEFAULT_TIMEOUT_MS,
): Promise<BackendAnswer> {
  if (!baseUrl) {
    console.warn("Backend request skipped: BACKEND_URL is not set");
    return { status: "down" };
  }
  const url = baseUrl.replace(/\/+$/, "") + path;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetchFn(url, { ...request, cache: "no-store", signal: controller.signal });
    return { status: "answered", httpStatus: response.status, body: await response.text(), url };
  } catch (error) {
    console.warn(`Backend request failed: ${error instanceof Error ? error.message : String(error)}`);
    return { status: "down" };
  } finally {
    clearTimeout(timer);
  }
}

/** Reads one path of the backend as JSON. A 404 is "not-found"; every other failure is "down" and logged. Never throws. */
export async function readBackend<T>(
  baseUrl: string | undefined,
  path: string,
  fetchFn: typeof fetch,
  timeoutMs: number = DEFAULT_TIMEOUT_MS,
): Promise<BackendResult<T>> {
  const answer = await sendBackend(baseUrl, path, {}, fetchFn, timeoutMs);
  if (answer.status === "down") {
    return answer;
  }
  if (answer.httpStatus === 404) {
    return { status: "not-found" };
  }
  if (answer.httpStatus < 200 || answer.httpStatus > 299) {
    console.warn(`Backend request failed: HTTP ${answer.httpStatus} from ${answer.url}`);
    return { status: "down" };
  }
  const data = parsedJson(answer.body);
  if (data === undefined) {
    console.warn(`Backend request failed: the answer of ${answer.url} is not JSON`);
    return { status: "down" };
  }
  return { status: "ok", data: data as T };
}

/** Sends a write and gives the JSON answer. A refusal (any non-2xx answer) carries the `detail` or `message` of the problem. Never throws. */
export async function writeBackend<T>(
  baseUrl: string | undefined,
  path: string,
  request: BackendRequest,
  fetchFn: typeof fetch = fetch,
  timeoutMs: number = DEFAULT_TIMEOUT_MS,
): Promise<WriteResult<T>> {
  const answer = await sendBackend(baseUrl, path, request, fetchFn, timeoutMs);
  if (answer.status === "down") {
    return answer;
  }
  const body = parsedJson(answer.body);
  if (answer.httpStatus < 200 || answer.httpStatus > 299) {
    return {
      status: "refused",
      httpStatus: answer.httpStatus,
      message: problemMessage(body) ?? `The backend answered HTTP ${answer.httpStatus}`,
      body,
    };
  }
  if (body === undefined) {
    console.warn(`Backend request failed: the answer of ${answer.url} is not JSON`);
    return { status: "down" };
  }
  return { status: "ok", data: body as T };
}

/** The parsed JSON, or undefined when the text is not JSON. */
function parsedJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

function problemMessage(body: unknown): string | undefined {
  if (typeof body !== "object" || body === null) {
    return undefined;
  }
  const { detail, message } = body as { detail?: unknown; message?: unknown };
  const text = detail ?? message;
  return typeof text === "string" ? text : undefined;
}
