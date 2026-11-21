import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchEvidenceItems, type DeveloperEvidence, type EvidenceItem } from "@/lib/evidence";
import DeveloperPage from "./page";

vi.mock("@/lib/evidence", () => ({ fetchEvidenceItems: vi.fn() }));

const PERSON = { personId: 7, displayName: "ada" };

const FEATURE: EvidenceItem = {
  id: 1,
  type: "feature_work",
  date: "2026-07-03",
  quarter: "2026-Q3",
  title: "Single sign-on",
  sourceUrl: "https://community.openproject.org/wp/10",
  links: [
    { label: "Work item #10 on OpenProject", url: "https://community.openproject.org/wp/10" },
    { label: "Pull request #101 on GitHub", url: "https://github.com/opf/openproject/pull/101" },
  ],
  tier: null,
  fields: {},
};

const BUG: EvidenceItem = {
  id: 2,
  type: "bug_fix",
  date: "2026-07-09",
  quarter: "2026-Q3",
  title: "Login fails",
  sourceUrl: "https://community.openproject.org/wp/20",
  links: [{ label: "Work item #20 on OpenProject", url: "https://community.openproject.org/wp/20" }],
  tier: null,
  fields: {},
};

function comment(id: number, pullRequestNumber: number, title: string, date: string): EvidenceItem {
  const pullRequestUrl = `https://github.com/opf/openproject/pull/${pullRequestNumber}`;
  return {
    id,
    type: "review_comment_written",
    date,
    quarter: "2026-Q4",
    title,
    sourceUrl: `${pullRequestUrl}#discussion_r${id}`,
    links: [
      { label: "Comment on GitHub", url: `${pullRequestUrl}#discussion_r${id}` },
      { label: `Pull request #${pullRequestNumber} on GitHub`, url: pullRequestUrl },
    ],
    tier: null,
    fields: {
      pullRequest: { number: pullRequestNumber, title, url: pullRequestUrl },
      comment: { url: `${pullRequestUrl}#discussion_r${id}`, path: "app/model.rb", isReply: false },
    },
  };
}

const TEST_CHANGE: EvidenceItem = {
  id: 4,
  type: "test_change",
  date: "2026-10-05",
  quarter: "2026-Q4",
  title: "Add spec for login",
  sourceUrl: "https://github.com/opf/openproject/pull/104",
  links: [{ label: "Pull request #104 on GitHub", url: "https://github.com/opf/openproject/pull/104" }],
  tier: { tier: "T2", reason: "Integration tests of the login flow" },
  fields: {
    pullRequest: { number: 104, title: "Add spec for login", url: "https://github.com/opf/openproject/pull/104" },
  },
};

const DOCUMENTATION: EvidenceItem = {
  ...TEST_CHANGE,
  id: 5,
  type: "documentation",
  title: "Explain the API",
  tier: null,
  links: [{ label: "Pull request #105 on GitHub", url: "https://github.com/opf/openproject/pull/105" }],
};

function answering(items: EvidenceItem[]): DeveloperEvidence {
  return { person: PERSON, items };
}

async function render(personId = "7"): Promise<string> {
  return renderToStaticMarkup(await DeveloperPage({ params: Promise.resolve({ personId }) }));
}

describe("DeveloperPage", () => {
  afterEach(() => {
    vi.mocked(fetchEvidenceItems).mockReset();
    vi.unstubAllEnvs();
  });

  it("shows the three sections with their links", async () => {
    vi.mocked(fetchEvidenceItems).mockResolvedValue({
      status: "ok",
      data: answering([comment(30, 103, "Add cache", "2026-10-02"), BUG, FEATURE]),
    });

    const html = await render();

    expect(html).toContain("ada");
    expect(html).toContain("Feature work");
    expect(html).toContain("Bug fix");
    expect(html).toContain("Review comment written");
    expect(html).toContain("Single sign-on");
    expect(html).toContain("Login fails");
    expect(html).toContain("2026-07-03");
    expect(html).toContain("2026-Q3");
    expect(html).toContain('href="https://community.openproject.org/wp/10"');
    expect(html).toContain('href="https://github.com/opf/openproject/pull/101"');
    expect(html).toContain('href="https://github.com/opf/openproject/pull/103#discussion_r30"');
  });

  it("shows a Test change section with its tier", async () => {
    vi.mocked(fetchEvidenceItems).mockResolvedValue({ status: "ok", data: answering([TEST_CHANGE]) });

    const html = await render();

    expect(html).toContain("Test change");
    expect(html).toContain("Add spec for login");
    expect(html).toContain("2026-10-05");
    expect(html).toContain("T2");
    expect(html).toContain("Integration tests of the login flow");
    expect(html).toContain('href="https://github.com/opf/openproject/pull/104"');
  });

  it("shows a section for every new type, and no tier for an item without one", async () => {
    vi.mocked(fetchEvidenceItems).mockResolvedValue({ status: "ok", data: answering([DOCUMENTATION]) });

    const html = await render();

    for (const title of ["Test change", "Refactor and performance", "Security", "Infra and release", "Documentation"]) {
      expect(html).toContain(title);
    }
    expect(html).toContain("Explain the API");
    expect(html).not.toMatch(/>T[123]</);
  });

  it("groups the review comments per pull request", async () => {
    vi.mocked(fetchEvidenceItems).mockResolvedValue({
      status: "ok",
      data: answering([
        comment(31, 103, "Add cache", "2026-10-02"),
        comment(32, 104, "Fix index", "2026-10-02"),
        comment(30, 103, "Add cache", "2026-10-02"),
      ]),
    });

    const html = await render();

    expect(html.match(/Pull request #103/g)).toHaveLength(1);
    expect(html.match(/Pull request #104/g)).toHaveLength(1);
    expect(html).toContain("2 comments");
    expect(html).toContain("1 comment<");
  });

  it("says there are no evidence items when the developer has none", async () => {
    vi.mocked(fetchEvidenceItems).mockResolvedValue({ status: "ok", data: answering([]) });

    const html = await render();

    expect(html).toContain("ada");
    expect(html).toContain("No evidence items");
  });

  it("says the backend is down when it cannot be read", async () => {
    vi.mocked(fetchEvidenceItems).mockResolvedValue({ status: "down" });

    expect(await render()).toContain("backend down");
  });

  it("is not found for an unknown developer", async () => {
    vi.mocked(fetchEvidenceItems).mockResolvedValue({ status: "not-found" });

    await expect(render("99")).rejects.toThrow("NEXT_HTTP_ERROR_FALLBACK;404");
  });

  it("is not found for an id that is no number, without asking the backend", async () => {
    await expect(render("ada")).rejects.toThrow("NEXT_HTTP_ERROR_FALLBACK;404");
    expect(fetchEvidenceItems).not.toHaveBeenCalled();
  });

  it("asks the backend named by BACKEND_URL for the developer", async () => {
    vi.stubEnv("BACKEND_URL", "http://backend.test:9000");
    vi.mocked(fetchEvidenceItems).mockResolvedValue({ status: "ok", data: answering([]) });

    await render("7");

    expect(fetchEvidenceItems).toHaveBeenCalledWith("http://backend.test:9000", 7);
  });
});
