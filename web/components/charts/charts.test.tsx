import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { formatValue } from "./chartFormat";
import { DonutChart } from "./DonutChart";
import { GroupedBarChart } from "./GroupedBarChart";
import { SegmentedBar } from "./SegmentedBar";
import { StackedBarChart } from "./StackedBarChart";

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

describe("DonutChart", () => {
  const slices = [
    { key: "feature_work", label: "Feature work", value: 3, color: "blue" },
    { key: "bug_fix", label: "Bug fix", value: 1, color: "orange" },
    { key: "documentation", label: "Documentation", value: 0, color: "pink" },
  ];

  it("DonutChart lists each slice with its count and share", () => {
    const html = renderToStaticMarkup(<DonutChart label="Pull requests by work type" slices={slices} />);

    expect(html).toContain("Pull requests by work type");
    expect(html).toContain("Feature work: 3 (75%)");
    expect(html).toContain("Bug fix: 1 (25%)");
    expect(html).toContain("Documentation: 0 (0%)");
  });

  it("rounds a share as the profile text does, so 14.5% reads 15%", () => {
    const html = renderToStaticMarkup(
      <DonutChart
        label="Pull requests"
        slices={[
          { key: "a", label: "A", value: 29, color: "blue" },
          { key: "b", label: "B", value: 171, color: "orange" },
        ]}
      />,
    );

    expect(html).toContain("A: 29 (15%)");
  });

  it("says there is no data for a total of 0", () => {
    const html = renderToStaticMarkup(<DonutChart label="Pull requests" slices={[{ ...slices[0], value: 0 }]} />);

    expect(html).toContain("No data");
    expect(html).not.toContain("(0%)");
  });

  it("shows the label of the centre when it is given", () => {
    const html = renderToStaticMarkup(<DonutChart label="Pull requests" slices={slices} centerLabel="4 PRs" />);

    expect(html).toContain("4 PRs");
  });
});

describe("GroupedBarChart", () => {
  const series = [
    { key: "person", label: "Person", color: "blue" },
    { key: "rest", label: "Rest of repository", color: "grey" },
  ];
  const categories = [
    { key: "t2", label: "T2 or above", values: { person: 0.5, rest: 0.25 } },
    { key: "t3", label: "T3", values: { person: null, rest: 0.1 } },
  ];

  it("lists every value of every group, as a percentage for the percent unit, and none for a missing one", () => {
    const html = renderToStaticMarkup(
      <GroupedBarChart label="Feature work level" series={series} categories={categories} unit="percent" />,
    );

    expect(html).toContain("T2 or above: Person 50%, Rest of repository 25%");
    expect(html).toContain("T3: Person no data, Rest of repository 10%");
  });

  it("names the reference line in the summary", () => {
    const html = renderToStaticMarkup(
      <GroupedBarChart
        label="Feature work level"
        series={series}
        categories={categories}
        unit="percent"
        referenceLine={{ value: 1 / 3, label: "One third" }}
      />,
    );

    expect(html).toContain("One third: 33%");
  });

  it("lists plain counts for the count unit", () => {
    const html = renderToStaticMarkup(
      <GroupedBarChart
        label="Complexity"
        series={series}
        categories={[{ key: "low", label: "Low", values: { person: 2, rest: 7 } }]}
        unit="count"
      />,
    );

    expect(html).toContain("Low: Person 2, Rest of repository 7");
  });
});

describe("StackedBarChart", () => {
  const segments = [
    { key: "T1", label: "T1", color: "light" },
    { key: "T2", label: "T2", color: "mid" },
  ];

  it("lists every bar with its segments and total", () => {
    const html = renderToStaticMarkup(
      <StackedBarChart
        label="Substantive comments by tier"
        segments={segments}
        bars={[{ key: "ada", label: "Ada", values: { T1: 2, T2: 3 } }]}
      />,
    );

    expect(html).toContain("Ada: T1 2, T2 3, total 5");
  });
});

describe("formatValue", () => {
  it("rounds a share as the profile text does", () => {
    expect(formatValue(0.145, "percent")).toBe("15%");
    expect(formatValue(0.825, "percent")).toBe("83%");
  });

  it("shows a count as it is and a missing value as no data", () => {
    expect(formatValue(7, "count")).toBe("7");
    expect(formatValue(null, "percent")).toBe("no data");
  });
});
