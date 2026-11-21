import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchDevelopers } from "@/lib/evidence";
import DevelopersPage from "./page";

vi.mock("@/lib/evidence", () => ({ fetchDevelopers: vi.fn() }));

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
  afterEach(() => {
    vi.mocked(fetchDevelopers).mockReset();
    vi.unstubAllEnvs();
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

    expect(fetchDevelopers).toHaveBeenCalledWith("http://backend.test:9000");
  });
});
