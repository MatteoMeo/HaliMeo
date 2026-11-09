# HaliMeo Intelligent Glass Foundation Implementation Plan

> **For agentic workers:** Execute this plan task-by-task with tests and verification before each commit.

**Goal:** Establish the shared visual foundation for HaliMeo's new premium Intelligent Glass identity without changing app behavior.

**Architecture:** Introduce a focused design-token and shared-surface layer that derives from current persisted theme preferences. Migrate one representative screen/component at a time, beginning with reusable primitives, then expand to the diary, authentication, settings and secondary surfaces in separate plans.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3 Expressive, Haze 2.0.1, existing Google Sans Flex typography and HaliMeoMotion.

**Spec:** `docs/design/HALIMEO_VISUAL_IDENTITY.md`

## Global Constraints

- Work only on `feature/ui-ux-refresh`.
- Keep dark theme only and preserve the selected accent and all persisted preferences.
- Keep Haze selective; no blur on every list item or photo.
- Preserve authentication, invite-only policy, diary, Room, sync, server API and signing behavior.
- Keep reduced-motion and reduced-blur paths.
- Do not change dependencies unless Context7 documentation and a reproducible need justify it.

## Review Focus

- Existing persisted accent / blur / motion values survive app update and theme initialization.
- Small screens and large font scaling do not clip essential controls.
- Low-end rendering path remains legible with blur disabled or reduced.
- Long diary lists do not acquire per-item blur or unnecessary animation.
- Auth and invite flows remain behaviorally identical after visual migration.

---

### Task 1: Audit current theme and UI ownership

**Files:** inspect `MainActivity.kt`, `HaliMeoTypography.kt`, `HaliMeoMotion.kt`, settings persistence and existing tests.

- [ ] Identify current theme model, preference keys, color derivation and all Haze entry points.
- [ ] Map primary screen composables and their shared dependencies.
- [ ] Record baseline test/build status from the branch's current GitHub Actions run.
- [ ] Document the exact files that own theme state; do not edit production code in this audit step.

**Verification:** compare current implementation with the visual identity spec and list migration seams.

### Task 2: Centralized visual tokens

**Files:** create focused design-token source under `app/src/main/java/it/halimeo/app/ui/theme/`; add tests in the existing Android test structure.

- [ ] Write tests for accent-derived roles, contrast-safe foregrounds and deterministic dark surfaces.
- [ ] Run tests and confirm expected failures before implementation.
- [ ] Implement immutable token model and pure accent-to-role derivation.
- [ ] Verify default violet exploration and at least blue, mint and rose accents.
- [ ] Verify existing preference values map without changing stored keys or defaults unexpectedly.

### Task 3: Shared Intelligent Glass primitives

**Files:** create focused glass surface / ambient backdrop / button / field composables under `ui/components/`; reuse existing Haze setup.

- [ ] Test token mapping and reduced-blur fallback.
- [ ] Implement one controlled glass surface API with explicit blur tier, border, shape and content color.
- [ ] Implement ambient backdrop that does not intercept input and does not animate blur radius.
- [ ] Add reusable button and field styles without replacing functional callbacks.
- [ ] Verify semantics, minimum touch targets, focus and disabled states.

### Task 4: Brand and typography integration

**Files:** existing typography, launcher icon resources and theme entry point.

- [ ] Audit current launcher icon variants and persisted selection.
- [ ] Align typography roles with Google Sans Flex; preserve existing variable font controls.
- [ ] Apply brand-level background lighting and icon treatment.
- [ ] Verify launcher alias switching and stored icon choice remain unchanged.

### Task 5: Representative screen migration

**Files:** migrate one low-risk shared surface first, then main diary shell and navbar in a separate reviewable commit.

- [ ] Apply the token system to the chosen representative surface.
- [ ] Verify dynamic accent and reduced blur behavior.
- [ ] Apply shared glass navigation without altering navigation routes or gestures.
- [ ] Run UI/unit tests and inspect build logs.

### Task 6: Screen-family plans

Create separate implementation plans before touching each independent area:
- Diary and Highlights.
- Authentication and invitation.
- Settings and Theme Studio.
- Secondary screens, updater, sync/server status and WebView.

Each plan must preserve existing behavior and specify screen-level tests.

### Task 7: Final verification

- [ ] Run the complete available test suite.
- [ ] Run Android debug build through GitHub Actions.
- [ ] Inspect changed-file diff for unintended business-logic/backend changes.
- [ ] Verify no secrets, signing files or local environment files entered the diff.
- [ ] Report exact workflow URL and result; do not infer success from a commit.

