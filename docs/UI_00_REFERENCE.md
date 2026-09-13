# UI-00 visual direction reference

## Review status

- Status: Approved on 2026-09-08
- Reference state: Synthetic dashboard and transaction data only
- Production UI changes: None

## Baseline method

No Android emulator or `adb` executable is available in the current workspace,
so this pass could not capture device pixels from the running application. The
current-state reference is reconstructed directly from the Compose dimensions,
typography, cards, and content in the primary screens. Device screenshots remain
a UI-00 completion check when an emulator or physical device is available.

The baseline represents these current patterns:

- 20 dp screen padding on dashboard, ledger, detail, and settings;
- 24 dp screen padding on onboarding;
- 16-18 dp gaps between major screen elements;
- 20 dp information-card padding and 16 dp transaction-card padding;
- full-width cards, segmented controls, links, and many actions;
- default Material 3 typography and shape scale.

## Concepts to compare

### A - Balanced ledger (recommended)

This direction removes most decorative card boundaries, groups related facts,
and uses divided ledger rows while retaining comfortable reading rhythm. Status
labels remain textual, and interactive rows retain a 48 dp minimum touch target.

Expected normal transaction row: 68 dp. Expected benefit: roughly four to five
typical records visible above bottom navigation on a normal phone, depending on
system insets and header content.

This direction is approved as the implementation baseline.

### B - Maximum density

This direction shortens headers further and targets approximately 60-64 dp
transaction rows. It exposes more data but leaves less room for merchant names,
large text, and persistent foreign/excluded labels. It should be treated as a
density boundary, not the default direction.

### Current treatment

The current treatment is clear but gives similar emphasis to totals, ordinary
content, status, and explanatory copy. Full-width cards and generous nested
padding make the interface feel spacious rather than ledger-like.

## Proposed token sheet

| Token | Proposed value | Use |
| --- | ---: | --- |
| Page margin / compact width | 16 dp | Phone screen edge to content |
| Page margin / narrow width | 12 dp | Width-constrained fallback around 320 dp |
| Section gap | 12 dp | Separate major content groups |
| Related-content gap | 8 dp | Content within a group |
| Tight gap | 4 dp | Label and supporting metadata |
| Group padding | 12-16 dp | Summary and status surfaces |
| Ledger row target | 68 dp | Typical transaction without wrapping |
| Minimum touch target | 48 dp | Every interactive control or row |
| Compact corner | 12 dp | Summary/status surfaces |
| Small corner | 8 dp | Chips, badges, and small tonal elements |
| Detail/onboarding max width | 600 dp | Centered content pane |
| Ledger/settings max width | 720 dp | Centered content pane |
| Page title | Material `headlineSmall` | Primary destination title |
| Section title | Material `titleMedium` | Major subsection |
| Row title/value | Material `bodyLarge` or `titleSmall` | Merchant and amount |
| Metadata | Material `bodySmall` | Date, category, counts |
| Total | Material `displaySmall` | Dashboard headline only |

These are starting tokens rather than user preferences. Runtime density settings
are outside the current feature scope.

## Component direction

- Page header: title plus optional one-line subtitle; omit redundant subtitle.
- Dashboard summary: one tonal surface containing period, range, total,
  comparison, and exceptional-count links.
- Category list: divided interactive rows instead of a button per category.
- Transaction list: one grouped surface or edge-to-edge rows with dividers.
- Status marker: short text plus optional icon; never color-only.
- Settings: titled sections and label/value rows; reserve a container for access
  problems and destructive data controls.
- Actions: one primary action per region, with secondary actions inline or
  outlined only when they need persistent emphasis.

## Accessibility constraints

- Compact spacing must not reduce touch targets below 48 dp.
- Rows expand vertically rather than clipping at large font scale.
- Amounts receive stable trailing space and may move below merchant content when
  the width or font scale cannot support two columns.
- Foreign and excluded labels remain visible and are included in the merged row
  semantics.
- Error, warning, and destructive treatments include text or icon cues in
  addition to color.
- Dashboard bars retain complete spoken date-and-amount descriptions.

## Approved direction

- Use **Balanced ledger** as the default density; maximum density remains a
  boundary reference rather than the default.
- Use neutral light/dark surfaces, retain green quietly for brand, navigation,
  primary actions, links, and switches, and use category colors for financial
  content.
- Carry the category color from transaction-row marker to transaction-detail
  summary tint and dashboard breakdown.
- Pair every category color with a name or other non-color cue.
- Represent reviewable transactions separately from category colors with an
  amber question marker, **Needs your input**, the compact supporting text
  **Details missing**, and a **Review** action.
- Remove redundant destination subtitles after onboarding unless a particular
  empty, permission, or error state needs explanatory copy.

## Completion checklist

- [x] Current spacing and hierarchy reconstructed from the implemented screens.
- [x] Representative dashboard and transaction-list concepts prepared.
- [x] Balanced and maximum-density boundaries defined.
- [x] Initial design-token sheet documented.
- [x] Accessibility constraints documented.
- [ ] Running-app screenshots captured on an emulator or device (deferred to
      UI-05 because no emulator or `adb` executable was available during UI-00).
- [x] Visual direction reviewed and selected.
- [x] Selected tokens and status treatment adjusted from review feedback.
