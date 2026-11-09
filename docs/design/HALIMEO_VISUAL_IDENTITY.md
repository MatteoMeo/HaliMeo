# HaliMeo — Visual Identity & Design System

**Status:** Approved direction — implementation begins with the foundation phase  
**Branch:** `feature/ui-ux-refresh`  
**Reference:** User-provided HaliMeo login / icon / Theme Studio visual (dark premium glassmorphism)

## Product intent

Give HaliMeo an independent, recognizable premium identity. One UI is an ergonomic reference only; do not reproduce Samsung UI or default Material screens. Preserve the app's private-friends character and all existing behavior.

## Visual direction: Intelligent Glass

- Deep ink / midnight-blue base with restrained blue-violet ambient lighting.
- Translucent glass reserved for meaningful floating surfaces: navigation, prominent cards, dialogs, auth panel and Theme Studio previews.
- Use Haze selectively. Avoid stacking multiple blur layers or blurring large scrolling content.
- Hairline borders, soft inner highlights and controlled elevation create glass depth; avoid heavy black shadows and bright neon outlines.
- Dynamic accent is user-selected and must flow through buttons, focus, selected states, icon emphasis and ambient glow. Default visual exploration uses violet/lavender; preserve existing stored accent selection.
- Dark theme only. No photo wallpaper backgrounds.
- Strong readable contrast; glass must never reduce text legibility.
- Premium brand mark and coordinated launcher/app icon family.
- Typography: retain and build on the existing Google Sans Flex variable font setup; establish consistent display/title/body/label roles.
- Motion: restrained, spatial, tactile and fast. Honor reduce-motion and refresh-rate settings; no decorative infinite animations on content-heavy screens.

## Design tokens

Create a centralized token layer for:
- Base/background levels, glass surface levels, elevated surfaces and scrims.
- Accent, accent-muted, on-accent, focus, selected, outline and semantic status colors.
- Surface opacity, border opacity, corner radii, spacing, elevations and blur tiers.
- Motion durations/easings and interaction scales.
- Typography roles.

Tokens must be derived from existing user preferences where applicable; do not silently reset persisted theme, accent, blur, refresh-rate or motion settings.

## Glass performance rules

1. One Haze source per screen content layer; floating glass overlays consume that source.
2. Prefer a small number of blur regions. No blur on every diary item or image tile.
3. Provide a visually coherent no-blur / reduced-blur fallback.
4. Keep scrolling content and photo rendering outside expensive nested blur stacks.
5. Avoid animated blur radius; animate opacity, translation and scale instead.
6. Keep touch targets, semantics, accessibility contrast and text selection intact.

## App-wide scope

1. Brand and foundations: palette, tokens, typography, iconography, app icon and background lighting.
2. Shared components: buttons, fields, cards, chips, sheets, dialogs, navigation and Haze wrappers.
3. Main diary and Highlights: preserve feed semantics, newest-first ordering, existing media and actions.
4. Authentication: login, registration, invite redemption, Google/Facebook, password recovery and linking.
5. Settings and Theme Studio: profile, account, app preferences, accent/theme controls, blur/motion/refresh options.
6. Secondary surfaces: updates, server/sync status, reminders, empty/loading/error states and WebView entry.
7. QA: accessibility, performance, device-size checks, persistence, offline behavior and regression coverage.

## Non-negotiable preservation

- Do not alter invite-only access policy or authentication rules as a side effect of visual work.
- Do not change backend contracts, account identity, sessions, local Room data, diary content, media references, sync semantics or release signing.
- Preserve dark-only preference, dynamic accent, no custom photo background, and existing settings semantics.
- Preserve the user's existing Haze implementation and motion preferences unless a measured, tested change is required.
- No changes to `master` or `feature/inviti-in-app`; work only on `feature/ui-ux-refresh`.
- Do not claim a build is successful until GitHub Actions confirms it.

## Acceptance criteria

- A single source of truth drives colors, shapes, spacing, typography and motion.
- Every redesigned screen uses the same visual language and adapts to selected accent.
- Glass hierarchy is clear: background < content surface < floating glass < modal.
- Reduced blur and reduced motion remain functional and visually intentional.
- Existing flows and data remain unchanged.
- Main UI remains responsive on mid-range Android devices; avoid blanket blur and unnecessary recomposition.
- Build, tests and GitHub Actions are checked before reporting completion.
