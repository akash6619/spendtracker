# Documentation index

This directory is the durable project context for product, engineering, test,
and handoff work.

## First read

| Document | Purpose |
| --- | --- |
| [`../AGENTS.md`](../AGENTS.md) | Mandatory operating rules for coding agents |
| [`STATUS.md`](STATUS.md) | Current implementation, active work, and next action |
| [`MVP_PLAN.md`](MVP_PLAN.md) | Ordered, independently testable MVP slices |

## Product and engineering

| Document | Purpose |
| --- | --- |
| [`PRODUCT.md`](PRODUCT.md) | Goals, user journeys, scope, and product requirements |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Framework choice, module boundaries, and data flow |
| [`DATA_AND_PRIVACY.md`](DATA_AND_PRIVACY.md) | Storage model, raw-SMS policy, retention, and currency |
| [`TESTING.md`](TESTING.md) | Test pyramid, fixtures, commands, and release matrix |
| [`DEVELOPMENT.md`](DEVELOPMENT.md) | Local setup, emulator workflow, and coding conventions |
| [`DECISIONS.md`](DECISIONS.md) | Accepted architecture and product decisions |

## Maintenance rule

Code is the source of truth for current behavior; these documents are the source
of truth for intended behavior. Every feature handoff must reconcile the two by
updating `STATUS.md` and any affected plan, decision, or architecture section.

