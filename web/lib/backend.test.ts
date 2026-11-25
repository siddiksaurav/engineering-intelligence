import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { readBackend, writeBackend } from "./backend";

const BASE_URL = "http://localhost:8080";

function answering(status: number, body: string): typeof fetch {
  return vi.fn(async () => new Response(body, { status })) as unknown as typeof fetch;
}

describe("readBackend", () => {
  let warn: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    warn = vi.spyOn(console, "warn").mockImplementation(() => {});
  });

  afterEach(() => {
    warn.mockRestore();
  });

  it("returns the body of the path, uncached, without a doubled slash", async () => {
    const fetchFn = answering(200, '{"a":1}');

    const result = await readBackend(BASE_URL + "/", "/api/x", fetchFn);

    expect(result).toEqual({ status: "ok", data: { a: 1 } });
    expect(vi.mocked(fetchFn).mock.calls[0][0]).toBe("http://localhost:8080/api/x");
    expect(vi.mocked(fetchFn).mock.calls[0][1]?.cache).toBe("no-store");
  });

  it("says not found when the backend answers 404", async () => {
    expect(await readBackend(BASE_URL, "/api/x", answering(404, "{}"))).toEqual({ status: "not-found" });
  });

  it("says down when the backend answers an error, and logs the status", async () => {
    expect(await readBackend(BASE_URL, "/api/x", answering(500, "{}"))).toEqual({ status: "down" });
    expect(warn).toHaveBeenCalledWith("Backend request failed: HTTP 500 from http://localhost:8080/api/x");
  });

  it("says down when the connection fails", async () => {
    const failing = vi.fn(async () => {
      throw new TypeError("fetch failed");
    }) as unknown as typeof fetch;

    expect(await readBackend(BASE_URL, "/api/x", failing)).toEqual({ status: "down" });
    expect(warn).toHaveBeenCalledWith("Backend request failed: fetch failed");
  });

  it("says down when the answer is not JSON", async () => {
    expect(await readBackend(BASE_URL, "/api/x", answering(200, "<html>"))).toEqual({ status: "down" });
  });

  it("says down when the request takes longer than the timeout", async () => {
    const hanging = vi.fn(
      (_url: string, init: RequestInit) =>
        new Promise((_resolve, reject) => {
          init.signal?.addEventListener("abort", () => reject(new Error("aborted")));
        }),
    ) as unknown as typeof fetch;

    expect(await readBackend(BASE_URL, "/api/x", hanging, 10)).toEqual({ status: "down" });
    expect(warn).toHaveBeenCalledWith("Backend request failed: aborted");
  });

  it("says down without a call when no backend URL is set", async () => {
    const fetchFn = answering(200, "{}");

    expect(await readBackend(undefined, "/api/x", fetchFn)).toEqual({ status: "down" });
    expect(fetchFn).not.toHaveBeenCalled();
  });
});

describe("writeBackend", () => {
  let warn: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    warn = vi.spyOn(console, "warn").mockImplementation(() => {});
  });

  afterEach(() => {
    warn.mockRestore();
  });

  it("sends the request as given and returns the JSON answer", async () => {
    const fetchFn = answering(200, '{"a":1}');

    const result = await writeBackend(BASE_URL, "/api/x", { method: "PUT", body: "{}" }, fetchFn);

    expect(result).toEqual({ status: "ok", data: { a: 1 } });
    expect(vi.mocked(fetchFn).mock.calls[0][0]).toBe("http://localhost:8080/api/x");
    expect(vi.mocked(fetchFn).mock.calls[0][1]).toMatchObject({ method: "PUT", body: "{}" });
  });

  it("gives the detail of a problem as the refusal message", async () => {
    const result = await writeBackend(BASE_URL, "/api/x", {}, answering(409, '{"detail":"No label yet"}'));

    expect(result).toEqual({
      status: "refused",
      httpStatus: 409,
      message: "No label yet",
      body: { detail: "No label yet" },
    });
  });

  it("gives the message of an import refusal and keeps its body", async () => {
    const body = { message: "2 rows are wrong", problems: [{ line: 3 }] };

    const result = await writeBackend(BASE_URL, "/api/x", {}, answering(400, JSON.stringify(body)));

    expect(result).toEqual({ status: "refused", httpStatus: 400, message: "2 rows are wrong", body });
  });

  it("names the status when a refusal has no words", async () => {
    const result = await writeBackend(BASE_URL, "/api/x", {}, answering(502, "<html>"));

    expect(result).toMatchObject({ status: "refused", message: "The backend answered HTTP 502" });
  });

  it("says down when the connection fails or no backend URL is set", async () => {
    const failing = vi.fn(async () => {
      throw new TypeError("fetch failed");
    }) as unknown as typeof fetch;

    expect(await writeBackend(BASE_URL, "/api/x", {}, failing)).toEqual({ status: "down" });
    expect(await writeBackend(undefined, "/api/x", {}, answering(200, "{}"))).toEqual({ status: "down" });
  });

  it("says down when a success is not JSON", async () => {
    expect(await writeBackend(BASE_URL, "/api/x", {}, answering(200, "ok"))).toEqual({ status: "down" });
  });
});
