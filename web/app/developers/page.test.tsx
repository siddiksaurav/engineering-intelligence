import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchDevelopers } from "@/lib/evidence";
import { fetchTeamScores } from "@/lib/team";
import DevelopersPage from "./page";

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

  it("shows each developer's score and working tier per dimension at L2, and not scored without them", async () => {
    vi.mocked(fetchDevelopers).mockResolvedValue({
      status: "ok",
      data: [
        { personId: 7, displayName: "ada", counts: { ...NO_ITEMS, featureWork: 2 } },
        { personId: 8, displayName: "grace", counts: NO_ITEMS },
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

    const html = renderToStaticMarkup(await DevelopersPage());

    expect(fetchTeamScores).toHaveBeenCalledWith(undefined, [7, 8], "2026-Q3", "L2");
    const ada = html.slice(html.indexOf(">ada<"), html.indexOf(">grace<"));
    expect(ada).toContain("Coding and code quality");
    expect(ada).toMatch(/<strong[^>]*>2<\/strong> of 2/);
    expect(ada).toContain('aria-label="Works at T3"');
    expect(ada).toMatch(/<strong[^>]*>0<\/strong> of 2/);
    expect(ada).toContain("no working tier");
    expect(html.slice(html.indexOf(">grace<"))).toContain("not scored");
  });

  it("shows the total of a developer's evidence items", async () => {
    vi.mocked(fetchDevelopers).mockResolvedValue({
      status: "ok",
      data: [{ personId: 7, displayName: "ada", counts: { ...NO_ITEMS, featureWork: 2, security: 4 } }],
    });

    expect(renderToStaticMarkup(await DevelopersPage())).toMatch(/>6<\/strong> items/);
  });

  it("asks for nothing and says why when DEFAULT_QUARTER is not set", async () => {
    vi.stubEnv("DEFAULT_QUARTER", "");

    const html = renderToStaticMarkup(await DevelopersPage());

    expect(fetchDevelopers).not.toHaveBeenCalled();
    expect(fetchTeamScores).not.toHaveBeenCalled();
    expect(html).toContain("DEFAULT_QUARTER");
  });

  it("lists each developer with a count per type and a link to the page", async () => {
    vi.mocked(fetchDevelopers).mockResolvedValue({
      status: "ok",
      data: [
        {
          personId: 7,
          displayName: "ada",
          counts: { ...NO_ITEMS, featureWork: 2, bugFix: 1, reviewCommentWritten: 5, testChange: 3, security: 4 },
        },
        { personId: 8, displayName: "grace", counts: NO_ITEMS },
      ],
    });

    const html = renderToStaticMarkup(await DevelopersPage());

    expect(html).toContain('href="/developers/7"');
    expect(html).toContain('href="/developers/8"');
    expect(html).toContain("ada");
    expect(html).toContain("grace");
    expect(html).toMatch(/Feature work[^0-9]*2/);
    expect(html).toMatch(/Bug fix[^0-9]*1/);
    expect(html).toMatch(/Review comment written[^0-9]*5/);
    expect(html).toMatch(/Test change[^0-9]*3/);
    expect(html).toMatch(/Security[^0-9]*4/);
    for (const label of ["Refactor and performance", "Infra and release", "Documentation"]) {
      expect(html).toContain(label);
    }
  });

  it("each developer links to their profile", async () => {
    vi.mocked(fetchDevelopers).mockResolvedValue({
      status: "ok",
      data: [{ personId: 7, displayName: "ada", counts: NO_ITEMS }],
    });

    const html = renderToStaticMarkup(await DevelopersPage());

    expect(html).toMatch(/<a[^>]*href="\/developers\/7\/profile"[^>]*>ada<\/a>/);
  });

  it("says there are no developers when none is stored", async () => {
    vi.mocked(fetchDevelopers).mockResolvedValue({ status: "ok", data: [] });

    expect(renderToStaticMarkup(await DevelopersPage())).toContain("No developers");
  });

  it("says the backend is down when it cannot be read", async () => {
    vi.mocked(fetchDevelopers).mockResolvedValue({ status: "down" });

    expect(renderToStaticMarkup(await DevelopersPage())).toContain("backend down");
  });

  it("asks the backend named by BACKEND_URL", async () => {
    vi.stubEnv("BACKEND_URL", "http://backend.test:9000");
    vi.mocked(fetchDevelopers).mockResolvedValue({ status: "ok", data: [] });

    await DevelopersPage();

    expect(fetchDevelopers).toHaveBeenCalledWith("http://backend.test:9000", "2026-Q3");
  });
});
