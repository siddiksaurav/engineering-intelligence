import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { ItemSummary } from "@/lib/items";
import { Drill, ofPullRequests } from "./Drill";

const SUMMARY: ItemSummary = {
  id: 41,
  type: "test_change",
  date: "2026-07-03",
  quarter: "2026-Q3",
  title: "Add spec for login",
  sourceUrl: "https://github.com/opf/openproject/pull/141",
  links: [],
  person: { personId: 7, displayName: "ada" },
  tier: "T3",
  current: true,
  currentItemId: null,
};

function render(count: Parameters<typeof Drill>[0]["count"], summaries: ItemSummary[] = []): string {
  return renderToStaticMarkup(
    <Drill label="Reviewed" count={count} summariesById={new Map(summaries.map((summary) => [summary.id, summary]))} />,
  );
}

describe("Drill", () => {
  it("folds open to the items with their type and tier, and to the pull requests on GitHub", () => {
    const html = render({ count: 2, evidenceItemIds: [41, 42], pullRequestNumbers: [141, 142] }, [SUMMARY]);

    expect(html).toContain("<details");
    expect(html).toContain("Reviewed: 2");
    expect(html).toContain('href="/items/41"');
    expect(html).toContain("Add spec for login");
    expect(html).toContain("Test change, T3");
    expect(html).toContain('href="/items/42"');
    expect(html).toContain("item #42");
    expect(html).toContain('href="https://github.com/opf/openproject/pull/141"');
    expect(html).toContain('href="https://github.com/opf/openproject/pull/142"');
  });

  it("has nothing to open for a count of zero", () => {
    const html = render({ count: 0, evidenceItemIds: [], pullRequestNumbers: [] });

    expect(html).toContain("Reviewed: 0");
    expect(html).not.toContain("<details");
  });

  it("lists only pull requests for a count made of pull request numbers", () => {
    const html = render(ofPullRequests([201, 202]));

    expect(html).toContain("Reviewed: 2");
    expect(html).toContain('href="https://github.com/opf/openproject/pull/201"');
    expect(html).not.toContain("/items/");
  });
});
