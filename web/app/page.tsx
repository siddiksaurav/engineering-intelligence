import Link from "next/link";
import { checkBackend } from "@/lib/health";

// Asked on every request, on the server. The browser never calls the backend.
export const dynamic = "force-dynamic";

export default async function Home() {
  const status = await checkBackend(process.env.BACKEND_URL);
  return (
    <main>
      <h1>Engineering Intelligence</h1>
      <p>{status === "up" ? "backend up" : "backend down"}</p>
      <p>
        <Link href="/developers">Developers</Link>
      </p>
    </main>
  );
}
