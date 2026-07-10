import type { Metadata } from "next";
import { Plus_Jakarta_Sans, Manrope, JetBrains_Mono } from "next/font/google";
import "./globals.css";

// Telemetry type system: Plus Jakarta Sans carries the display personality — a
// rounder, warmer geometric-humanist face than a pure geometric sans, chosen so
// headings on approval/review screens read as considered rather than clinical.
// Manrope is the warm, legible body face, and JetBrains Mono is the utility face
// for eyebrows, metrics, and dates.
const displayFont = Plus_Jakarta_Sans({
  variable: "--font-display",
  subsets: ["latin"],
  weight: ["500", "600", "700"],
});

const bodyFont = Manrope({
  variable: "--font-sans",
  subsets: ["latin"],
});

const monoFont = JetBrains_Mono({
  variable: "--font-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Engineering Intelligence",
  description: "Activity, approvals, and blockers across every team.",
};

// Applies the saved (or system) theme before first paint so dark mode never
// flashes. Kept inline and dependency-free on purpose — it must run before the
// body renders.
const themeScript = `(function(){try{var t=localStorage.getItem('theme');var d=t?t==='dark':window.matchMedia('(prefers-color-scheme: dark)').matches;if(d)document.documentElement.classList.add('dark');}catch(e){}})();`;

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="en"
      className={`${displayFont.variable} ${bodyFont.variable} ${monoFont.variable} h-full antialiased`}
      suppressHydrationWarning
    >
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeScript }} />
      </head>
      <body className="min-h-full flex flex-col">{children}</body>
    </html>
  );
}
