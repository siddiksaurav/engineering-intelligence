import { fetchSampleFile } from "@/lib/blindCheck";

const TEXT = "text/plain; charset=utf-8";

/** The labeling file as a download. The browser never calls the backend, so this passes its CSV through. */
export async function GET(): Promise<Response> {
  const file = await fetchSampleFile(process.env.BACKEND_URL);
  if (file.status === "down") {
    return new Response("backend down", { status: 503, headers: { "Content-Type": TEXT } });
  }
  if (file.status === "refused") {
    return new Response(file.message, { status: file.httpStatus, headers: { "Content-Type": TEXT } });
  }
  return new Response(file.csv, {
    headers: {
      "Content-Type": "text/csv; charset=utf-8",
      "Content-Disposition": 'attachment; filename="blind-check.csv"',
    },
  });
}
