"use server";

import { revalidatePath } from "next/cache";
import { writeBackend } from "@/lib/backend";
import { wrongRowsOf, type UploadState } from "@/lib/blindCheck";

const UPLOAD_TIMEOUT_MS = 30_000;

type ImportCounts = { labeledNow: number; alreadyLabeled: number; notLabeledYet: number };

function refusal(message: string): UploadState {
  return { status: "refused", message, problems: [] };
}

/** Sends the filled in file to the backend; the answer is the counts or every wrong row. Nothing is stored on a refusal. */
export async function uploadLabels(_previous: UploadState, formData: FormData): Promise<UploadState> {
  const file = formData.get("labels");
  if (!(file instanceof File) || file.size === 0) {
    return refusal("Choose the file you filled in.");
  }
  const result = await writeBackend<ImportCounts>(
    process.env.BACKEND_URL,
    "/api/blind-check/labels",
    { method: "POST", headers: { "Content-Type": "text/csv" }, body: new Uint8Array(await file.arrayBuffer()) },
    fetch,
    UPLOAD_TIMEOUT_MS,
  );
  if (result.status === "down") {
    return refusal("backend down");
  }
  if (result.status === "refused") {
    return { status: "refused", message: result.message, problems: wrongRowsOf(result.body) };
  }
  revalidatePath("/blind-check");
  return { status: "imported", ...result.data };
}
