import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchDevelopers } from "@/lib/evidence";
import { fetchTeamScores } from "@/lib/team";
import DevelopersPage from "./page";

vi.mock("next/navigation", () => ({
  notFound: () => {
    throw new Error("not found");
  },
}));
vi.mock("@/lib/evidence", () => ({ fetchDevelopers: vi.fn() }));
vi.mock("@/lib/team", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/team")>()),
  fetchTeamScores: vi.fn(),
}));

const NO_ITEMS = {
  featureWork: 0,
  bugFix: 0,
  reviewCommentWritten: 0,
  testChange: 0,
  refactorPerformance: 0,
  security: 0,
  infraRelease: 0,
  documentation: 0,
};

async function render(searchParams: Record<string, string> = {}): Promise<string> {
  return renderToStaticMarkup(await DevelopersPage({ searchParams: Promise.resolve(searchParams) }));
}

describe("DevelopersPage", () => {
  beforeEach(() => {
    vi.stubEnv("DEFAULT_QUARTER", "2026-Q3");
    vi.mocked(fetchTeamScores).mockResolvedValue(new Map());
  });

  afterEach(() => {
    vi.mocked(fetchDevelopers).mockReset();
    vi.mocked(fetchTeamScores).mockReset();
    vi.unstubAllEnvs();
  });

  it("lists developers by name with both scores at the chosen level", async () => {
    vi.mocked(fetchDevelopers).mockResolvedValue({
      status: "ok",
      data: [
        { personId: 8, displayName: "grace", counts: NO_ITEMS },
        { personId: 7, displayName: "ada", counts: { ...NO_ITEMS, featureWork: 2 } },
      ],
    });
    vi.mocked(fetchTeamScores).mockResolvedValue(
      new Map([
        [
          7,
          [
            { key: "coding", name: "Coding and code quality", score: 2, workingTier: "T3" },
            { key: "standards", name: "Engineering standards", score: 0, workingTier: null },
          ],
        ],
      ]),
    );

    const html = await render({ level: "L3" });

    expect(fetchTeamScores).toHaveBeenCalledWith(undefined, [7, 8], "2026-Q3", "L3");
    expect(html.indexOf(">ada<")).toBeLessThan(html.indexOf(">grace<"));
    const ada = html.slice(html.indexOf(">ada<"), html.indexOf(">grace<"));
    expect(ada).toContain("Coding and code quality");
    expect(ada).toMatch(/data-score="2"[^>]*>2</);
    expect(ada).toContain('data-tier="T3"');
    expect(ada).toMatch(/data-score="0"[^>]*>0</);
    expect(ada).toContain("no working tier");
    expect(html.slice(html.indexOf(">grace<"))).toContain("not scored");
  });

  it("links each level, keeping the quarter, and marks the chosen one", async () => {
    vi.mocked(fetchDevelopers).mockResolvedValue({ status: "ok", data: [] });

    const html = await render({ quarter: "2026-Q2" });

    expect(html).toContain('href="/developers?quarter=2026-Q2&amp;level=L1"');
    expect(html).toMatch(/aria-current="page"[^>]*>L2</);
    expect(fetchDevelopers).toHaveBeenCalledWith(undefined, "2026-Q2");
  });

  it("shows the total and a bar of a developer's items of the quarter", async () => {
    vi.mocked(fetchDevelopers).mockResolvedValue({
      status: "ok",
      data: [{ personId: 7, displayName: "ada", counts: { ...NO_ITEMS, featureWork: 2, security: 4 } }],
    });

    const html = await render();

    expect(html).toMatch(/>6<\/strong> items/);
    expect(html).toContain('aria-label="Items of ada');
  });

  it("links each developer to the profile, the evidence list and a comparison at the chosen level", async () => {
    vi.mocked(fetchDevelopers).mockResolvedValue({
      status: "ok",
      data: [{ personId: 7, displayName: "ada", counts: NO_ITEMS }],
    });

    const html = await render({ level: "L1" });

    expect(html).toMatch(/<a[^>]*href="\/developers\/7\/profile\?quarter=2026-Q3&amp;level=L1"[^>]*>ada<\/a>/);
    expect(html).toContain('href="/developers/7"');
    expect(html).toContain('href="/developers/compare?a=7&amp;quarter=2026-Q3&amp;level=L1"');
  });

  it("asks for nothing and says why when no quarter is given or set", async () => {
    vi.stubEnv("DEFAULT_QUARTER", "");

    const html = await render();

    expect(fetchDevelopers).not.toHaveBeenCalled();
    expect(fetchTeamScores).not.toHaveBeenCalled();
    expect(html).toContain("DEFAULT_QUARTER");
  });

  it("answers not found for an unknown level", async () => {
    await expect(render({ level: "L9" })).rejects.toThrow("not found");
  });

  it("says there are no developers when none is stored", async () => {
    vi.mocked(fetchDevelopers).mockResolvedValue({ status: "ok", data: [] });

    expect(await render()).toContain("No developers");
  });

  it("says the backend is down when it cannot be read", async () => {
    vi.mocked(fetchDevelopers).mockResolvedValue({ status: "down" });

    expect(await render()).toContain("backend down");
  });

  it("asks the backend named by BACKEND_URL", async () => {
    vi.stubEnv("BACKEND_URL", "http://backend.test:9000");
    vi.mocked(fetchDevelopers).mockResolvedValue({ status: "ok", data: [] });

    await render();

    expect(fetchDevelopers).toHaveBeenCalledWith("http://backend.test:9000", "2026-Q3");
  });
});
