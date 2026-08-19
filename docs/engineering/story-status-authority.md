# Story Status Authority

A story's status lives in two places, and they drifted three separate times before this rule existed.
This document defines which one wins and how the two are kept in agreement.

## The rule

`_bmad-output/implementation-artifacts/sprint-status.yaml` is the **authority for what the status is**.

The story file's `Status:` header is a **mirror** of the tracker. It must always match.

The story file's **Change Log** is the authority for **why** the status changed. Every transition gets
a dated line there. The tracker records state; the change log records reasons.

Nothing else is a status. Prose in Dev Notes, Completion Notes, or a commit message describing a
transition does not make the transition real — if the tracker and header do not both say it, it did
not happen.

## Allowed values

| Scope | Values |
| ----- | ------ |
| Story | `backlog`, `ready-for-dev`, `in-progress`, `review`, `done` |
| Epic | `backlog`, `in-progress`, `done` |
| Retrospective | `optional`, `done` |

An epic whose stories are all `done` must itself be `done`.

## Changing a status

Three edits, always together, in the same commit:

1. `sprint-status.yaml` — the new value.
2. The story file's `Status:` header — the same value.
3. The story file's Change Log — a dated line saying what changed and why.

Then run:

```bash
./scripts/check-story-status.sh
```

## Enforcement

`scripts/check-story-status.sh` verifies that:

- every story file's header matches its tracker entry;
- every story file has a tracker entry, and every status value is valid;
- no epic is left open when all of its stories are `done`.

It runs in three places:

- **Locally**, as step 1 of `./scripts/ci-local.sh` — the cheapest check, so it runs first.
- **On every pull request**, as the `story-status` job in `.github/workflows/pr-validation.yml`.
  Unlike the test job, it is not skipped for docs-only changes, because status edits *are* docs-only
  changes.
- **On demand**, by running the script directly. It works from any directory.

## Why this exists

Epic 2's retrospective raised "enforce single status authority and change-log discipline" as
Epic 3 Story `3-01`, a gate on starting Epic 3. The very next story, 7.1, then drifted three ways at
once: the header said `ready-for-dev`, the change log said `in-progress`, and the tracker said
`backlog`. Story 2.0 had drifted too — completed and evidenced on 2026-02-16, tracker updated, header
left at `review` for six months.

A convention that depends on remembering to update two files does not survive contact with a
course correction. This one is checked.

## History

| Date | Change |
| ---- | ------ |
| 2026-08-19 | Rule defined, checker added, and the three outstanding drifts reconciled (Story 2.0 -> `done`, Story 7.1 -> `in-progress`, Epic 2 -> `done`). |
