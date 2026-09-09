# UI enhancement plan

## Status

- Backlog feature: UI enhancements and polish
- Current state: Ready for implementation
- Product scope: presentation and usability only
- Implementation status: UI-00 through UI-03 complete; UI-04 ready

This document shapes the first feature in `FEATURES.md` independently from the
MVP release slices. Before implementation begins, the selected UI slice must be
added to `MVP_PLAN.md` and claimed in the `STATUS.md` active-work table.

## Tracking

| ID | Slice | Status | Depends on | Intended result | Next action |
| --- | --- | --- | --- | --- | --- |
| UI-00 | Visual direction and reference baseline | Done | — | An approved compact visual language supported by representative screen concepts and design tokens | Preserve the approved decisions in UI-01 implementation and visual checks |
| UI-01 | Compact design foundation | Done | UI-00 | Shared spacing, typography, width, container, and navigation patterns | Preserve the foundation while applying it to individual screens |
| UI-02 | Dense transaction browsing and detail | Done | UI-01 | More ledger rows visible with clearer filtering, editing, and source access | Preserve behavior and row semantics while later slices evolve |
| UI-03 | Compact dashboard hierarchy | Done | UI-01 | Spending totals, comparisons, exceptions, categories, and trends scan efficiently | Preserve exact period and deep-link behavior in later work |
| UI-04 | Onboarding and settings simplification | Ready | UI-01 | Permission, privacy, status, and data controls have clear hierarchy with less scrolling | Apply established components now that the main financial screens are stable |
| UI-05 | Accessibility and visual release gate | Planned | UI-02, UI-03, UI-04 | Responsive, accessible, visually verified primary screens | Run the complete viewport, theme, font-scale, TalkBack, and device matrix |

### Status legend

- `Ready`: sufficiently shaped to claim and begin.
- `Planned`: waits for its listed dependencies.
- `In progress`: claimed in `STATUS.md` and actively being implemented.
- `Blocked`: cannot proceed until a documented decision or dependency is resolved.
- `Done`: acceptance criteria and validation gates passed and documentation is current.

## Outcome

Make SpendTracker feel compact, efficient, consistent, and trustworthy while
preserving the current behavior, privacy model, and transaction semantics. The
target is a dense financial ledger rather than a stack of large, equally
prominent cards.

Compact does not mean reducing accessibility. Interactive controls must retain
at least 48 dp touch targets, content must work with large text, and foreign,
excluded, error, permission, and destructive states must remain explicit.

## Current UI audit

The present expanded feeling comes from several patterns used together:

- Screens use 20-24 dp outer padding and 12-18 dp gaps throughout.
- Reusable information cards add another 20 dp of internal padding regardless
  of whether they contain a major message or a short status.
- Most sections, transaction records, and actions occupy the full available
  width, so content appears stretched on larger phones and tablets.
- Screen headers always show both a prominent title and a large subtitle, even
  when bottom navigation already establishes the destination.
- Each transaction is a separate padded card with a large gap to the next row,
  reducing the number of records visible at once.
- Dashboard category entries are full-width Material text buttons, which gives
  every entry a relatively tall interaction container and makes long values
  prone to wrapping.
- Settings presents informational, operational, privacy, and destructive
  content as similarly weighted cards, weakening visual hierarchy.
- Transaction detail exposes the necessary facts but does not group related
  metadata, editing, and source-message controls into a clear hierarchy.
- The theme defines colors but relies on default Material typography, shapes,
  dimensions, and component emphasis.
- Existing previews and tests do not establish explicit behavior for narrow,
  wide, landscape, dark-theme, or large-font configurations.

## Target design principles

- Use 16 dp phone page margins, 12 dp section gaps, and 8 dp gaps between
  closely related content as the starting scale.
- Preserve 48 dp minimum interactive targets; compact their visual treatment,
  surrounding whitespace, and grouping instead of shrinking hit areas.
- Reserve cards or tonal containers for totals, warnings, permission states,
  and destructive actions. Use grouped sections and divided rows for ordinary
  ledger and settings content.
- Constrain content on wide screens instead of allowing it to stretch:
  onboarding and detail should target a 560-600 dp maximum content width, while
  ledger and settings may use approximately 680-720 dp.
- Prefer a centered single-column phone layout. Introduce a second dashboard
  column only when the available width makes the information easier to scan.
- Use `headlineSmall` for normal page titles and `titleMedium` or `titleSmall`
  for section headings. Reserve display typography for the primary monetary
  result.
- Prefer short inline or trailing actions over full-width buttons. Keep a
  full-width treatment only where a primary onboarding or confirmation action
  benefits from it.
- Never rely on color alone to communicate foreign currency, excluded spend,
  an error, or a destructive action.

## UI-00 - Visual direction and reference baseline

### Goal

Choose and document the visual language before applying it across every screen.
This is a small design task and an approval gate, not a large speculative design
phase.

### Work

- Capture synthetic-data screenshots of the current onboarding, dashboard,
  transactions, detail, and settings screens on a normal phone viewport.
- Produce one representative compact dashboard and transaction-list concept.
- Exercise the concept in light and dark themes and at normal and large text.
- Define a small token sheet covering spacing, content widths, typography,
  shapes, container emphasis, dividers, status treatments, and action hierarchy.
- Compare the concept with the current implementation for visible rows,
  scrolling distance, hierarchy, accessibility, and information retained.
- Record the selected direction and any rejected alternatives in this document.

### Deliverable

A lightweight reference package containing before/after views for the dashboard
and transaction list plus the agreed token sheet. High-fidelity mockups for
every state are not required; the remaining screens should be able to derive
their treatment from these references.

### Acceptance criteria

- [ ] The direction is reviewed before broad component refactoring begins.
- [ ] Compactness is demonstrated with measurable improvements, such as more
      visible transaction rows or less scrolling to reach key information.
- [ ] Foreign and excluded status remains visible in the proposed transaction
      treatment.
- [ ] The concept preserves 48 dp touch targets and works at large text.
- [ ] Light and dark treatments use the same hierarchy and do not rely on color
      alone.
- [ ] The chosen tokens are concrete enough for Compose implementation.

### Working reference

The audit evidence, candidate directions, token proposal, and review questions
are recorded in `UI_00_REFERENCE.md`. UI-00 remains in progress until one
direction is explicitly accepted or revised.

## UI-01 - Compact design foundation

### Scope

- Introduce named spacing, width, shape, and typography tokens based on UI-00.
- Add reusable content-pane, compact section-heading, status/banner, and
  grouped-row components.
- Reduce page-header height and allow subtitles to be omitted where redundant.
- Apply centered maximum content widths to primary screens.
- Refine bottom-navigation presentation while retaining labels and standard
  touch targets.
- Add representative previews for narrow phone, normal phone, wide screen,
  light/dark theme, and increased font scale.

### Acceptance criteria

- [x] Primary content does not stretch indefinitely on a wide viewport.
- [x] New layout spacing comes from shared tokens rather than scattered large
      literals.
- [x] Interactive controls retain at least 48 dp targets.
- [x] Page title, section title, body, metadata, warning, and destructive
      treatments are visually distinct.
- [x] Navigation behavior and UI-state contracts remain unchanged.

### Primary area

- `androidApp/src/main/kotlin/com/spendtracker/app/ui/theme/Theme.kt`
- `androidApp/src/main/kotlin/com/spendtracker/app/ui/components/CommonComponents.kt`
- `androidApp/src/main/kotlin/com/spendtracker/app/ui/SpendTrackerApp.kt`

## UI-02 - Dense transaction browsing and detail

### Transaction list

- Replace separate transaction cards with one grouped surface or divided rows.
- Target a normal row height of roughly 64-72 dp, expanding only when status or
  large text requires it.
- Keep merchant and amount on the primary line, with category and date on the
  secondary line.
- Render foreign and excluded status as compact text badges included in the
  merged accessibility description.
- Replace the large filter area with a compact toolbar showing the filter
  action, active-filter count or chips, and a clear action only when needed.
- Prefer a modal bottom sheet for filters on phones if it improves reachability;
  retain an appropriate larger-screen presentation.

### Transaction detail

- Replace the text-button back action with a compact top bar.
- Group merchant, amount, date, and inclusion into a clear summary region.
- Present kind, direction, account hint, and confidence as aligned label/value
  rows.
- Keep category and inclusion editing together.
- Use one prominent save action and lower emphasis for source-message access.
- Keep the source privacy explanation adjacent to the source action.

### Acceptance criteria

- [x] At least four typical transaction rows are visible above bottom
      navigation on a normal phone viewport.
- [x] Merchant and amount do not collide at common widths.
- [x] Large text may expand rows but does not clip monetary values, statuses,
      filters, or save actions.
- [x] Foreign and excluded status remains visible and announced.
- [x] Filtering, editing, source lookup, and dashboard deep links retain their
      current behavior.

## UI-03 - Compact dashboard hierarchy

### Scope

- Combine period, range, total, transaction count, and comparison into one
  concise summary surface.
- Keep Day/Week/Month controls and historical navigation compact instead of
  stretching them unnecessarily.
- Present excluded and foreign counts as compact tappable facts below the total.
- Replace tall category buttons with divided rows: category/share on the left,
  amount on the right, and count as secondary information where space permits.
- Keep the daily chart compact and improve its zero/empty treatment and
  accessibility summary.
- On sufficiently wide screens, consider category and daily-spend panels side
  by side; retain a centered single column on phones.

### Acceptance criteria

- [x] Total, period, comparison, and exceptional counts are visible together
      near the top of the screen.
- [x] Category rows do not wrap at default font size on common phone widths.
- [x] Category, excluded, and foreign deep links remain accessible and retain
      their exact filters.
- [x] Empty-ledger and no-INR-spend states remain clearly different.
- [x] Daily bars retain meaningful TalkBack descriptions.

## UI-04 - Onboarding and settings simplification

### Onboarding

- Use a centered, width-constrained privacy and permission presentation.
- Reduce repeated disclosure and permission card nesting.
- Keep one primary permission action and one lower-emphasis demo action.
- Show active scan progress as one compact status block.
- Preserve the prominent disclosure and explicit user-initiated permission
  request.

### Settings

- Replace the long stack of equally weighted cards with groups for SMS access
  and scan status, spending and currency rules, privacy, and data controls.
- Use label/value rows for last scan, parser version, and stored count.
- Use a compact inline action for managing SMS access.
- Isolate delete-all in an unmistakably destructive section.
- Long supporting copy may use progressive disclosure only when mandatory
  privacy and lifecycle information remains immediately available.

### Acceptance criteria

- [ ] Access state and last-scan status are visible without scrolling on a
      typical phone.
- [ ] Denied, permanently denied, revoked, scanning, failed, and demo states
      retain distinct recovery actions.
- [ ] No privacy promise or data-lifecycle explanation is removed.
- [ ] Delete-all still requires confirmation and cannot run during scanning or
      another deletion.

## UI-05 - Accessibility and visual release gate

### Automated checks

- Add screenshot or golden baselines for representative UI states; behavior
  tests alone are insufficient for layout regressions.
- Exercise approximately 320 dp, 360 dp, a normal modern phone, and 600 dp+
  widths in light and dark themes.
- Exercise normal, approximately 1.3x, and 2.0x font scales.
- Add semantics checks for transaction rows, monetary totals, status badges,
  chart summaries, toggles, and destructive actions.
- Verify that primary actions remain reachable by scrolling at large text.

### Manual checks

- Inspect TalkBack order and announcements.
- Check contrast and verify that color is never the only status cue.
- Check portrait, landscape, narrow, and wide layouts.
- Run on at least API 26 and the current target API, plus a physical Android
  phone before release.

### Acceptance criteria

- [ ] There is no clipping, overlap, unreachable action, or horizontal
      truncation of financial values in the test matrix.
- [ ] Touch targets remain at least 48 dp.
- [ ] TalkBack traversal follows visual order.
- [ ] Foreign, excluded, error, and destructive states have non-color cues.
- [ ] Existing Compose behavior tests and the new visual/accessibility checks
      pass.

## Recommended sequence

1. UI-00 visual direction and reference baseline.
2. UI-01 compact design foundation.
3. UI-02 transaction list and detail.
4. UI-03 dashboard.
5. UI-04 onboarding and settings.
6. UI-05 accessibility and visual release gate.

Transactions follow the foundation because list density provides the clearest
efficiency improvement and validates the shared row and section system before
it spreads to the other screens.

## Explicit non-goals

This feature must not change:

- parsing, categorization, aggregation, or transaction inclusion rules;
- stored data, database schemas, or migrations;
- SMS permissions, source-message retention, or privacy behavior;
- top-level navigation destinations;
- foreign-currency treatment;
- application networking or the offline-only product posture.

## Required implementation workflow

For each implementation slice:

1. Perform UI implementation on the dedicated `codex/ui-enhancements` branch,
   preferably in its own worktree while other feature slices are active.
2. Promote the slice into `MVP_PLAN.md` with its acceptance criteria and gate.
3. Claim it in the `STATUS.md` active-work table before editing code.
4. Keep changes within the named UI slice unless a direct dependency is found.
5. Add or update Compose tests and previews with the implementation.
6. Run shared JVM tests, Android unit tests, lint, debug assembly, and connected
   Compose tests as required by `TESTING.md`.
7. Record visual/manual checks and final status in `STATUS.md`.

Do not switch a shared checkout carrying another slice's uncommitted changes
onto the UI branch. Create a separate worktree or wait until that checkout is
clean so parallel feature histories remain isolated.
