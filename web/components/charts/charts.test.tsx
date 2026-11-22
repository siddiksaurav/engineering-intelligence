import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { MeterBar } from "./MeterBar";
import { RingMeter } from "./RingMeter";
import { SegmentedBar } from "./SegmentedBar";

describe("SegmentedBar", () => {
  const segments = [
    { key: "a", label: "Feature work", value: 3, color: "blue" },
    { key: "b", label: "Bug fix", value: 0, color: "orange" },
    { key: "c", label: "Test change", value: 1, color: "green" },
  ];

  it("gives each segment its value as its share of the width and leaves out a segment of 0", () => {
    const html = renderToStaticMarkup(<SegmentedBar label="Evidence" segments={segments} />);

    expect(html).toContain("flex-grow:3");
    expect(html).toContain("flex-grow:1");
    expect(html.match(/flex-grow/g)).toHaveLength(2);
    expect(html).toContain('title="Feature work: 3"');
    expect(html).not.toContain("Bug fix: 0");
  });

  it("names every part in its label, so the picture can be read without seeing it", () => {
    expect(renderToStaticMarkup(<SegmentedBar label="Evidence" segments={segments} />)).toContain(
      'aria-label="Evidence: Feature work 3, Test change 1"',
    );
  });

  it("draws an empty track and says none for a total of 0", () => {
    const html = renderToStaticMarkup(
      <SegmentedBar label="Evidence" segments={[{ key: "a", label: "Feature work", value: 0, color: "blue" }]} />,
    );

    expect(html).toContain('aria-label="Evidence: none"');
    expect(html).not.toContain("flex-grow");
  });
});

describe("RingMeter", () => {
  it("is a meter with the whole percent, written in its middle", () => {
    const html = renderToStaticMarkup(<RingMeter label="Tested" share={5 / 8} />);

    expect(html).toContain('role="meter"');
    expect(html).toContain('aria-valuenow="63"');
    expect(html).toContain('aria-label="Tested"');
    expect(html).toContain(">63%<");
    expect(html).toContain('stroke-dasharray="62.5 100"');
  });

  it("says no PRs and is no meter for a share that is null", () => {
    const html = renderToStaticMarkup(<RingMeter label="Tested" share={null} />);

    expect(html).toContain("no PRs");
    expect(html).not.toContain('role="meter"');
    expect(html).not.toContain("<svg");
  });
});

describe("MeterBar", () => {
  it("is a meter filled to the share, with the count and the percent in its title", () => {
    const html = renderToStaticMarkup(<MeterBar label="Fix" count={6} share={0.24} color="blue" />);

    expect(html).toContain('role="meter"');
    expect(html).toContain('aria-valuenow="24"');
    expect(html).toContain('title="Fix: 6, 24%"');
    expect(html).toContain("width:24%");
  });

  it("stays an empty track for a share of 0", () => {
    const html = renderToStaticMarkup(<MeterBar label="Fix" count={0} share={0} color="blue" />);

    expect(html).toContain('aria-valuenow="0"');
    expect(html).not.toContain("width:");
  });
});
