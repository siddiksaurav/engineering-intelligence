import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchBlindCheck, type BlindCheckNumbers } from "@/lib/blindCheck";
import BlindCheckPage from "./page";

vi.mock("@/lib/blindCheck", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/blindCheck")>()),
  fetchBlindCheck: vi.fn(),
}));

const NO_SAMPLE: BlindCheckNumbers = {
  sampled: 0,
  labeled: 0,
  cannotJudge: 0,
  reasonMarked: 0,
  exactMatch: { count: 0, of: 0, share: null },
  withinOneStep: { count: 0, of: 0, share: null },
  tableExactMatch: { count: 0, of: 0, share: null },
  reasonRight: { count: 0, of: 0, share: null },
  confusion: [],
  problemVerdicts: {
    right: 0,
    should_be_routine: 0,
    should_be_answer_exists: 0,
    should_be_no_ready_answer: 0,
    not_a_problem: 0,
  },
  thinEvidence: {
    items: 0,
    exactMatch: { count: 0, of: 0, share: null },
    reasonRight: { count: 0, of: 0, share: null },
  },
  targets: { exactMatch: 0.7, withinOneStep: 0.95, reasonRight: 0.9 },
  reasonsToMark: [],
  disagreements: [],
  cannotJudgeItems: [],
};

const COMPLETE: BlindCheckNumbers = {
  ...NO_SAMPLE,
  sampled: 30,
  labeled: 30,
  cannotJudge: 2,
  reasonMarked: 28,
  exactMatch: { count: 21, of: 28, share: 0.75 },
  withinOneStep: { count: 27, of: 28, share: 27 / 28 },
  tableExactMatch: { count: 19, of: 28, share: 19 / 28 },
  reasonRight: { count: 24, of: 28, share: 24 / 28 },
  confusion: [
    { label: "low", ai: "low", count: 7 },
    { label: "low", ai: "medium", count: 3 },
    { label: "high", ai: "medium", count: 4 },
  ],
  problemVerdicts: {
    right: 61,
    should_be_routine: 9,
    should_be_answer_exists: 5,
    should_be_no_ready_answer: 2,
    not_a_problem: 4,
  },
  thinEvidence: {
    items: 6,
    exactMatch: { count: 3, of: 5, share: 0.6 },
    reasonRight: { count: 4, of: 5, share: 0.8 },
  },
  disagreements: [
    {
      itemId: 17,
      title: "Single sign-on",
      developer: "ada",
      userComplexity: "high",
      aiComplexity: "medium",
      complexityByTable: "high",
      userNote: "Hidden permission model",
      aiReason: "One standard protocol covers it",
      reasonRight: false,
      markNote: "It missed the mapping",
    },
  ],
  cannotJudgeItems: [{ itemId: 23, title: "Vendor sync", note: "No access to the repository" }],
};

function answering(numbers: BlindCheckNumbers): void {
  vi.mocked(fetchBlindCheck).mockResolvedValue({ status: "ok", data: numbers });
}

async function render(): Promise<string> {
  return renderToStaticMarkup(await BlindCheckPage());
}

describe("BlindCheckPage", () => {
  afterEach(() => {
    vi.mocked(fetchBlindCheck).mockReset();
    vi.unstubAllEnvs();
  });

  it("every number on the page equals the API value", async () => {
    answering(COMPLETE);

    const html = await render();

    expect(html).toContain("21 of 28, 75%");
    expect(html).toContain("27 of 28, 96%");
    expect(html).toContain("24 of 28, 86%");
    expect(html).toContain("19 of 28, 68%");
    expect(html).toContain("target 70%");
    expect(html).toContain("target 95%");
    expect(html).toContain("target 90%");
    expect(html).toMatch(/<span[^>]*>30<\/span><span[^>]*>sampled/);
    expect(html).toMatch(/<span[^>]*>30<\/span><span[^>]*>labeled/);
    expect(html).toMatch(/<span[^>]*>28<\/span><span[^>]*>reasons marked/);
    expect(html).toContain("3 of 5, 60%");
    expect(html).toContain("4 of 5, 80%");
    for (const [words, count] of [
      ["Depth is right", 61],
      ["Should be routine", 9],
      ["Should be answer exists", 5],
      ["Should be no ready answer", 2],
      ["Not a real problem of this work", 4],
    ] as const) {
      expect(html).toMatch(new RegExp(`${words}</dt><dd[^>]*>${count}<`));
    }
  });

  it("says whether each target is met when every sampled item counts", async () => {
    answering(COMPLETE);

    const html = await render();

    expect(html).toContain("target met");
    expect(html).toContain("target not met");
  });

  it("says how many are labeled so far before every sampled item counts", async () => {
    answering({ ...COMPLETE, labeled: 12, reasonMarked: 3, cannotJudge: 0 });

    const html = await render();

    expect(html).toContain("12 of 30 labeled so far");
    expect(html).toContain("3 of 30 marked so far");
    expect(html).not.toContain("target met");
    expect(html).not.toContain("target not met");
  });

  it("shows the label against AI table with a zero for a missing pair", async () => {
    answering(COMPLETE);

    const html = await render();

    const row = /<th scope="row">Low<\/th>((?:<td[^>]*>\d+<\/td>){4})/.exec(html)?.[1];
    expect(row?.match(/>(\d+)</g)).toEqual([">7<", ">3<", ">0<", ">0<"]);
    const highRow = /<th scope="row">High<\/th>((?:<td[^>]*>\d+<\/td>){4})/.exec(html)?.[1];
    expect(highRow?.match(/>(\d+)</g)).toEqual([">0<", ">4<", ">0<", ">0<"]);
  });

  it("lists the disagreements with both answers, the notes and a link to the item", async () => {
    answering(COMPLETE);

    const html = await render();

    expect(html).toContain('href="/items/17"');
    expect(html).toContain("Single sign-on");
    expect(html).toContain("ada");
    expect(html).toContain("Hidden permission model");
    expect(html).toContain("One standard protocol covers it");
    expect(html).toContain("It missed the mapping");
    expect(html).toContain("Vendor sync");
    expect(html).toContain("No access to the repository");
  });

  it("links every item that waits for a reason mark", async () => {
    answering({ ...COMPLETE, reasonsToMark: [{ itemId: 31, title: "Report export" }] });

    const html = await render();

    expect(html).toContain("Reasons to mark");
    expect(html).toContain('href="/items/31"');
    expect(html).toContain("Report export");
  });

  it("with no sample shows the steps and no numbers", async () => {
    answering(NO_SAMPLE);

    const html = await render();

    expect(html).toContain('href="/blind-check/sample.csv"');
    expect(html).toContain("Do not open profiles");
    expect(html).not.toContain("target 70%");
    expect(html).not.toContain("Reasons to mark");
  });

  it("says backend down when the numbers cannot be read", async () => {
    vi.mocked(fetchBlindCheck).mockResolvedValue({ status: "down" });

    expect(await render()).toContain("backend down");
  });
});
