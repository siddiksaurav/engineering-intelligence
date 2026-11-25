import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { revalidatePath } from "next/cache";
import { saveReasonMark } from "./actions";

vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));

const IDLE = { status: "idle" } as const;

function filledForm(): FormData {
  const formData = new FormData();
  formData.set("reasonRight", "yes");
  formData.set("verdict-0", "right");
  return formData;
}

describe("saveReasonMark", () => {
  let fetchFn: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.stubEnv("BACKEND_URL", "http://backend.test:9000");
    fetchFn = vi.fn();
    vi.stubGlobal("fetch", fetchFn);
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    vi.mocked(revalidatePath).mockReset();
  });

  it("puts the mark to the backend and refreshes the item page and the blind check page", async () => {
    fetchFn.mockResolvedValue(new Response('{"reasonRight":true}', { status: 200 }));

    const state = await saveReasonMark(5, ["A"], IDLE, filledForm());

    expect(state).toEqual({ status: "saved" });
    const [url, init] = fetchFn.mock.calls[0];
    expect(url).toBe("http://backend.test:9000/api/evidence-items/5/reason-mark");
    expect(init.method).toBe("PUT");
    expect(JSON.parse(init.body)).toEqual({
      reasonRight: true,
      problemMarks: [{ name: "A", verdict: "right" }],
      note: null,
    });
    expect(revalidatePath).toHaveBeenCalledWith("/items/5");
    expect(revalidatePath).toHaveBeenCalledWith("/blind-check");
  });

  it("shows the detail of a refusal and refreshes nothing", async () => {
    fetchFn.mockResolvedValue(new Response('{"detail":"Item 5 has no label yet"}', { status: 409 }));

    expect(await saveReasonMark(5, ["A"], IDLE, filledForm())).toEqual({
      status: "refused",
      message: "Item 5 has no label yet",
    });
    expect(revalidatePath).not.toHaveBeenCalled();
  });

  it("asks for a missing verdict without calling the backend", async () => {
    const state = await saveReasonMark(5, ["A", "B"], IDLE, filledForm());

    expect(state).toEqual({ status: "refused", message: 'Give a verdict for the problem "B".' });
    expect(fetchFn).not.toHaveBeenCalled();
  });

  it("says backend down when the backend cannot be reached", async () => {
    vi.spyOn(console, "warn").mockImplementation(() => {});
    fetchFn.mockRejectedValue(new TypeError("fetch failed"));

    expect(await saveReasonMark(5, ["A"], IDLE, filledForm())).toEqual({ status: "refused", message: "backend down" });
  });
});
