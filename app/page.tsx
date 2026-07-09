import { redirect } from "next/navigation";
import { getSessionProfile, roleHome } from "@/lib/auth";

export default async function Home() {
  const p = await getSessionProfile();
  redirect(p ? roleHome(p.role) : "/login");
}
