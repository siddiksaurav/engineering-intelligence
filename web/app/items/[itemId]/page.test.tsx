import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchItem, type HumanLabel, type ItemBlindCheck, type ItemDetail, type ItemJudgment } from "@/lib/items";
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
  blindCheck: null,
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
    expect(html).toContain('href="/developers/7/profile?quarter=2026-Q3"');
    expect(html).toContain('href="/developers/7?quarter=2026-Q3"');
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

  it("writes a field name of the judgment in words", async () => {
    answering({
      ...FEATURE,
      type: "test_change",
      tier: "T2",
      judgment: {
        ...COMPLEXITY_JUDGMENT,
        kind: "item_tier",
        tier: "T2",
        value: { testKind: "integration" },
        facts: null,
      },
    });

    expect(await render()).toContain("Test kind: integration");
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

  describe("a judgment with problems", () => {
    const PROBLEMS_VALUE = {
      workType: "feature",
      problems: [
        {
          name: "Single sign-on handshake",
          depth: "answer_exists",
          depthReason: "A standard protocol covers it.",
          quote: "uses the OIDC library",
          source: "PR-101",
          earlierExample: "not_checked",
        },
        {
          name: "Role mapping",
          depth: "no_ready_answer",
          depthReason: "No pattern fitted the group model.",
          quote: "",
          source: "",
          earlierExample: "not_checked",
        },
      ],
      problemsAffectEachOther: true,
      problemsAffectEachOtherReason: "The mapping depends on the handshake claims.",
      mistakesStayHidden: false,
      mistakesStayHiddenReason: "",
      reachesAcrossProjects: false,
      thinEvidence: false,
      complexity: "high",
      complexityByTable: "high",
    };
    const PROBLEMS_JUDGMENT: ItemJudgment = {
      ...COMPLEXITY_JUDGMENT,
      tier: "T3",
      value: PROBLEMS_VALUE,
      facts: [
        {
          fact: 'Single sign-on handshake: answer exists, "uses the OIDC library"',
          link: "https://github.com/opf/openproject/pull/101",
        },
        { fact: "Role mapping: no ready answer", link: "https://community.openproject.org/wp/10" },
      ],
    };

    it('shows each problem with its depth and "earlier example not checked"', async () => {
      answering({ ...FEATURE, judgment: PROBLEMS_JUDGMENT });

      const html = await render();

      expect(html).toContain("Problems solved");
      expect(html).toContain("Single sign-on handshake");
      expect(html).toContain("Answer exists");
      expect(html).toContain("A standard protocol covers it.");
      expect(html).toContain("No ready answer");
      expect(html).toContain("No pattern fitted the group model.");
      expect(html).toContain('href="https://github.com/opf/openproject/pull/101"');
      expect(html).toContain("uses the OIDC library");
      expect(html).toContain('href="https://community.openproject.org/wp/10"');
      expect(html.match(/earlier example not checked/g)).toHaveLength(2);
      expect(html).toContain("The problems affect each other");
      expect(html).toContain("The mapping depends on the handshake claims.");
      expect(html).not.toContain("Mistakes stay hidden");
      expect(html).not.toContain("thin evidence");
      expect(html).not.toContain("<h3>Facts</h3>");
    });

    it("shows the two flags and thin evidence when they are set", async () => {
      answering({
        ...FEATURE,
        judgment: {
          ...PROBLEMS_JUDGMENT,
          value: {
            ...PROBLEMS_VALUE,
            mistakesStayHidden: true,
            mistakesStayHiddenReason: "A wrong schema would show only later.",
            thinEvidence: true,
          },
        },
      });

      const html = await render();

      expect(html).toContain("Mistakes stay hidden");
      expect(html).toContain("A wrong schema would show only later.");
      expect(html).toContain("thin evidence");
    });

    it("shows the table line only when it differs", async () => {
      answering({ ...FEATURE, judgment: PROBLEMS_JUDGMENT });
      expect(await render()).not.toContain("The table of spec 6.2");

      answering({
        ...FEATURE,
        judgment: { ...PROBLEMS_JUDGMENT, value: { ...PROBLEMS_VALUE, complexityByTable: "medium" } },
      });
      expect(await render()).toContain("The table of spec 6.2 would give Medium.");
    });

    it("a value with an unknown depth is not shown as problems", async () => {
      const [first, second] = PROBLEMS_VALUE.problems;
      answering({
        ...FEATURE,
        judgment: {
          ...PROBLEMS_JUDGMENT,
          value: { ...PROBLEMS_VALUE, problems: [first, { ...second, depth: "deep" }] },
        },
      });

      expect(await render()).not.toContain("Problems solved");
    });

    it("a judgment without problems shows as before", async () => {
      answering(FEATURE);

      const html = await render();

      expect(html).not.toContain("Problems solved");
      expect(html).toContain("<h3>Facts</h3>");
      expect(html).toContain("Adds a new role table");
    });
  });

  describe("an item of the blind check sample", () => {
    const LABEL: HumanLabel = {
      complexity: "medium",
      cannotJudge: false,
      note: "Only one hard part",
      labeledAt: "2026-11-26T09:00:00Z",
    };
    const withLabel = (label: ItemBlindCheck["label"]): ItemDetail => ({
      ...FEATURE,
      judgment: { ...COMPLEXITY_JUDGMENT, value: { complexity: "high" } },
      blindCheck: { label, reasonMark: null },
    });

    it("a labeled item shows your label beside the AI judgment", async () => {
      answering(withLabel(LABEL));

      const html = await render();

      expect(html).toContain("Your label");
      expect(html).toContain("Medium");
      expect(html).toContain("one step apart");
      expect(html).toContain("Only one hard part");
      expect(html).toContain("Complexity: High");
    });

    it("says same and two or more steps apart", async () => {
      answering(withLabel({ ...LABEL, complexity: "high" }));
      expect(await render()).toContain("same");

      answering(withLabel({ ...LABEL, complexity: "low" }));
      expect(await render()).toContain("two or more steps apart");
    });

    it("hides the AI judgment until the label is imported", async () => {
      answering({
        ...withLabel(null),
        judgment: { ...NOT_JUDGED_YET, state: "hidden_until_labeled" },
      });

      const html = await render();

      expect(html).toContain("hidden until your label is imported");
      expect(html).not.toContain("not judged yet");
      expect(html).not.toContain("Complexity:");
    });

    it("says you could not judge an item that has no complexity", async () => {
      answering(withLabel({ complexity: null, cannotJudge: true, note: null, labeledAt: LABEL.labeledAt }));

      const html = await render();

      expect(html).toContain("You could not judge this item");
      expect(html).not.toContain("steps apart");
    });

    it("shows no label panel for an item outside the sample", async () => {
      answering(FEATURE);

      expect(await render()).not.toContain("Your label");
    });
  });
});
