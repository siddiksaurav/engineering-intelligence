"use client";

import { createBrowserSupabase } from "@/lib/supabase/client";

export default function LoginPage() {
  const handleGoogleLogin = async () => {
    const supabase = createBrowserSupabase();
    await supabase.auth.signInWithOAuth({
      provider: "google",
      options: { redirectTo: `${location.origin}/auth/callback` },
    });
  };

  return (
    <main className="flex flex-1 items-center justify-center p-6">
      <div className="surface w-full max-w-sm p-8 text-center">
        <div
          aria-hidden
          className="mx-auto mb-5 grid h-12 w-12 place-items-center rounded-xl text-sm font-bold text-white"
          style={{
            background: "linear-gradient(135deg, var(--grad-a), var(--grad-b))",
            boxShadow:
              "0 6px 20px color-mix(in oklch, var(--grad-a) 40%, transparent)",
          }}
        >
          EI
        </div>
        <p className="eyebrow">Engineering Intelligence</p>
        <h1 className="mt-2 text-2xl font-semibold tracking-tight">Sign in</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          Activity, approvals, and blockers across every team.
        </p>
        <button
          onClick={handleGoogleLogin}
          className="btn-primary mt-6 w-full py-2.5 text-sm"
        >
          Continue with Google
        </button>
      </div>
    </main>
  );
}
