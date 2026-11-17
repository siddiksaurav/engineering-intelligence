import { describe, expect, it, vi } from "vitest";
import { checkBackend } from "./health";

const BASE = "http://localhost:8080";

function answering(status: number, body: string): typeof fetch {
  return vi.fn(async () => new Response(body, { status })) as unknown as typeof fetch;
}

describe("checkBackend", () => {
  it("is up when the health endpoint answers 200 with status UP", async () => {
    expect(await checkBackend(BASE, answering(200, '{"status":"UP"}'))).toBe("up");
  });

  it("calls the Actuator health endpoint of the backend", async () => {
    const fetchFn = answering(200, '{"status":"UP"}');
    await checkBackend(BASE, fetchFn);
    expect(fetchFn).toHaveBeenCalledTimes(1);
    expect(vi.mocked(fetchFn).mock.calls[0][0]).toBe("http://localhost:8080/actuator/health");
  });

  it("does not double the slash when the base URL ends with one", async () => {
    const fetchFn = answering(200, '{"status":"UP"}');
    await checkBackend(BASE + "/", fetchFn);
    expect(vi.mocked(fetchFn).mock.calls[0][0]).toBe("http://localhost:8080/actuator/health");
  });

  it("does not let the answer be cached", async () => {
    const fetchFn = answering(200, '{"status":"UP"}');
    await checkBackend(BASE, fetchFn);
    expect(vi.mocked(fetchFn).mock.calls[0][1]?.cache).toBe("no-store");
  });

  it("is down when the status is not UP", async () => {
    expect(await checkBackend(BASE, answering(200, '{"status":"DOWN"}'))).toBe("down");
  });

  it("is down when the HTTP status is not 200, whatever the body says", async () => {
    expect(await checkBackend(BASE, answering(503, '{"status":"DOWN"}'))).toBe("down");
    expect(await checkBackend(BASE, answering(503, '{"status":"UP"}'))).toBe("down");
  });

  it("is down when the body is not JSON", async () => {
    expect(await checkBackend(BASE, answering(200, "<html>"))).toBe("down");
  });

  it("is down when the body is JSON without a status", async () => {
    expect(await checkBackend(BASE, answering(200, "null"))).toBe("down");
    expect(await checkBackend(BASE, answering(200, "{}"))).toBe("down");
  });

  it("is down when the connection fails", async () => {
    const fetchFn = vi.fn(async () => {
      throw new TypeError("fetch failed");
    }) as unknown as typeof fetch;
    expect(await checkBackend(BASE, fetchFn)).toBe("down");
  });

  it("is down after the timeout when the backend does not answer, and aborts the request", async () => {
    let signal: AbortSignal | undefined;
    const fetchFn = vi.fn((_url: unknown, init?: RequestInit) => {
      signal = init?.signal ?? undefined;
      return new Promise<Response>((_resolve, reject) => {
        signal?.addEventListener("abort", () => reject(new DOMException("aborted", "AbortError")));
      });
    }) as unknown as typeof fetch;

    expect(await checkBackend(BASE, fetchFn, 50)).toBe("down");
    expect(signal?.aborted).toBe(true);
  });

  it("is down without calling anything when no backend URL is set", async () => {
    const fetchFn = answering(200, '{"status":"UP"}');
    expect(await checkBackend(undefined, fetchFn)).toBe("down");
    expect(await checkBackend("", fetchFn)).toBe("down");
    expect(fetchFn).not.toHaveBeenCalled();
  });
});
