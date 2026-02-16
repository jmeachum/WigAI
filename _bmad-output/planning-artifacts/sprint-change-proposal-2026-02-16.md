# Sprint Change Proposal - Epic 2 G6 Dependency/Version Refresh Checkpoint Closure

Date: 2026-02-16  
Project: WigAI  
Trigger: Epic 2 kickoff gate G6 pending (`dependency/version refresh checkpoint`)  
Mode: Incremental

## 1. Issue Summary

Epic 2 kickoff is currently blocked by G6 in `_bmad-output/implementation-artifacts/epic-2-kickoff-checklist-2026-02-14.md`.

Context and evidence:
- Epic sequencing note requires a dedicated dependency-refresh and regression checkpoint after Story 1.7 and before Epic 2 implementation (`_bmad-output/planning-artifacts/epics.md`).
- Epic 1 retrospective lists dependency refresh/version-upgrade checkpoint as a critical-path gate before Epic 2 kickoff (`_bmad-output/implementation-artifacts/epic-1-retro-2026-02-13.md`).
- G6 remains `pending` in the kickoff checklist with broad wording and no executable backlog item.

Problem statement:
- G6 is a mandatory kickoff condition but is not represented as a dedicated, trackable story with explicit evidence outputs and status transition rules.

## 2. Impact Analysis

### Epic Impact
- Epic 2 remains valid and ready in scope, but cannot start until G6 is completed.
- No epic invalidation is required.

### Story Impact
- Add one dedicated pre-implementation checkpoint story for Epic 2.
- Existing Epic 2 implementation stories remain intact; sequencing changes so checkpoint executes first.

### Artifact Conflicts
- `_bmad-output/planning-artifacts/epics.md` requires a new checkpoint story entry under Epic 2.
- `_bmad-output/implementation-artifacts/sprint-status.yaml` requires a matching story key and backlog status.
- `_bmad-output/implementation-artifacts/epic-2-kickoff-checklist-2026-02-14.md` requires explicit evidence criteria for G6 closure.

### Technical Impact
- No architecture changes.
- No product-scope/PRD change.
- No code-level dependency upgrade is proposed here; this change adds planning and execution governance to complete G6 safely.

## 3. Recommended Approach

Selected path: **Direct Adjustment**

Why this path:
- Fastest path to unblock Epic 2 while preserving gate quality.
- Converts ambiguous gate text into executable backlog work.
- Keeps risk low by avoiding replan or rollback.

Effort/Risk/Timeline:
- Effort: Low
- Risk: Low
- Timeline impact: ~0.5-1 day before Epic 2 development starts

## 4. Detailed Change Proposals

### Proposal S1: Add dedicated checkpoint story to Epic 2

Artifact: `_bmad-output/planning-artifacts/epics.md`  
Section: Epic 2 story list and sequencing preface

OLD:
- Epic 2 starts with `Story 2.1: Standard Track Targeting Contract...`

NEW:
- Insert a dedicated checkpoint story before implementation stories:
  - Story ID recommendation: `2.0` (to avoid renumbering existing `2.1..2.3`)
  - Title: `Dependency/Version Refresh Checkpoint Closure for Epic 2 Kickoff (G6)`
  - Required outputs:
    1. Dependency/version matrix recorded (MCP SDK, Jetty, JUnit, transitive-risk notes)
    2. Full automated test evidence path recorded
    3. Host-required smoke evidence path recorded
    4. Rollback point reference (tag/branch/commit) recorded
    5. G6 checklist status updated to `done` with evidence links

Rationale:
- Makes G6 executable, auditable, and completion-driven.

### Proposal S2: Add explicit tracking key in sprint status

Artifact: `_bmad-output/implementation-artifacts/sprint-status.yaml`  
Section: `development_status` under `epic-2`

OLD:
- Epic 2 story keys begin at `2-1-standard-track-targeting-contract-index-exact-name-selected-default: backlog`

NEW:
- Add a leading checkpoint key:
  - `2-0-dependency-version-refresh-checkpoint-g6-closure: backlog`
- Keep existing `2-1..2-3` entries after this key.
- Keep `epic-2: backlog` until checkpoint story and all gates are done.

Rationale:
- Aligns gate control with sprint tracking and prevents accidental Epic 2 start.

### Proposal S3: Tighten G6 evidence contract in kickoff checklist

Artifact: `_bmad-output/implementation-artifacts/epic-2-kickoff-checklist-2026-02-14.md`  
Section: G6 row (`Evidence / Notes`)

OLD:
- `Confirm dependency updates and versions per Story 1.6/1.7 notes`

NEW:
- Replace with explicit completion contract tied to checkpoint story deliverables:
  - Dependency/version matrix path
  - Automated test run evidence path
  - Host smoke run evidence path
  - Rollback point reference
- Rule: G6 can be marked `done` only when all evidence links are present and checkpoint story status is `done`.

Rationale:
- Eliminates subjective closure and improves auditability.

## 5. Implementation Handoff

### Scope Classification
- **Moderate**: backlog reorganization + gate-governance updates

### Handoff Recipients
- Product Owner / Scrum Master (planning artifact updates and sequencing)
- Development lead (checkpoint execution evidence owner)
- QA (smoke evidence verification)

### Responsibilities
- PO/SM:
  - Apply artifact edits S1-S3.
  - Ensure sprint-status and kickoff checklist remain synchronized.
- Dev lead:
  - Produce dependency/version matrix and rollback reference.
  - Run and attach automated test evidence.
- QA:
  - Run and attach host-required smoke evidence.

### Success Criteria
- Dedicated Epic 2 checkpoint story exists and is actionable.
- `sprint-status.yaml` contains checkpoint story key and sequencing.
- G6 evidence criteria are explicit and linked to artifacts.
- G6 transitions to `done` only after all required evidence is present.
- Epic 2 implementation stories remain blocked until all kickoff gates are complete.

## 6. Approval Record

- Approved by Josh on 2026-02-16.
- Decision: Approved for implementation (`yes`).
- Scope classification at approval: Moderate.
