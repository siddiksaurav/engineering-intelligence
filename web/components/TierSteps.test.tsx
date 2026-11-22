import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { TierChip } from "./TierChip";
import { TierSteps } from "./TierSteps";

function filledSteps(html: string): number {
  return html.match(/data-filled="true"/g)?.length ?? 0;
}

describe("TierSteps", () => {
  it("fills the steps up to the working tier and marks the expected one", () => {
    const html = renderToStaticMarkup(<TierSteps workingTier="T2" expected={{ level: "L3", tier: "T3" }} />);

    expect(filledSteps(html)).toBe(2);
    expect(html.match(/data-expected="true"/g)).toHaveLength(1);
    expect(html).toMatch(/data-expected="true"[^>]*data-tier="T3"|data-tier="T3"[^>]*data-expected="true"/);
    expect(html).toContain('aria-label="Works at T2; L3 expects T3"');
  });

  it("fills no step and says so when there is no working tier", () => {
    const html = renderToStaticMarkup(<TierSteps workingTier={null} />);

    expect(filledSteps(html)).toBe(0);
    expect(html).toContain('aria-label="No working tier"');
    expect(html).not.toContain("data-expected");
  });

  it("writes the tier under each step at the regular size only", () => {
    expect(renderToStaticMarkup(<TierSteps workingTier="T1" />)).toContain(">T3<");
    expect(renderToStaticMarkup(<TierSteps workingTier="T1" size="small" />)).not.toContain(">T3<");
  });
});

describe("TierChip", () => {
  it("shows the tier", () => {
    expect(renderToStaticMarkup(<TierChip tier="T2" />)).toContain(">T2<");
  });
});
