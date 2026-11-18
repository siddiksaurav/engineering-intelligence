import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { checkBackend } from "@/lib/health";
import Home from "./page";

vi.mock("@/lib/health", () => ({ checkBackend: vi.fn() }));

describe("Home", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.mocked(checkBackend).mockReset();
  });

  it("shows that the backend is up", async () => {
    vi.mocked(checkBackend).mockResolvedValue("up");
    expect(renderToStaticMarkup(await Home())).toContain("backend up");
  });

  it("shows that the backend is down", async () => {
    vi.mocked(checkBackend).mockResolvedValue("down");
    expect(renderToStaticMarkup(await Home())).toContain("backend down");
  });

  it("asks the backend named by BACKEND_URL", async () => {
    vi.stubEnv("BACKEND_URL", "http://backend.test:9000");
    vi.mocked(checkBackend).mockResolvedValue("up");
    await Home();
    expect(checkBackend).toHaveBeenCalledWith("http://backend.test:9000");
  });
});
