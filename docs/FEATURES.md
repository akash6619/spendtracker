# Feature backlog

This document tracks product enhancements that are not yet executable slices in
`MVP_PLAN.md`. Use it to capture intent, refine scope, and decide what to work on
next. When a feature is ready for implementation, split it into a focused slice
with acceptance criteria and test gates in `MVP_PLAN.md`.

## Status legend

- `Idea`: captured but not yet scoped.
- `Shaping`: desired outcome and boundaries are being defined.
- `Ready`: scope, acceptance criteria, and dependencies are clear.
- `In progress`: claimed in the `STATUS.md` active-work table.
- `Done`: implemented, validated, and reflected in project documentation.
- `Parked`: intentionally deferred, with the reason recorded.

## Priority order

| Priority | Feature | Status | Desired outcome | Next action |
| --- | --- | --- | --- | --- |
| 1 | UI enhancements and polish | Shaping | Make the existing experience feel clear, consistent, accessible, and release-ready. | Audit every primary screen and turn findings into small, testable UI slices. |
| 2 | Parsing accuracy improvements | Shaping | Recognize more legitimate financial messages while reducing incorrect or ambiguous transactions. | Measure the current synthetic corpus, group misses by cause, and prioritize parser rules from evidence. |
| 3 | Historical dashboard ranges | Done | Browse individual days and earlier calendar weeks/months with matching ledger deep links. | Delivered as ENH-01 in `MVP_PLAN.md`. |

## UI enhancements and polish

### Outcome

Improve the presentation and usability of the current onboarding, dashboard,
transaction, detail, and settings flows without changing the app's privacy model
or core transaction semantics.

### Candidate scope

- Strengthen visual hierarchy, spacing, typography, color, and component consistency.
- Improve loading, empty, denied, error, and no-INR-spend states.
- Refine transaction rows, filters, detail editing, and dashboard readability.
- Check navigation feedback, touch targets, TalkBack labels, contrast, and large-text behavior.
- Review light and dark themes across supported screen sizes.

### Definition of ready

- [ ] Current screens have been audited with concrete findings or references.
- [ ] Work is split into focused slices with screenshots or written behavior where useful.
- [ ] Each slice identifies affected screens, states, and accessibility expectations.
- [ ] Visual changes do not obscure excluded, foreign-currency, or uncertain records.
- [ ] Required Compose, screenshot/manual, accessibility, and device checks are listed.

### Notes and decisions

- Preserve the existing Android-first, offline experience.
- Do not combine cosmetic work with transaction-policy or parser changes unless a
  dependency makes that unavoidable.

## Parsing accuracy improvements

### Outcome

Increase correct extraction and classification across supported financial SMS
formats while keeping behavior deterministic, explainable, private, and on-device.

### Candidate scope

- Expand synthetic coverage for banks, cards, UPI, fees, refunds, transfers, and ATM messages.
- Improve amount, currency, direction, kind, merchant, and account-hint extraction.
- Reduce false positives from OTP, failed, declined, cancelled, and authorization-only messages.
- Review categorization and merchant-normalization misses separately from parsing failures.
- Make review/rejection reasons useful without storing or logging message content.

### Definition of ready

- [ ] A synthetic baseline reports accepted, review, rejected, false-positive, and false-negative cases.
- [ ] Proposed rules are grouped by failure cause and ordered by expected impact.
- [ ] Every new rule has positive, nearby-negative, malformed-input, and regression fixtures.
- [ ] Reparse behavior and protection of user-edited records are explicitly covered.
- [ ] No fixture, diagnostic, log, or documentation contains real or reversible SMS data.

### Notes and decisions

- Keep parsing and categorization deterministic and in the shared module.
- Preserve original currency and existing spend-inclusion rules.
- Treat a wider merchant detector and any confidence-threshold change as explicit
  decisions because they affect review volume and user-visible results.

## Adding a feature

Add the feature to the priority table, then give it a section containing:

1. the user outcome;
2. candidate scope and explicit exclusions;
3. dependencies or open decisions;
4. a definition-of-ready checklist;
5. privacy, data, accessibility, and testing considerations.

Keep broad ideas here. Move implementation-ready work into `MVP_PLAN.md`, claim
it in `STATUS.md`, and update this backlog as the work progresses.
