"use server";

import { revalidatePath } from "next/cache";
import { writeBackend } from "@/lib/backend";
import { reasonMarkRequest, type SaveState } from "@/lib/blindCheck";
import type { ReasonMark } from "@/lib/items";

/** Saves the reason mark of an item through the backend; a refusal gives the backend's words back to the form. */
export async function saveReasonMark(
  itemId: number,
  problemNames: string[],
  _previous: SaveState,
  formData: FormData,
): Promise<SaveState> {
  const request = reasonMarkRequest(formData, problemNames);
  if (!request.ok) {
    return { status: "refused", message: request.message };
  }
  const result = await writeBackend<ReasonMark>(process.env.BACKEND_URL, `/api/evidence-items/${itemId}/reason-mark`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request.body),
  });
  if (result.status === "down") {
    return { status: "refused", message: "backend down" };
  }
  if (result.status === "refused") {
    return { status: "refused", message: result.message };
  }
  revalidatePath(`/items/${itemId}`);
  revalidatePath("/blind-check");
  return { status: "saved" };
}
