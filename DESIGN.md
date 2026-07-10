---
name: Engineering Intelligence
description: A warm, colorful telemetry system for daily engineering activity, approvals, and blockers.
colors:
  sky-azure-primary: "oklch(0.58 0.15 236)"
  sky-azure-primary-dark: "oklch(0.68 0.15 234)"
  canvas-bg: "oklch(0.975 0.004 240)"
  canvas-bg-dark: "oklch(0.17 0.02 255)"
  card-surface: "oklch(1 0 0)"
  card-surface-dark: "oklch(0.215 0.022 255)"
  ink-foreground: "oklch(0.23 0.028 255)"
  ink-foreground-dark: "oklch(0.96 0.005 250)"
  muted-foreground: "oklch(0.53 0.025 250)"
  accent-tint: "oklch(0.955 0.016 236)"
  border-hairline: "oklch(0.912 0.008 245)"
  success-signal: "oklch(0.62 0.15 156)"
  warning-signal: "oklch(0.72 0.16 71)"
  info-slate: "oklch(0.5 0.08 250)"
  destructive-signal: "oklch(0.58 0.21 25)"
  gradient-start: "oklch(0.62 0.15 236)"
  gradient-end: "oklch(0.72 0.13 224)"
  chart-1-sky: "oklch(0.58 0.16 236)"
  chart-2-teal: "oklch(0.68 0.13 190)"
  chart-3-amber: "oklch(0.78 0.16 78)"
  chart-4-rose: "oklch(0.63 0.21 15)"
  chart-5-slate: "oklch(0.55 0.05 250)"
typography:
  display:
    fontFamily: "Plus Jakarta Sans, var(--font-sans), sans-serif"
    fontSize: "2.25rem"
    fontWeight: 600
    lineHeight: 1.1
    letterSpacing: "-0.025em"
  subheading:
    fontFamily: "Plus Jakarta Sans, var(--font-sans), sans-serif"
    fontSize: "1.5rem"
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: "-0.025em"
  headline:
    fontFamily: "Plus Jakarta Sans, var(--font-sans), sans-serif"
    fontSize: "1.125rem"
    fontWeight: 600
    lineHeight: 1.3
    letterSpacing: "-0.025em"
  stat:
    fontFamily: "JetBrains Mono, ui-monospace, monospace"
    fontSize: "1.875rem"
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: "-0.01em"
  base:
    fontFamily: "Manrope, Arial, Helvetica, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.6
    letterSpacing: "-0.006em"
  body:
    fontFamily: "Manrope, Arial, Helvetica, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: "-0.006em"
  caption:
    fontFamily: "Manrope, Arial, Helvetica, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 500
    lineHeight: 1.3
    letterSpacing: "0"
  label:
    fontFamily: "JetBrains Mono, ui-monospace, monospace"
    fontSize: "0.6875rem"
    fontWeight: 500
    lineHeight: 1.2
    letterSpacing: "0.12em"
rounded:
  sm: "calc(0.7rem - 4px)"
  md: "calc(0.7rem - 2px)"
  lg: "0.7rem"
  xl: "calc(0.7rem + 4px)"
  full: "999px"
spacing:
  xs: "0.35rem"
  sm: "0.6rem"
  md: "1rem"
  lg: "1.5rem"
  xl: "2rem"
components:
  button-primary:
    backgroundColor: "{colors.sky-azure-primary}"
    textColor: "oklch(0.99 0.005 236)"
    rounded: "{rounded.lg}"
    padding: "0.4rem 0.65rem"
  button-primary-hover:
    backgroundColor: "{colors.sky-azure-primary}"
  button-gradient:
    backgroundColor: "{colors.gradient-start}"
    textColor: "oklch(0.99 0.01 236)"
    rounded: "{rounded.lg}"
    padding: "0.4rem 0.65rem"
  card:
    backgroundColor: "{colors.card-surface}"
    textColor: "{colors.ink-foreground}"
    rounded: "{rounded.lg}"
    padding: "1.5rem"
  pill-warning:
    backgroundColor: "{colors.warning-signal}"
    textColor: "{colors.warning-signal}"
    rounded: "{rounded.full}"
    padding: "0.15rem 0.6rem"
  pill-success:
    backgroundColor: "{colors.success-signal}"
    textColor: "{colors.success-signal}"
    rounded: "{rounded.full}"
    padding: "0.15rem 0.6rem"
---

# Design System: Engineering Intelligence

## 1. Overview

**Creative North Star: "The Sky Ledger"**

Engineering Intelligence reads like a clear-headed daily ledger, not a compliance spreadsheet: open sky/azure signal color, soft-lifted white cards on a cool tinted canvas, and a mono utility face reserved for eyebrows, metrics, and dates. The system already leans colorful on purpose — a two-stop sky→azure gradient anchors every hero band and the primary product mark, semantic pills (warning amber, success green, info slate) carry status at a glance, and the contribution heatmap ramps from a whisper of azure to full-saturation primary. PRODUCT.md's Brand Personality calls for **warm, approachable, human** — explicitly not Jira/Workday-shaped — and the direction confirmed for this pass is to push further into that: more color, more polish, not less. The palette itself is not the gap; the gap is making every screen use it as confidently as the hero bands already do, and refreshing the type pairing so it reads as considered rather than default.

This system explicitly rejects dense, cluttered, form-heavy enterprise software that feels like a chore to open (PRODUCT.md's anti-reference). It also rejects the opposite failure mode: retreating to flat gray "for elegance" whenever a screen isn't a hero. Color is not decoration reserved for the top of the page — it's the vocabulary the whole product speaks.

**Key Characteristics:**
- Cool-tinted canvas with soft dual-corner gradient wash, white cards lifting off it with a hairline border and diffuse shadow
- One signal accent (sky/azure) used with restraint on interactive elements, but spent generously on hero bands, the product mark, and the heatmap ramp
- Plus Jakarta Sans display face on headings — a rounder, warmer geometric-humanist face chosen to read as considered rather than clinical on approval/review screens; Manrope for legible body text; JetBrains Mono exclusively for eyebrows, metrics, and dates
- Semantic color (not just gray + one accent) for status: warning amber, success green, slate-blue info, distinct from the sky primary

## 2. Colors

The palette is a cool sky/azure system: one confident signal color, a cohesive 5-hue chart palette, and a full set of semantic status colors — deliberately not a grayscale-plus-one-accent system.

### Primary
- **Clear Sky Azure** (`oklch(0.58 0.15 236)`, dark mode `oklch(0.68 0.15 234)`): the one signal accent — active nav state, primary buttons, focus rings, links, the product mark. Open and calm rather than corporate-blue; reserved for things that are interactive or "on."

### Secondary
- **Gradient Sky→Azure** (`oklch(0.62 0.15 236)` → `oklch(0.72 0.13 224)`): the vivid anchor treatment for hero bands, the product mark badge, and gradient primary buttons. This is where the system is allowed to be loud — it's the one deliberately saturated surface, not the default.

### Tertiary — Semantic status
- **Success** (`oklch(0.62 0.15 156)`): approved / done states, soft-filled pills.
- **Warning** (`oklch(0.72 0.16 71)`): blocked tasks, pending states.
- **Info / Slate** (`oklch(0.5 0.08 250)`): informational pills — deliberately slate-blue rather than sky, so it never gets mistaken for the primary accent.
- **Destructive** (`oklch(0.58 0.21 25)`): destructive actions, error states.

### Neutral
- **Canvas** (`oklch(0.975 0.004 240)`, dark `oklch(0.17 0.02 255)`): page background — a cool near-white, never true white, always warmed by the corner gradient wash.
- **Card Surface** (`oklch(1 0 0)`, dark `oklch(0.215 0.022 255)`): elevated panel background.
- **Ink** (`oklch(0.23 0.028 255)`, dark `oklch(0.96 0.005 250)`): primary text.
- **Muted Ink** (`oklch(0.53 0.025 250)`): secondary text, eyebrows, timestamps.
- **Hairline Border** (`oklch(0.912 0.008 245)`): the 1px edge on every card and divider.

### Named Rules
**The Signal Restraint Rule.** The flat primary color (not the gradient) is used on ≤10% of any given screen — active nav, focus rings, primary buttons. Its rarity is what makes it register as "signal."

**The Gradient Is Not Decoration Rule.** The sky→azure gradient appears in exactly three places: hero bands, the product mark, and gradient primary CTAs. It never appears as a background wash on a body card — that would flatten its impact.

**The No-Gray-Only Rule.** Status is never conveyed by a single muted-gray badge. Every status has a semantic color (success/warning/info/destructive) with a matching soft-fill background — color carries meaning, not just hierarchy.

## 3. Typography

**Display Font:** Plus Jakarta Sans (with system sans-serif fallback)
**Body Font:** Manrope (with Arial, Helvetica fallback)
**Label/Mono Font:** JetBrains Mono (with ui-monospace fallback)

**Character:** A rounder, warmer geometric-humanist display face paired with a legible humanist body face — the pairing that carries "warm, approachable, human" (PRODUCT.md's Brand Personality) without losing product-UI legibility: headings feel considered rather than clinical, paragraphs feel approachable. Plus Jakarta Sans replaced Space Grotesk, whose monoline geometric letterforms read as "engineered/technical" and undercut the brand personality on exactly the screens (approvals, blocked-task review, sign-in) that most need to avoid feeling like an audit tool. Mono is never used for reading text, only for the telemetry vocabulary (eyebrows, metrics, dates, tabular numbers).

### Hierarchy

Eight roles, fixed rem scale (product register: no fluid `clamp()` sizing). The 0.75–1.125rem "core UI zone" (caption → body → base → headline) holds a tight 1.125–1.167 ratio; the jump from headline to the rarer, singular heading roles (subheading, stat, display) is deliberately larger — each of those appears once per page/card, not repeated densely, so a bigger jump reads as a clear break rather than muddy hierarchy:

- **Display** (600, `text-4xl`/2.25rem, tight 1.1 line-height, `-0.025em` tracking, Plus Jakarta Sans): page-level `<h1>` inside hero bands — "Today", "Org", "Team", "Me", "Admin".
- **Subheading** (600, `text-2xl`/1.5rem, 1.2 line-height, `-0.025em` tracking, Plus Jakarta Sans): secondary page headings outside a hero band — currently the login page's "Sign in".
- **Stat** (600, `text-3xl`/1.875rem, tabular-nums, mono): standalone KPI numbers (e.g. the org dashboard's headline stat) — mono because it's a number to be scanned, not read.
- **Headline** (600, `text-lg`/1.125rem, `-0.025em` tracking, Plus Jakarta Sans): section titles via `SectionHeader`, paired with a mono eyebrow above and a short primary-colored accent bar to the left.
- **Base** (400, `text-base`/1rem, 1.6 line-height): reading-length prose that isn't UI chrome — private notes, descriptions. Capped at 65–75ch measure where it appears.
- **Body** (400, `text-sm`/0.875rem, 1.5 line-height, `-0.006em` tracking): the default UI workhorse — form controls, table cells, most paragraph text that isn't reading-length prose.
- **Caption** (500, `text-xs`/0.75rem, 1.3 line-height): compact meta — filter labels, badges, small status pills, secondary metadata.
- **Label** (500, `0.6875rem`, `0.12em` tracking, uppercase, mono): the `.eyebrow` class — sits above every section title and on metric labels.

### Named Rules
**The Mono-Is-Utility Rule.** JetBrains Mono is reserved for eyebrows, metric values, and dates — never body copy, never headings. It's the "instrument panel" register, used sparingly so it stays meaningful.

**The Tight-Tracking Headings Rule.** All headings (`h1`–`h3`) carry `-0.025em` letter-spacing (Tailwind's `tracking-tight`, applied consistently at every call site; the shared base-layer rule matches this value as the fallback) — never left at browser default, which reads as unstyled.

**The Reading Text Gets Base, Not Body Rule.** Any prose long enough to be *read* rather than *scanned* (private notes, descriptions) uses Base (1rem/16px), not Body (0.875rem/14px) — Body is for UI chrome and short strings, not paragraphs.

## 4. Elevation

Layered, not flat: cards lift softly off the tinted canvas with a hairline border plus a two-layer diffuse shadow (a tight near shadow and a broader ambient one), confirmed as the direction to keep. Depth is structural (it marks "this is a distinct panel"), not decorative — there is no drop-shadow-as-flourish on inline elements.

### Shadow Vocabulary
- **Card rest** (`0 1px 2px oklch(0.23 0.03 255 / 0.05), 0 4px 16px oklch(0.23 0.03 255 / 0.05)`): default state for every `.surface` card.
- **Card hover** (`0 2px 4px oklch(0.23 0.03 255 / 0.06), 0 10px 30px oklch(0.23 0.03 255 / 0.08)`): hover/interactive lift, slightly stronger and more diffuse.
- **Gradient glow** (`0 10px 34px color-mix(in oklch, var(--grad-a) 40%, transparent)`): the colored ambient shadow specific to hero bands and gradient buttons — a tinted glow, not a neutral shadow, reinforcing that this surface is the vivid one.

### Named Rules
**The Soft-Lift Rule.** Every card gets a two-layer shadow (tight + ambient) plus a 1px hairline border — never border-only, never shadow-only. The combination is what keeps cards from reading either flat or heavy.

## 5. Components

### Buttons
- **Shape:** rounded-lg (`0.7rem`), consistent across all sizes.
- **Primary:** `bg-primary` (Clear Sky Azure) with `primary-foreground` text; the gradient variant (`.btn-primary` — sky→azure gradient, white text, tinted ambient shadow) is reserved for the most prominent CTA per page (e.g. "Submit for approval").
- **Hover / Focus:** default variant darkens toward `primary/80`; gradient variant lifts 1px with a stronger glow; all variants get a 3px `ring-ring/50` focus ring plus a border color shift — never focus-outline-only.
- **Secondary / Ghost / Outline / Destructive:** secondary uses the muted-tint surface; ghost is transparent until hover; destructive uses a soft destructive-tinted fill, not a solid red block — consistent with the "soft-fill semantic" rule from Colors.

### Chips / Pills
- **Style:** fully rounded (`rounded-full`/999px), soft-filled background (`color-mix` of the semantic color at 16% into the card color) with matching semantic-colored text and a small solid dot (`::before`) in `currentColor`.
- **State:** warning / info / success variants map directly to item/day status; no unstyled default pill exists — every status gets a semantic mapping.

### Cards / Containers
- **Corner Style:** `rounded-lg` (`0.7rem`).
- **Background:** `--card` (pure white / near-black in dark mode).
- **Shadow Strategy:** see Elevation — soft-lift, always paired with the hairline border.
- **Border:** 1px, `--border` hairline.
- **Internal Padding:** generous, `1.5rem`+ on primary panels.

### Inputs / Fields
- **Style:** `rounded-lg`, 1px `--input` border, transparent background.
- **Focus:** border shifts to `--ring` plus a 3px ring glow — same focus language as buttons, for consistency across all interactive elements.
- **Error / Disabled:** `aria-invalid` gets a destructive border + destructive ring glow; disabled drops to 50% opacity with a muted fill.

### Navigation
- **Style:** sticky, translucent (`bg-background/70` + backdrop-blur) top bar with a gradient product-mark badge on the left, role-aware text links in the center, theme toggle + avatar menu on the right.
- **States:** active link gets `text-primary` on an `--accent` tint pill background; inactive links are muted-foreground until hover, when they get a muted background — no underline-based nav state anywhere.
- **Mobile:** the nav row scrolls horizontally (`overflow-x-auto`) rather than collapsing into a hamburger, keeping every role-appropriate link one tap away.

### Heatmap Calendar (signature component)
A single-hue activity ramp (`heat-0` through `heat-4`) built by mixing the primary sky/azure color into the card surface at increasing strength (24% → 46% → 70% → 100%), so it follows the palette automatically and inverts correctly in dark mode. This is the most distinctive surface in the product — the calendar reads as "sky filling in" rather than a generic green GitHub-style ramp, and it's the strongest expression of the North Star.

## 6. Do's and Don'ts

### Do:
- **Do** spend the sky→azure gradient boldly on hero bands, the product mark, and the primary CTA per page — per the confirmed direction, this system should read as colorful and polished, not restrained-to-the-point-of-flat.
- **Do** give every status (approved, blocked, pending, in-progress) its own semantic color with a soft-filled pill — color is how state is scanned at a glance across dense dashboards.
- **Do** keep the mono eyebrow + accent-bar pattern (`SectionHeader`) as the section-opening signature — it's load-bearing for the "instrument panel, warmed up" personality.
- **Do** keep cards soft-lifted (hairline border + two-layer shadow) — confirmed as the right elevation direction.
- **Do** use the Plus Jakarta Sans + Manrope pairing for all new headings and body copy — it replaced Space Grotesk specifically to serve the warm/human direction; don't reintroduce a colder geometric display face.
- **Do** use the formalized eight-role type scale (Display/Subheading/Stat/Headline/Base/Body/Caption/Label) for any new size — snap to a documented `rem` step rather than an arbitrary Tailwind bracket value (`text-[0.7rem]` etc.).

### Don't:
- **Don't** build anything that reads as Jira- or Workday-shaped: dense, cluttered, form-heavy, chore-like (PRODUCT.md anti-reference, repeated here verbatim).
- **Don't** let the palette collapse into grayscale-plus-one-accent. This was flagged directly: the system "shouldn't be colorless" — every new surface should ask where color earns its place (status, hero, signal), not default to neutral-on-neutral.
- **Don't** use a muted-gray badge for status when a semantic color exists — see the No-Gray-Only Rule.
- **Don't** apply the sky→azure gradient as a background wash on ordinary body cards — it's reserved for hero bands, the mark, and primary CTAs (Gradient Is Not Decoration Rule).
- **Don't** use JetBrains Mono for anything a user has to read at length — it's the utility/label face only.
