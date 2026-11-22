import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { usePathname } from "next/navigation";
import { AppBar } from "./AppBar";

vi.mock("next/navigation", () => ({ usePathname: vi.fn() }));

function currentLinkAt(path: string): string | undefined {
  vi.mocked(usePathname).mockReturnValue(path);
  return renderToStaticMarkup(<AppBar />).match(/aria-current="page"[^>]*>([^<]*)</)?.[1];
}

describe("AppBar", () => {
  it("names the product and links to the overview and the developers", () => {
    vi.mocked(usePathname).mockReturnValue("/");

    const html = renderToStaticMarkup(<AppBar />);

    expect(html).toContain("Engineering Intelligence");
    expect(html).toContain('href="/"');
    expect(html).toContain('href="/developers"');
  });

  it("marks the link of the current section with aria-current", () => {
    expect(currentLinkAt("/")).toBe("Overview");
    expect(currentLinkAt("/developers")).toBe("Developers");
    expect(currentLinkAt("/developers/9/profile")).toBe("Developers");
  });

  it("counts an item page as part of the developers section", () => {
    expect(currentLinkAt("/items/42")).toBe("Developers");
  });
});
