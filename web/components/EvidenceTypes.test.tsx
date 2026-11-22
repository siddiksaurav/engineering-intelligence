import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { TypeCounts } from "@/lib/evidence";
import { TypeBar, TypeLegend } from "./EvidenceTypes";

const COUNTS: TypeCounts = {
  featureWork: 2,
  bugFix: 0,
  reviewCommentWritten: 5,
  testChange: 0,
  refactorPerformance: 0,
  security: 0,
  infraRelease: 0,
  documentation: 0,
};

describe("TypeBar", () => {
  it("draws one segment per type that has items, in the colour of the type", () => {
    const html = renderToStaticMarkup(<TypeBar label="Evidence of ada" counts={COUNTS} />);

    expect(html).toContain('title="Feature work: 2"');
    expect(html).toContain('title="Review comment written: 5"');
    expect(html).toContain("var(--type-feature-work)");
    expect(html).not.toContain("Bug fix");
  });
});

describe("TypeLegend", () => {
  it("names all eight types, also those without items", () => {
    const html = renderToStaticMarkup(<TypeLegend />);

    for (const title of ["Feature work", "Bug fix", "Security", "Documentation", "Review comment written"]) {
      expect(html).toContain(title);
    }
    expect(html.match(/<li/g)).toHaveLength(8);
  });
});
