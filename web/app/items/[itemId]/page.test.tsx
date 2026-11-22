import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchItem, type ItemDetail, type ItemJudgment } from "@/lib/items";
import ItemPage from "./page";

vi.mock("@/lib/items", () => ({ fetchItem: vi.fn() }));

const NOT_JUDGED_YET: ItemJudgment = {
  state: "not_judged_yet",
  kind: null,
  tier: null,
  value: null,
  reason: null,
  facts: null,
  model: null,
  promptVersion: null,
  judgedAt: null,
};

const COMPLEXITY_JUDGMENT: ItemJudgment = {
  state: "judged",
  kind: "feature_work_complexity",
  tier: "T3",
  value: { complexity: "very_high" },
  reason: "Touches the permission model",
  facts: [{ fact: "Adds a new role table", link: "https://github.com/opf/openproject/pull/101" }],
  model: "llama-3.3-70b",
  promptVersion: "feature-work-v2",
  judgedAt: "2026-11-20T10:15:00Z",
};

const FEATURE: ItemDetail = {
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
  person: { personId: 7, displayName: "ada" },
  tier: "T3",
  current: true,
  currentItemId: null,
  judgment: COMPLEXITY_JUDGMENT,
};

async function render(itemId = "1"): Promise<string> {
  return renderToStaticMarkup(await ItemPage({ params: Promise.resolve({ itemId }) }));
}

function answering(item: ItemDetail): void {
  vi.mocked(fetchItem).mockResolvedValue({ status: "ok", data: item });
}

describe("ItemPage", () => {
  afterEach(() => {
    vi.mocked(fetchItem).mockReset();
    vi.unstubAllEnvs();
  });

  it("shows the AI judgment with its reason, facts and a link to the pull request on GitHub", async () => {
    answering(FEATURE);

    const html = await render();

    expect(html).toContain("Single sign-on");
    expect(html).toContain("Feature work");
    expect(html).toContain("2026-07-03");
    expect(html).toContain('href="/developers/7/profile"');
    expect(html).toContain("ada");
    expect(html).toContain('href="https://github.com/opf/openproject/pull/101"');
    expect(html).toContain('href="https://community.openproject.org/wp/10"');
    expect(html).toContain("T3");
    expect(html).toContain("Very high (counts as T3)");
    expect(html).toContain("Touches the permission model");
    expect(html).toContain("Adds a new role table");
    expect(html).toContain("llama-3.3-70b");
    expect(html).toContain("feature-work-v2");
    expect(html).toContain("2026-11-20");
  });

  it("says the item is not judged yet", async () => {
    answering({ ...FEATURE, tier: null, judgment: NOT_JUDGED_YET });

    expect(await render()).toContain("not judged yet");
  });

  it("says a Bug fix is counted and not tiered", async () => {
    answering({
      ...FEATURE,
      type: "bug_fix",
      tier: null,
      judgment: { ...NOT_JUDGED_YET, state: "not_tiered" },
    });

    const html = await render();

    expect(html).toContain("Bug fix");
    expect(html).toContain("counted, not tiered");
  });

  it("shows the comment type and whether a review comment is substantive", async () => {
    answering({
      ...FEATURE,
      type: "review_comment_written",
      tier: "T2",
      judgment: {
        ...COMPLEXITY_JUDGMENT,
        kind: "review_comment",
        tier: "T2",
        value: { commentType: "edge_case", substantive: true },
        facts: null,
      },
    });

    const html = await render();

    expect(html).toContain("edge case");
    expect(html).toContain("Substantive: yes");
  });

  it("names the test kind of a test change", async () => {
    answering({
      ...FEATURE,
      type: "test_change",
      tier: "T2",
      judgment: {
        ...COMPLEXITY_JUDGMENT,
        kind: "item_tier",
        tier: "T2",
        value: { kind: "integration" },
        facts: null,
      },
    });

    expect(await render()).toContain("integration");
  });

  it("says a row that is no longer current and links to the current one", async () => {
    answering({ ...FEATURE, current: false, currentItemId: 9 });

    const html = await render();

    expect(html).toContain("no longer current");
    expect(html).toContain('href="/items/9"');
  });

  it("says a withdrawn row is no longer current when it has no current row", async () => {
    answering({ ...FEATURE, current: false, currentItemId: null });

    const html = await render();

    expect(html).toContain("no longer current");
    expect(html).not.toContain('href="/items/');
  });

  it("says the backend is down when it cannot be read", async () => {
    vi.mocked(fetchItem).mockResolvedValue({ status: "down" });

    expect(await render()).toContain("backend down");
  });

  it("is not found for an unknown item", async () => {
    vi.mocked(fetchItem).mockResolvedValue({ status: "not-found" });

    await expect(render("99")).rejects.toThrow("NEXT_HTTP_ERROR_FALLBACK;404");
  });

  it("is not found for an id that is no number, without asking the backend", async () => {
    await expect(render("abc")).rejects.toThrow("NEXT_HTTP_ERROR_FALLBACK;404");
    expect(fetchItem).not.toHaveBeenCalled();
  });

  it("asks the backend named by BACKEND_URL for the item", async () => {
    vi.stubEnv("BACKEND_URL", "http://backend.test:9000");
    answering(FEATURE);

    await render("1");

    expect(fetchItem).toHaveBeenCalledWith("http://backend.test:9000", 1);
  });
});
