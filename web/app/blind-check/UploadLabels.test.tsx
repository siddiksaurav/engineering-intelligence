import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import type { UploadState } from "@/lib/blindCheck";
import { UploadLabels, UploadOutcome } from "./UploadLabels";

vi.mock("./actions", () => ({ uploadLabels: vi.fn() }));

describe("UploadLabels", () => {
  it("offers a file field for the filled in file and an upload button", () => {
    const html = renderToStaticMarkup(<UploadLabels />);

    expect(html).toMatch(/<input[^>]*type="file"[^>]*name="labels"|<input[^>]*name="labels"[^>]*type="file"/);
    expect(html).toContain("accept=");
    expect(html).toContain("Upload");
  });
});

describe("UploadOutcome", () => {
  it("a refused file lists every wrong row", () => {
    const state: UploadState = {
      status: "refused",
      message: "2 rows of the file are wrong; nothing was stored",
      problems: [
        {
          line: 3,
          itemId: "17",
          value: "mediun",
          problem: "complexity must be low, medium, high, very_high or cannot_judge",
        },
        { line: 9, itemId: null, value: "abc", problem: "item_id is not a number" },
      ],
    };

    const html = renderToStaticMarkup(<UploadOutcome state={state} />);

    expect(html).toContain("2 rows of the file are wrong; nothing was stored");
    expect(html).toContain("Line 3");
    expect(html).toContain("item 17");
    expect(html).toContain("mediun");
    expect(html).toContain("complexity must be low, medium, high, very_high or cannot_judge");
    expect(html).toContain("Line 9");
    expect(html).toContain("abc");
    expect(html).toContain("item_id is not a number");
    expect(html.match(/<li/g)).toHaveLength(2);
  });

  it("shows the three counts of an imported file", () => {
    const html = renderToStaticMarkup(
      <UploadOutcome state={{ status: "imported", labeledNow: 12, alreadyLabeled: 3, notLabeledYet: 15 }} />,
    );

    expect(html).toContain("12 labeled now");
    expect(html).toContain("3 already labeled");
    expect(html).toContain("15 not labeled yet");
  });

  it("shows a refusal without rows as its message alone", () => {
    const html = renderToStaticMarkup(
      <UploadOutcome state={{ status: "refused", message: "backend down", problems: [] }} />,
    );

    expect(html).toContain("backend down");
    expect(html).not.toContain("<li");
  });

  it("shows nothing before an upload", () => {
    expect(renderToStaticMarkup(<UploadOutcome state={{ status: "idle" }} />)).toBe("");
  });
});
