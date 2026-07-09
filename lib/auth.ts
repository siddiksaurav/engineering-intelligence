import { redirect } from "next/navigation";
import { createServerSupabase } from "./supabase/server";
import type { AppRole, Profile } from "./types";

export async function getSessionProfile(): Promise<Profile | null> {
  const sb = await createServerSupabase();
  const {
    data: { user },
  } = await sb.auth.getUser();
  if (!user) return null;
  const { data } = await sb.from("profiles").select("*").eq("id", user.id).single();
  return (data as Profile) ?? null;
}

export function roleHome(role: AppRole) {
  return role === "manager" ? "/org" : role === "lead" ? "/team" : "/today";
}

export async function requireRole(...roles: AppRole[]): Promise<Profile> {
  const p = await getSessionProfile();
  if (!p) redirect("/login");
  if (p.status !== "active") redirect("/login?pending=1");
  if (roles.length && !roles.includes(p.role)) redirect(roleHome(p.role));
  return p;
}
