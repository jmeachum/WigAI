# Sprint Change Proposal - Dependency Upgrade Path (Post Story 2.0)

Date: 2026-02-16  
Project: WigAI  
Trigger: Story 2.0 checkpoint complete; define executable dependency modernization path including major versions
Mode: Incremental

## 1. Issue Summary

Story `2-0-dependency-version-refresh-checkpoint-g6-closure` completed gate G6 with evidence and documented deferred upgrade decisions. The sprint checkpoint confirmed that key major-version upgrade paths were not yet fully reviewed for execution (Jetty 12, Jakarta alignment, JUnit 6 adoption, MCP SDK 0.17.x migration, Bitwig API 24 compatibility).

Problem statement:
Define a phased, evidence-backed dependency upgrade path that includes major-version review and execution gates while avoiding instability in Epic 2 feature delivery.

## 2. Impact Analysis

### Epic Impact
- Epic 2 feature scope remains valid and should continue.
- Modernization work should run in a dedicated, gated track to prevent feature-sequencing disruption.

### Story Impact
- Introduce a dedicated modernization epic/workstream with explicit patch and major-migration phases.
- Preserve Epic 2 feature stories and avoid coupling them to high-risk platform migrations.

### Artifact Conflicts
- `epics.md` currently lacks a dedicated modernization epic that includes Bitwig API 24 evaluation/execution.
- `sprint-status.yaml` lacks executable modernization story keys.
- No single roadmap artifact currently defines go/no-go gates and fallback paths for major upgrades.

### Technical Impact
- Patch upgrades: low-to-medium risk.
- Major upgrades: medium-to-high risk due to transport/runtime/API compatibility and servlet-line alignment.
- Requires explicit regression + host smoke evidence at each phase.

## 3. Recommended Approach

Selected path: **Hybrid Direct Adjustment**

1. Patch refresh first (low-risk debt reduction).
2. Major migration spike with compatibility matrix (Jetty 12, Jakarta alignment, MCP SDK 0.17.x, Bitwig API 24, JUnit 6 viability).
3. Controlled implementation behind explicit release gates.

Effort/Risk/Timeline:
- Effort: Medium
- Risk: Medium (overall), High for some major upgrades if rushed
- Timeline impact: Minimal to Epic 2 if modernization is parallel and gated

## 4. Detailed Change Proposals

### Proposal A - Add modernization epic/workstream (includes Bitwig API 24)

Artifact: `_bmad-output/planning-artifacts/epics.md`  
Section: append new Epic (recommended Epic 6)

OLD:
- No explicit modernization epic for dependency/platform upgrades.

NEW:
- Add `Epic 6: Platform & Dependency Modernization` with phased stories:
  - `6.1` Patch refresh: Jetty `11.0.20 -> 11.0.26`, JUnit `5.10 -> latest 5.x`, full test + host smoke.
  - `6.2` Major migration spike: Jetty 12, Jakarta/Servlet alignment, MCP SDK `0.17.x`, Bitwig API 24 compatibility assessment.
  - `6.3` Execute approved major upgrades with compatibility fixes.
  - `6.4` Docs/contracts/release-guardrail refresh with rollback evidence.

Rationale:
- Includes Bitwig API 24 in a governed migration track and removes ad-hoc upgrade risk.

### Proposal B - Add modernization story keys in sprint tracking

Artifact: `_bmad-output/implementation-artifacts/sprint-status.yaml`  
Section: `development_status`

OLD:
- No modernization story keys.

NEW:
- Add:
  - `epic-6: backlog`
  - `6-1-patch-refresh-jetty-11-0-26-junit-5-x-validation: backlog`
  - `6-2-major-migration-spike-jetty-12-jakarta-mcp-sdk-0-17-bitwig-api-24: backlog`
  - `6-3-major-upgrade-implementation-and-compatibility-fixes: backlog`
  - `6-4-docs-contracts-release-guardrails-refresh: backlog`
  - `epic-6-retrospective: optional`

Rationale:
- Makes modernization work executable, sequenced, and auditable.

### Proposal C - Create explicit upgrade roadmap artifact

Artifact: `_bmad-output/implementation-artifacts/dependency-upgrade-roadmap-2026-02-16.md` (new)

OLD:
- Upgrade findings are distributed across checkpoint notes, without a single execution plan.

NEW:
- Create a roadmap with:
  - target version matrix,
  - per-target compatibility gates,
  - rollout order and blast-radius controls,
  - blocker/fallback criteria,
  - evidence mapping to logs/tests/smoke runs.

Rationale:
- Converts deferred-upgrade findings into a practical execution plan.

## 5. Implementation Handoff

### Scope Classification
- **Moderate**: backlog expansion + modernization governance + execution planning

### Handoff Recipients
- Scrum Master / Product Owner: backlog sequencing and artifact governance
- Development Lead: migration spike and implementation planning
- QA Lead: compatibility/regression gate ownership

### Responsibilities
- SM/PO:
  - Add Epic 6 and related stories to planning and sprint tracking.
- Dev Lead:
  - Drive migration spike (Story 6.2) and propose final implementation order.
- QA:
  - Define and verify regression/host-smoke promotion gates per phase.

### Success Criteria
- Modernization epic exists with executable, phased stories.
- Sprint tracking includes all modernization story keys.
- Roadmap artifact defines objective go/no-go and fallback gates.
- Epic 2 feature delivery remains unblocked by major-version uncertainty.

## 6. Approval Record

- Approved by Josh on 2026-02-16.
- Decision: Approved for implementation (`yes`).
- Scope classification at approval: Moderate.
