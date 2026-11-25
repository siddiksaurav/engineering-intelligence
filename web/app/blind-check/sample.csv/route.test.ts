import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { GET } from "./route";

describe("GET /blind-check/sample.csv", () => {
  let fetchFn: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.stubEnv("BACKEND_URL", "http://backend.test:9000");
    fetchFn = vi.fn();
    vi.stubGlobal("fetch", fetchFn);
    vi.spyOn(console, "warn").mockImplementation(() => {});
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("picks the sample first and passes the CSV through as a download", async () => {
    fetchFn
      .mockResolvedValueOnce(new Response("[]", { status: 200 }))
      .mockResolvedValueOnce(new Response("item_id,complexity\n1,\n", { status: 200 }));

    const response = await GET();

    expect(fetchFn.mock.calls[0][0]).toBe("http://backend.test:9000/api/blind-check/sample");
    expect(fetchFn.mock.calls[0][1].method).toBe("POST");
    expect(fetchFn.mock.calls[1][0]).toBe("http://backend.test:9000/api/blind-check/sample.csv");
    expect(response.status).toBe(200);
    expect(response.headers.get("Content-Type")).toBe("text/csv; charset=utf-8");
    expect(response.headers.get("Content-Disposition")).toBe('attachment; filename="blind-check.csv"');
    expect(await response.text()).toBe("item_id,complexity\n1,\n");
  });

  it("answers the refusal of the pick as plain text with its status and no file", async () => {
    fetchFn.mockResolvedValueOnce(new Response('{"detail":"Only 4 items are low; 10 are needed"}', { status: 409 }));

    const response = await GET();

    expect(response.status).toBe(409);
    expect(response.headers.get("Content-Type")).toBe("text/plain; charset=utf-8");
    expect(await response.text()).toBe("Only 4 items are low; 10 are needed");
    expect(fetchFn).toHaveBeenCalledTimes(1);
  });

  it("answers 503 as plain text when the backend is down", async () => {
    fetchFn.mockRejectedValue(new TypeError("fetch failed"));

    const response = await GET();

    expect(response.status).toBe(503);
    expect(await response.text()).toBe("backend down");
  });

  it("answers 502 when the backend refuses the export", async () => {
    fetchFn
      .mockResolvedValueOnce(new Response("[]", { status: 200 }))
      .mockResolvedValueOnce(new Response("boom", { status: 500 }));

    const response = await GET();

    expect(response.status).toBe(502);
    expect(await response.text()).toContain("500");
  });
});
