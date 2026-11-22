import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchItem, fetchItemSummaries } from "./items";

const BASE_URL = "http://localhost:8080";

function answering(status: number, body: string): typeof fetch {
  return vi.fn(async () => new Response(body, { status })) as unknown as typeof fetch;
}

describe("items client", () => {
  let warn: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    warn = vi.spyOn(console, "warn").mockImplementation(() => {});
  });

  afterEach(() => {
    warn.mockRestore();
  });

  describe("fetchItemSummaries", () => {
    it("sends the ids comma-separated and makes no call for none", async () => {
      const fetchFn = answering(200, "[]");

      expect(await fetchItemSummaries(BASE_URL, [3, 1, 2], fetchFn)).toEqual({ status: "ok", data: [] });
      expect(vi.mocked(fetchFn).mock.calls[0][0]).toBe("http://localhost:8080/api/evidence-items?ids=3,1,2");

      const noCall = answering(200, "[]");
      expect(await fetchItemSummaries(BASE_URL, [], noCall)).toEqual({ status: "ok", data: [] });
      expect(noCall).not.toHaveBeenCalled();
    });

    it("says the backend is down when it answers an error", async () => {
      expect(await fetchItemSummaries(BASE_URL, [1], answering(400, "{}"))).toEqual({ status: "down" });
    });
  });

  describe("fetchItem", () => {
    it("returns the item with its judgment", async () => {
      const fetchFn = answering(200, '{"id":5}');

      expect(await fetchItem(BASE_URL, 5, fetchFn)).toEqual({ status: "ok", data: { id: 5 } });
      expect(vi.mocked(fetchFn).mock.calls[0][0]).toBe("http://localhost:8080/api/evidence-items/5");
    });

    it("says not found when the backend answers 404", async () => {
      expect(await fetchItem(BASE_URL, 5, answering(404, "{}"))).toEqual({ status: "not-found" });
    });
  });
});
