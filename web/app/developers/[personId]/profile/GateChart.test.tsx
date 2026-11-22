import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { GateResult } from "@/lib/profile";
import { GateChart } from "./GateChart";

function gate(overrides: Partial<GateResult> = {}): GateResult {
  return {
    gate: "G1",
    outcome: "PASS",
    unverifiedReason: null,
    personTestedPullRequests: [],
    personPullRequests: [],
    repositoryTestedPullRequests: [],
    repositoryPullRequests: [],
    personSharePercent: 66.7,
    repositorySharePercent: 50,
    differencePoints: -16.7,
    developersInGroup: [1, 2, 3],
    minimumDevelopers: 3,
    limits: { passWithinPoints: 10, farMissBeyondPoints: 25 },
    pullRequestsWithoutDigest: [],
    note: null,
    ...overrides,
  };
}

describe("GateChart", () => {
  it("draws a gate as a chart with the person's and the repository's share", () => {
    const html = renderToStaticMarkup(<GateChart gate={gate()} />);

    expect(html).toContain('aria-label="G1: person 66.7%, repository 50%"');
    expect(html).toMatch(/data-mark="person"[^>]*style="left:66.7%"/);
    expect(html).toMatch(/data-mark="repository"[^>]*style="left:50%"/);
    expect(html).toContain("Person 66.7%");
    expect(html).toContain("Repository 50%");
  });

  it("shades the pass zone and the close-miss zone from the limits of the gate", () => {
    const html = renderToStaticMarkup(<GateChart gate={gate()} />);

    expect(html).toMatch(/data-zone="pass"[^>]*style="left:40%;width:60%"/);
    expect(html).toMatch(/data-zone="close"[^>]*style="left:25%;width:15%"/);
  });

  it("starts a zone at 0 when the limit reaches below it", () => {
    const html = renderToStaticMarkup(<GateChart gate={gate({ repositorySharePercent: 15 })} />);

    expect(html).toMatch(/data-zone="pass"[^>]*style="left:5%;width:95%"/);
    expect(html).toMatch(/data-zone="close"[^>]*style="left:0%;width:5%"/);
  });

  it("shades no zone for a gate without limits", () => {
    expect(renderToStaticMarkup(<GateChart gate={gate({ limits: null })} />)).not.toContain("data-zone");
  });

  it("draws nothing when a share is missing", () => {
    expect(renderToStaticMarkup(<GateChart gate={gate({ personSharePercent: null })} />)).toBe("");
  });

  it("keeps a label inside the chart when its mark is at an end", () => {
    const html = renderToStaticMarkup(
      <GateChart gate={gate({ personSharePercent: 100, repositorySharePercent: 4 })} />,
    );

    expect(html).toMatch(/data-mark="person"[^>]*data-edge="end"|data-edge="end"[^>]*data-mark="person"/);
    expect(html).toMatch(/data-mark="repository"[^>]*data-edge="start"|data-edge="start"[^>]*data-mark="repository"/);
  });
});
