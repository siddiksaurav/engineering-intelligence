"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ChevronDownIcon } from "lucide-react";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { ThemeToggle } from "@/components/theme-toggle";
import { SignOutButton } from "@/components/sign-out-button";
import type { AppRole, Profile } from "@/lib/types";

type NavProfile = Pick<Profile, "full_name" | "email" | "role">;
type NavLink = { href: string; label: string };

// Links shown per role — mirrors roleHome() in lib/auth.ts (managers see
// everything, leads see team + own, developers see their own views).
function navFor(role: AppRole): NavLink[] {
  const base: NavLink[] = [
    { href: "/today", label: "Today" },
    { href: "/me", label: "My history" },
  ];
  if (role === "developer") return base;
  if (role === "lead") return [...base, { href: "/team", label: "Team" }];
  return [
    ...base,
    { href: "/team", label: "Team" },
    { href: "/org", label: "Org" },
    { href: "/admin", label: "Admin" },
  ];
}

const ROLE_LABEL: Record<AppRole, string> = {
  developer: "Developer",
  lead: "Team lead",
  manager: "Manager",
};

function initials(profile: NavProfile): string {
  const source = profile.full_name?.trim() || profile.email;
  const parts = source.split(/[\s@._-]+/).filter(Boolean);
  const letters = (parts[0]?.[0] ?? "") + (parts[1]?.[0] ?? "");
  return (letters || source.slice(0, 2)).toUpperCase();
}

// Shared top bar: gradient product mark on the left, role-aware nav in the
// middle, theme toggle + account menu on the right. Sticky and translucent so
// page content scrolls under it.
export function AppBar({ profile }: { profile: NavProfile }) {
  const pathname = usePathname();
  const links = navFor(profile.role);
  const name = profile.full_name?.trim() || profile.email;

  return (
    <header className="sticky top-0 z-30 border-b border-border bg-background/70 backdrop-blur-md">
      <div className="mx-auto flex h-14 max-w-6xl items-center gap-4 px-6">
        <Link href="/today" className="flex shrink-0 items-center gap-2.5">
          <span
            aria-hidden
            className="grid h-6 w-6 place-items-center rounded-[7px] text-xs font-bold text-white"
            style={{
              background:
                "linear-gradient(135deg, var(--grad-a), var(--grad-b))",
              boxShadow:
                "0 2px 8px color-mix(in oklch, var(--grad-a) 45%, transparent)",
            }}
          >
            EI
          </span>
          <span className="eyebrow hidden text-foreground/75 sm:inline">
            Engineering Intelligence
          </span>
        </Link>

        <nav className="flex flex-1 items-center gap-0.5 overflow-x-auto text-sm">
          {links.map((item) => {
            const active =
              pathname === item.href || pathname.startsWith(`${item.href}/`);
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={
                  active
                    ? "rounded-md px-3 py-1.5 font-medium text-primary bg-accent"
                    : "rounded-md px-3 py-1.5 font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
                }
              >
                {item.label}
              </Link>
            );
          })}
        </nav>

        <div className="flex shrink-0 items-center gap-1">
          <ThemeToggle />
          <Popover>
            <PopoverTrigger className="flex items-center gap-2 rounded-md py-1 pl-1 pr-1.5 transition-colors hover:bg-muted">
              <span
                aria-hidden
                className="grid h-7 w-7 place-items-center rounded-full bg-accent text-xs font-semibold text-accent-foreground"
              >
                {initials(profile)}
              </span>
              <ChevronDownIcon className="h-4 w-4 text-muted-foreground" />
            </PopoverTrigger>
            <PopoverContent align="end" className="w-64 gap-1.5 p-2">
              <div className="flex items-center gap-3 px-1.5 py-1.5">
                <span
                  aria-hidden
                  className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-accent text-sm font-semibold text-accent-foreground"
                >
                  {initials(profile)}
                </span>
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-foreground">
                    {name}
                  </p>
                  <p className="truncate text-xs text-muted-foreground">
                    {profile.email}
                  </p>
                </div>
              </div>
              <div className="px-1.5">
                <span className="eyebrow">{ROLE_LABEL[profile.role]}</span>
              </div>
              <div className="my-1 h-px bg-border" />
              <SignOutButton />
            </PopoverContent>
          </Popover>
        </div>
      </div>
    </header>
  );
}
