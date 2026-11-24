import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchDevelopers, type Developer, type TypeCounts } from "@/lib/evidence";
import { checkBackend } from "@/lib/health";
import { fetchTeamScores, type TeamScores } from "@/lib/team";
import Home from "./page";

vi.mock("@/lib/health", () => ({ checkBackend: vi.fn() }));
vi.mock("@/lib/evidence", () => ({ fetchDevelopers: vi.fn() }));
vi.mock("@/lib/team", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/team")>()),
  fetchTeamScores: vi.fn(),
}));

const NO_ITEMS: TypeCounts = {
  featureWork: 0,
  bugFix: 0,
  reviewCommentWritten: 0,
  testChange: 0,
  refactorPerformance: 0,
  security: 0,
  infraRelease: 0,
  documentation: 0,
};

const DEVELOPERS: Developer[] = [
  { personId: 7, displayName: "ada", counts: { ...NO_ITEMS, featureWork: 2, bugFix: 1 } },
  { personId: 8, displayName: "grace", counts: { ...NO_ITEMS, reviewCommentWritten: 5 } },
  { personId: 9, displayName: "linus", counts: NO_ITEMS },
];

const SCORES: TeamScores = new Map([
  [
    7,
    [
      { key: "coding", name: "Coding and code quality", score: 2, workingTier: "T3" },
      { key: "standards", name: "Engineering standards", score: 0, workingTier: null },
    ],
  ],
  [8, [{ key: "coding", name: "Coding and code quality", score: 1, workingTier: "T2" }]],
]);

async function render(): Promise<string> {
  return renderToStaticMarkup(await Home());
}

describe("Home", () => {
  beforeEach(() => {
    vi.stubEnv("DEFAULT_QUARTER", "2026-Q3");
    vi.mocked(checkBackend).mockResolvedValue("up");
    vi.mocked(fetchDevelopers).mockResolvedValue({ status: "ok", data: DEVELOPERS });
    vi.mocked(fetchTeamScores).mockResolvedValue(SCORES);
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.mocked(checkBackend).mockReset();
    vi.mocked(fetchDevelopers).mockReset();
    vi.mocked(fetchTeamScores).mockReset();
  });

  it("shows that the backend is up", async () => {
    expect(await render()).toContain("backend up");
  });

  it("shows that the backend is down, and no panel", async () => {
    vi.mocked(checkBackend).mockResolvedValue("down");
    vi.mocked(fetchDevelopers).mockResolvedValue({ status: "down" });

    const html = await render();

    expect(html).toContain("backend down");
    expect(html).not.toContain("Evidence by type");
  });

  it("links to the developer list", async () => {
    expect(await render()).toContain('href="/developers"');
  });

  it("asks the backend named by BACKEND_URL", async () => {
    vi.stubEnv("BACKEND_URL", "http://backend.test:9000");

    await render();

    expect(checkBackend).toHaveBeenCalledWith("http://backend.test:9000");
    expect(fetchDevelopers).toHaveBeenCalledWith("http://backend.test:9000", "2026-Q3");
  });

  it("shows the numbers of the team: developers, evidence items, review comments and developers scored", async () => {
    vi.stubEnv("DEFAULT_QUARTER", "2026-Q3");

    const html = await render();

    expect(html).toMatch(/>3<\/span><span[^>]*>Developers</);
    expect(html).toMatch(/>8<\/span><span[^>]*>Evidence items</);
    expect(html).toMatch(/>5<\/span><span[^>]*>Review comments written</);
    expect(html).toMatch(/>2 of 3<\/span><span[^>]*>Developers scored</);
  });

  it("shows the evidence of the team by type, the largest first, with count and share", async () => {
    const html = await render();

    const byType = html.slice(html.indexOf("Evidence by type"), html.indexOf("Most evidence"));
    expect(byType.indexOf("Review comment written")).toBeLessThan(byType.indexOf("Feature work"));
    expect(byType.indexOf("Feature work")).toBeLessThan(byType.indexOf("Bug fix"));
    expect(byType).toContain(">63%<");
    expect(byType).toContain(">25%<");
    expect(byType).toContain(">13%<");
  });

  it("lists the developers with the most evidence, each a link to the profile", async () => {
    const html = await render();

    const most = html.slice(html.indexOf("Most evidence"));
    expect(most).toMatch(/<a[^>]*href="\/developers\/8\/profile"[^>]*>grace<\/a>/);
    expect(most.indexOf("grace")).toBeLessThan(most.indexOf("ada"));
    expect(most).not.toContain("linus");
  });

  it("shows per dimension how many developers are at each score and each working tier at L2", async () => {
    vi.stubEnv("DEFAULT_QUARTER", "2026-Q3");

    const html = await render();

    expect(fetchTeamScores).toHaveBeenCalledWith(undefined, [7, 8, 9], "2026-Q3", "L2");
    expect(html).toContain("Coding and code quality");
    expect(html).toContain('aria-label="Scores: Score 1 1, Score 2 1"');
    expect(html).toContain('aria-label="Working tiers: T2 1, T3 1"');
    expect(html).toContain('aria-label="Scores: Score 0 1"');
    expect(html).toContain('aria-label="Working tiers: No working tier 1"');
  });

  it("says that the overview needs DEFAULT_QUARTER when it is not set, and asks for nothing", async () => {
    vi.stubEnv("DEFAULT_QUARTER", "");

    const html = await render();

    expect(html).toContain("DEFAULT_QUARTER");
    expect(fetchDevelopers).not.toHaveBeenCalled();
    expect(fetchTeamScores).not.toHaveBeenCalled();
  });

  it("says how scores are made when the quarter has none yet", async () => {
    vi.stubEnv("DEFAULT_QUARTER", "2026-Q3");
    vi.mocked(fetchTeamScores).mockResolvedValue(new Map());

    expect(await render()).toContain("dimension-scoring");
  });

  it("says there are no developers when none is stored", async () => {
    vi.mocked(fetchDevelopers).mockResolvedValue({ status: "ok", data: [] });

    const html = await render();

    expect(html).toContain("No developers");
    expect(html).not.toContain("Evidence by type");
  });
});
