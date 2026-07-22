# Design System: SplitSmith

## 1. Visual Theme & Atmosphere
A clinical, high-contrast, editorial layout. Flat card architecture, micro-monochrome surfaces, and sharp line dividers. Focuses entirely on numerical clarity and structured spacing, like a high-end physical ledger. The atmosphere is clinical, responsive, and precise, utilizing negative space and typography scale to guide the eye.

- **Density:** 6/10 (Balanced, data-focused ledger layout)
- **Variance:** 7/10 (Asymmetric split views, right-aligned monetary rows, offset details)
- **Motion:** 5/10 (Fluid transition offsets, spring physics on button clicks, fade-in loading state cards)

---

## 2. Color Palette & Roles
- **Canvas Chalk** (#F9F9F7) — Primary background canvas, warm natural cream tint
- **Pure Surface** (#FFFFFF) — Primary container fill, card backgrounds
- **Ink Primary** (#1A1A1A) — Primary text, buttons, high-contrast headers
- **Ink Muted** (#6E6E6A) — Secondary text, descriptions, inactive states
- **Whisper Border** (#E2E2DF) — Flat 1px borders, table dividers
- **Alert Crimson** (#DC2626) — Negative numbers, outstanding debts, error states
- **Success Emerald** (#16A34A) — Positive balances, settled states, verification checks
- **Accent Indigo** (#4F46E5) — Action indicators, focus states, interactive controls only

*(Strictly no purple/neon background glows, and no gradient fills)*

---

## 3. Typography Rules
- **Display & UI Headers:** `DM Sans` (semi-bold) — Track-tight, controlled scale, weight-driven hierarchy.
- **Body Text:** `DM Sans` (regular/medium) — Clean, geometric, relaxed line height.
- **Mono:** `DM Mono` (medium) — Used strictly for all numbers, currency figures, and dates, guaranteeing table alignment.
- **Banned:** `Inter`, `Roboto`, and generic system sans-serifs. Serif headings are banned inside this application to maintain a precise financial utility interface.

---

## 4. Component Stylings
- **Buttons:** Flat rectangles with sharp corners (4px radius). No outer glows or shadows. Primary buttons use `Ink Primary` fill with `Canvas Chalk` text. Secondary buttons use `Whisper Border` outlines.
- **Cards:** Flat containers with `1px` border of `Whisper Border`. 0 elevation and 0 box shadow. Used only to partition layouts.
- **Inputs/Fields:** Flat borders. Label is positioned above the input in `Ink Muted` (small caps, track-wide). Active focus changes border color to `Accent Indigo`.
- **Loaders:** Skeleton blocks matching the exact dimensions of cards and text rows. No circular rotating spinners.
- **Empty States:** Clean layout with centered text block and direct action button — no placeholder illustration or emoji.

---

## 5. Layout Principles
- **Ledger Layout:** Two-column split layouts where category and descriptions are left-aligned, and monetary transactions are strictly right-aligned using `DM Mono`.
- **Navigation:** Standard bottom navigation bar with flat outlined line-icons (no fill). Height is capped to `56dp`.
- **Responsive collapse:** Grid components collapse to a single-column layout on screens narrower than `768px`.
- **Spacing:** Fluid margins based on standard `8dp` increments (8dp, 16dp, 24dp, 32dp).

---

## 6. Motion & Interaction
- **Interactions:** Direct slide transitions when pushing screens. Button active states use a immediate scale offset (`scale: 0.97`) to simulate physical buttons.
- **State Swapping:** Bottom sheets reveal via vertical translate spring animation (`stiffness: 120, damping: 22`).
- **Hardware Acceleration:** Animations restricted to `transform` and `opacity`.

---

## 7. Anti-Patterns (Banned)
- No emojis anywhere in the app UI, codebase, or content logs.
- No gradients on text, buttons, or backgrounds.
- No card nesting (do not place cards inside other card containers).
- No round placeholder numbers (use realistic, non-prefixed ledger examples).
- No AI marketing jargon ("seamlessly split", "next-gen dashboard").
- No circular loading spinners.
