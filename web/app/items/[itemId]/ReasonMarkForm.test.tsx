import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { reasonMarkRequest } from "@/lib/blindCheck";
import type { ReasonMark } from "@/lib/items";
import { ReasonMarkForm } from "./ReasonMarkForm";

vi.mock("./actions", () => ({ saveReasonMark: vi.fn() }));

const PROBLEM_NAMES = ["Single sign-on handshake", "Role mapping"];

const STORED: ReasonMark = {
  reasonRight: false,
  problemMarks: [
    { name: "Single sign-on handshake", verdict: "right" },
    { name: "Role mapping", verdict: "should_be_routine" },
  ],
  note: "The second problem is plain CRUD",
  markedAt: "2026-11-27T08:00:00Z",
};

function render(stored: ReasonMark | null): string {
  return renderToStaticMarkup(<ReasonMarkForm itemId={5} problemNames={PROBLEM_NAMES} stored={stored} />);
}

/** What a browser would send when the user ticks these inputs: the form data of the radios named in the markup. */
function formDataOf(html: string, verdictOfProblem: string[]): FormData {
  const verdictFields = [...html.matchAll(/type="radio"[^>]*name="(verdict-\d+)"/g)].map((match) => match[1]);
  const formData = new FormData();
  formData.set("reasonRight", "yes");
  [...new Set(verdictFields)].forEach((field, index) => formData.set(field, verdictOfProblem[index]));
  formData.set("note", "  checked on the pull request ");
  return formData;
}

/** The radios that are ticked, as "name=value". */
function checkedChoices(html: string): string[] {
  return [...html.matchAll(/<input[^>]*>/g)]
    .map((match) => match[0])
    .filter((input) => input.includes('checked=""'))
    .map((input) => `${/name="([^"]*)"/.exec(input)?.[1]}=${/value="([^"]*)"/.exec(input)?.[1]}`);
}

describe("ReasonMarkForm", () => {
  it("saving sends the verdict of every problem", () => {
    const html = render(null);

    const request = reasonMarkRequest(formDataOf(html, ["right", "should_be_no_ready_answer"]), PROBLEM_NAMES);

    expect(request).toEqual({
      ok: true,
      body: {
        reasonRight: true,
        problemMarks: [
          { name: "Single sign-on handshake", verdict: "right" },
          { name: "Role mapping", verdict: "should_be_no_ready_answer" },
        ],
        note: "checked on the pull request",
      },
    });
  });

  it("names every problem and offers the five verdicts for each", () => {
    const html = render(null);

    expect(html).toContain("Single sign-on handshake");
    expect(html).toContain("Role mapping");
    expect(html.match(/name="verdict-0"/g)).toHaveLength(5);
    expect(html.match(/name="verdict-1"/g)).toHaveLength(5);
    for (const words of [
      "Depth is right",
      "Should be routine",
      "Should be answer exists",
      "Should be no ready answer",
      "Not a real problem of this work",
    ]) {
      expect(html).toContain(words);
    }
    expect(html).toContain("The AI&#x27;s reason is right");
  });

  it("selects nothing without a stored mark", () => {
    expect(checkedChoices(render(null))).toEqual([]);
  });

  it("shows the stored mark filled in", () => {
    const html = render(STORED);

    expect(checkedChoices(html)).toEqual(["reasonRight=no", "verdict-0=right", "verdict-1=should_be_routine"]);
    expect(html).toContain("The second problem is plain CRUD");
  });
});

describe("reasonMarkRequest", () => {
  it("asks for an answer to the reason and a verdict for every problem", () => {
    expect(reasonMarkRequest(new FormData(), PROBLEM_NAMES)).toEqual({
      ok: false,
      message: 'Answer "The AI\'s reason is right".',
    });

    const formData = new FormData();
    formData.set("reasonRight", "no");
    formData.set("verdict-0", "right");
    expect(reasonMarkRequest(formData, PROBLEM_NAMES)).toEqual({
      ok: false,
      message: 'Give a verdict for the problem "Role mapping".',
    });
  });

  it("refuses a verdict the backend does not know and sends no note when it is blank", () => {
    const formData = new FormData();
    formData.set("reasonRight", "no");
    formData.set("verdict-0", "wrong");
    expect(reasonMarkRequest(formData, ["A"])).toEqual({
      ok: false,
      message: 'Give a verdict for the problem "A".',
    });

    formData.set("verdict-0", "not_a_problem");
    formData.set("note", "   ");
    expect(reasonMarkRequest(formData, ["A"])).toEqual({
      ok: true,
      body: { reasonRight: false, problemMarks: [{ name: "A", verdict: "not_a_problem" }], note: null },
    });
  });
});
