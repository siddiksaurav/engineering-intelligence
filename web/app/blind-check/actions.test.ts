import { revalidatePath } from "next/cache";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { uploadLabels } from "./actions";

vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));

const IDLE = { status: "idle" } as const;

function formWith(content: string | null): FormData {
  const formData = new FormData();
  if (content !== null) {
    formData.set("labels", new File([content], "blind-check.csv", { type: "text/csv" }));
  }
  return formData;
}

describe("uploadLabels", () => {
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

  it("posts the file as text/csv, gives the counts back and refreshes the page", async () => {
    fetchFn.mockResolvedValue(new Response('{"labeledNow":12,"alreadyLabeled":3,"notLabeledYet":15}', { status: 200 }));

    const state = await uploadLabels(IDLE, formWith("item_id,complexity,note\n1,low,\n"));

    expect(state).toEqual({ status: "imported", labeledNow: 12, alreadyLabeled: 3, notLabeledYet: 15 });
    const [url, init] = fetchFn.mock.calls[0];
    expect(url).toBe("http://backend.test:9000/api/blind-check/labels");
    expect(init.method).toBe("POST");
    expect(init.headers).toEqual({ "Content-Type": "text/csv" });
    expect(new TextDecoder().decode(init.body)).toBe("item_id,complexity,note\n1,low,\n");
    expect(revalidatePath).toHaveBeenCalledWith("/blind-check");
  });

  it("gives every wrong row of a refused file back", async () => {
    const problems = [{ line: 3, itemId: "17", value: "mediun", problem: "unknown complexity" }];
    fetchFn.mockResolvedValue(
      new Response(JSON.stringify({ message: "1 rows of the file are wrong; nothing was stored", problems }), {
        status: 400,
      }),
    );

    expect(await uploadLabels(IDLE, formWith("x"))).toEqual({
      status: "refused",
      message: "1 rows of the file are wrong; nothing was stored",
      problems,
    });
    expect(revalidatePath).not.toHaveBeenCalled();
  });

  it("gives the message alone for a wrong header", async () => {
    fetchFn.mockResolvedValue(
      new Response('{"message":"The header has no item_id column","problems":[]}', { status: 400 }),
    );

    expect(await uploadLabels(IDLE, formWith("x"))).toEqual({
      status: "refused",
      message: "The header has no item_id column",
      problems: [],
    });
  });

  it("asks for a file without calling the backend when none or an empty one is chosen", async () => {
    const expected = { status: "refused", message: "Choose the file you filled in.", problems: [] };

    expect(await uploadLabels(IDLE, formWith(null))).toEqual(expected);
    expect(await uploadLabels(IDLE, formWith(""))).toEqual(expected);
    expect(fetchFn).not.toHaveBeenCalled();
  });

  it("says backend down when the backend cannot be reached", async () => {
    vi.spyOn(console, "warn").mockImplementation(() => {});
    fetchFn.mockRejectedValue(new TypeError("fetch failed"));

    expect(await uploadLabels(IDLE, formWith("x"))).toEqual({
      status: "refused",
      message: "backend down",
      problems: [],
    });
  });
});
