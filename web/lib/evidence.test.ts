import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchDevelopers, fetchEvidenceItems } from "./evidence";

const BACKEND_URL = "http://localhost:8080";

const DEVELOPERS = [
  { personId: 7, displayName: "ada", counts: { featureWork: 2, bugFix: 1, reviewCommentWritten: 5 } },
];

const EVIDENCE = {
  person: { personId: 7, displayName: "ada" },
  items: [
    {
      id: 11,
      type: "bug_fix",
      date: "2026-07-09",
      quarter: "2026-Q3",
      title: "Login fails",
      sourceUrl: "https://community.openproject.org/wp/20",
      links: [{ label: "Work item #20 on OpenProject", url: "https://community.openproject.org/wp/20" }],
      fields: {},
    },
  ],
};

function answering(status: number, body: string): typeof fetch {
  return vi.fn(async () => new Response(body, { status })) as unknown as typeof fetch;
}

function failing(): typeof fetch {
  return vi.fn(async () => {
    throw new TypeError("fetch failed");
  }) as unknown as typeof fetch;
}

describe("evidence client", () => {
  let warn: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    warn = vi.spyOn(console, "warn").mockImplementation(() => {});
  });

  afterEach(() => {
    warn.mockRestore();
  });

  describe("fetchEvidenceItems", () => {
    it("returns the items of a developer", async () => {
      const fetchFn = answering(200, JSON.stringify(EVIDENCE));

      const result = await fetchEvidenceItems(BACKEND_URL, 7, fetchFn);

      expect(result).toEqual({ status: "ok", data: EVIDENCE });
      expect(vi.mocked(fetchFn).mock.calls[0][0]).toBe("http://localhost:8080/api/developers/7/evidence-items");
      expect(vi.mocked(fetchFn).mock.calls[0][1]?.cache).toBe("no-store");
    });

    it("says not found when the backend answers 404", async () => {
      expect(await fetchEvidenceItems(BACKEND_URL, 9, answering(404, "{}"))).toEqual({ status: "not-found" });
    });

    it("says the backend is down when it answers an error, and logs the status", async () => {
      expect(await fetchEvidenceItems(BACKEND_URL, 7, answering(500, "{}"))).toEqual({ status: "down" });
      expect(warn).toHaveBeenCalledWith(
        "Evidence request failed: HTTP 500 from http://localhost:8080/api/developers/7/evidence-items",
      );
    });

    it("says the backend is down when the connection fails", async () => {
      expect(await fetchEvidenceItems(BACKEND_URL, 7, failing())).toEqual({ status: "down" });
      expect(warn).toHaveBeenCalledWith("Evidence request failed: fetch failed");
    });

    it("says the backend is down when the answer is not JSON", async () => {
      expect(await fetchEvidenceItems(BACKEND_URL, 7, answering(200, "<html>"))).toEqual({ status: "down" });
    });

    it("says the backend is down without a call when no backend URL is set", async () => {
      const fetchFn = answering(200, "{}");
      expect(await fetchEvidenceItems(undefined, 7, fetchFn)).toEqual({ status: "down" });
      expect(fetchFn).not.toHaveBeenCalled();
    });
  });

  describe("fetchDevelopers", () => {
    it("returns the developers with their counts", async () => {
      const fetchFn = answering(200, JSON.stringify(DEVELOPERS));

      expect(await fetchDevelopers(BACKEND_URL + "/", fetchFn)).toEqual({ status: "ok", data: DEVELOPERS });
      expect(vi.mocked(fetchFn).mock.calls[0][0]).toBe("http://localhost:8080/api/developers");
    });

    it("says the backend is down when it answers an error", async () => {
      expect(await fetchDevelopers(BACKEND_URL, answering(503, "{}"))).toEqual({ status: "down" });
    });

    it("says the backend is down when the connection fails", async () => {
      expect(await fetchDevelopers(BACKEND_URL, failing())).toEqual({ status: "down" });
    });
  });
});
