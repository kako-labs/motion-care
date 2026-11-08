# B Refinement Implementation Plan

> Execute using superpowers:executing-plans; check each completed task. Existing user preference opts out of design questions.

**Goal:** Restore CI and refine all owned screens while retaining the approved B concept.
**Architecture:** Existing ViewModels and Repository APIs remain unchanged. Update presentation components and scoped app instrumentation assertions only.
**Tech Stack:** Kotlin/Compose, HTML/CSS/classic JS, existing Gradle/JDK 17 toolchain.
**Spec:** ../specs/2026-11-08-b-refinement-design.md

## Constraints and review focus

- Preserve existing dirty root changes; work in the clean isolated clone/feature branch, baseline backup branch retained.
- No fake records in normal DB; debug fixtures remain isolated. No shared main merge.
- Verify empty seats/driver, narrow width, long labels, missing samples and disabled/saving states.
- App product shell and public contracts remain untouched; shell styling supplied as review patch only.

## Tasks

- [x] CI: use the recorded failing run as red baseline; update app/src/androidTest/.../ScaffoldFlowTest.kt to current entry/back/detail/feedback UI. Assert sample count via TripRepository before/after recreation; run all five tests, then push the scoped CI fix.
- [x] HTML: add a focused refinement stylesheet after existing styles in index/review; remove excess decorative labels through targeted markup/JS only as needed. Preserve data and navigation. Inspect home/records/profile/advice/feedback/environment/routes at representative widths.
- [x] Android: refine MotionCareComponents.kt, BHistoryPanels.kt, departure seat overlay and owned screen headings/forms. Preserve the vehicle asset hash. Keep touch targets accessible while reducing visible ornament.
- [x] Verify: standard verify, UI/storage tests and screenshots; inspect normal app boundary and provide unapplied app-theme review patch. Capture before/after and limits.
- [x] Review/save: one fresh final branch reviewer, resolve findings, commit/push approved feature branch and update PR/Notion/handoff. Confirm remote CI status and main unchanged.

## Ledger

- Baseline: 23e8361, clean clone; root has preexisting dirty/untracked files, preserved.
- CI red: run 42477028026 attempt 2, 3 passed/2 failed; old placeholder text at ScaffoldFlowTest lines 24/40. Original infrastructure timeout was separate.
- Scope ruling: user's explicit request to fix failing CI authorizes editing app/androidTest assertions, not app product flow or shared contracts.

- Final review: no Critical/Important issues; refreshed captures/ledgers. 6/6 isolated UI/storage tests pass at both1024 and1682; normal DB unchanged.
- Ruling: keep review-only B test overlays out of app sources after local tests; only explicitly requested ScaffoldFlowTest fix is committed in app.
- Saved UI revision: 63bba24, pushed to the approved branch and PR #19; Notion refinement section persisted after reload. At this documentation save, latest UI CI was running; recovery revision 1a20003 had passed both jobs. Final live result: PR #19 Checks. No main merge or push.
