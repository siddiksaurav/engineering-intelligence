import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchDevelopers } from "@/lib/evidence";
import DevelopersPage from "./page";

vi.mock("@/lib/evidence", () => ({ fetchDevelopers: vi.fn() }));

describe("DevelopersPage", () => {
  afterEach(() => {
    vi.mocked(fetchDevelopers).mockReset();
    vi.unstubAllEnvs();
  });

  it("lists each developer with a count per type and a link to the page", async () => {
    vi.mocked(fetchDevelopers).mockResolvedValue({
      status: "ok",
      data: [
        { personId: 7, displayName: "ada", counts: { featureWork: 2, bugFix: 1, reviewCommentWritten: 5 } },
        { personId: 8, displayName: "grace", counts: { featureWork: 0, bugFix: 0, reviewCommentWritten: 0 } },
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
