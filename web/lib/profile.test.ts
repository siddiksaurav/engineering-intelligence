import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchAnalytics, fetchDimensionScores } from "./profile";

const BASE_URL = "http://localhost:8080";

const SCORES = {
  person: { personId: 7, displayName: "ada" },
  quarter: "2026-Q3",
  level: "L2",
  scored: false,
  dimensions: [],
};

function answering(status: number, body: string): typeof fetch {
  return vi.fn(async () => new Response(body, { status })) as unknown as typeof fetch;
}

describe("profile client", () => {
  let warn: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    warn = vi.spyOn(console, "warn").mockImplementation(() => {});
  });

  afterEach(() => {
    warn.mockRestore();
  });

  describe("fetchDimensionScores", () => {
    it("calls the scores endpoint with quarter and level and returns the body", async () => {
      const fetchFn = answering(200, JSON.stringify(SCORES));

      const result = await fetchDimensionScores(BASE_URL, 7, "2026-Q3", "L2", fetchFn);

      expect(result).toEqual({ status: "ok", data: SCORES });
      expect(vi.mocked(fetchFn).mock.calls[0][0]).toBe(
        "http://localhost:8080/api/developers/7/dimension-scores?quarter=2026-Q3&level=L2",
      );
    });

    it("says not found when the backend answers 404", async () => {
      expect(await fetchDimensionScores(BASE_URL, 9, "2026-Q3", "L2", answering(404, "{}"))).toEqual({
        status: "not-found",
      });
    });

    it("says the backend is down when it answers an error", async () => {
      expect(await fetchDimensionScores(BASE_URL, 7, "2026-Q3", "L2", answering(500, "{}"))).toEqual({
        status: "down",
      });
    });

    it("says the backend is down when the request times out", async () => {
      const hanging = vi.fn(
        (_url: string, init: RequestInit) =>
          new Promise((_resolve, reject) => {
            init.signal?.addEventListener("abort", () => reject(new Error("aborted")));
          }),
      ) as unknown as typeof fetch;
      vi.useFakeTimers();

      const pending = fetchDimensionScores(BASE_URL, 7, "2026-Q3", "L2", hanging);
      await vi.advanceTimersByTimeAsync(5000);

      expect(await pending).toEqual({ status: "down" });
      vi.useRealTimers();
    });

    it("says the backend is down without a call when no backend URL is set", async () => {
      const fetchFn = answering(200, "{}");

      expect(await fetchDimensionScores(undefined, 7, "2026-Q3", "L2", fetchFn)).toEqual({ status: "down" });
      expect(fetchFn).not.toHaveBeenCalled();
    });
  });

  describe("fetchAnalytics", () => {
    it("calls the analytics endpoint with the quarter and returns the body", async () => {
      const fetchFn = answering(200, '{"quarter":"2026-Q3"}');

      const result = await fetchAnalytics(BASE_URL, 7, "2026-Q3", fetchFn);

      expect(result).toEqual({ status: "ok", data: { quarter: "2026-Q3" } });
      expect(vi.mocked(fetchFn).mock.calls[0][0]).toBe(
        "http://localhost:8080/api/developers/7/analytics?quarter=2026-Q3",
      );
    });

    it("says not found when the backend answers 404", async () => {
      expect(await fetchAnalytics(BASE_URL, 9, "2026-Q3", answering(404, "{}"))).toEqual({ status: "not-found" });
    });
  });
});
