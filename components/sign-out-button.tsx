"use client";

import { useTransition } from "react";
import { useRouter } from "next/navigation";
import { LogOutIcon } from "lucide-react";
import { createBrowserSupabase } from "@/lib/supabase/client";

// Ends the Supabase session and returns to the login screen.
export function SignOutButton() {
  const router = useRouter();
  const [pending, startTransition] = useTransition();

  function signOut() {
    startTransition(async () => {
      await createBrowserSupabase().auth.signOut();
      router.replace("/login");
      router.refresh();
    });
  }

  return (
    <button
      type="button"
      onClick={signOut}
      disabled={pending}
      className="flex w-full items-center gap-2 rounded-md px-2.5 py-2 text-left text-sm font-medium text-foreground transition-colors hover:bg-muted disabled:opacity-50"
    >
      <LogOutIcon className="h-4 w-4 text-muted-foreground" />
      {pending ? "Signing out…" : "Sign out"}
    </button>
  );
}
