# Sprint Change Proposal - 2026-08-20

## 1. Issue Summary

### Trigger
- Epic: Epic 7 (Developer Environment Standardization)
- Discovery point: direct scope decision by the product owner while completing Story 7.1 implementation on `implementation/story-7-1`.

### Problem Statement
- Story 7.1 (per `sprint-change-proposal-2026-08-19.md`) still carries two criteria inherited from the withdrawn Story 7.2:
  - AC 7 requires a dedicated verification check that resolves specific known symbols (`WigAIExtension`, `BitwigApiFacade`, `McpServerManager`) after indexing.
  - AC 10 requires a baseline-vs-MCP-assisted token-usage benchmark, recorded as a repo artifact.
- The product owner determined this work is not valuable at this time: the devcontainer, install script, indexing, and live-sync watcher were verified end-to-end in-container this session (fresh run, idempotent re-run, and a real file-change pickup), which already demonstrates the tool works — a further scripted symbol-resolution check and a formal token-usage benchmark add cost without adding confidence proportional to that cost right now.

### Evidence
- `docs/engineering/devcontainer-mcp-setup.md` documents `codegraphcontext stats`/`list`/`report` as the supported ways to inspect what got indexed; no dedicated symbol-lookup script exists or is planned.
- No token-usage benchmark artifact exists under `_bmad-output/implementation-artifacts/tests/`, and none is queued.
- Every other criterion in the 2026-08-19 rescope (devcontainer build, Python 3.12 provisioning, pinned upstream install, backend selection, MCP config for VS Code/Claude Code/Codex, host connectivity, healthcheck, git-ignore hygiene, documentation) is implemented and verified.

### Approved Direction
- Mode: `Direct Adjustment`
- AC 10 (token-usage benchmark) is removed from Story 7.1 entirely.
- AC 7 is relaxed: indexing must complete over `src/` and be queryable (verified via `codegraphcontext list`/`stats`), but no dedicated symbol-resolution verification script is required.

## 2. Impact Analysis

### Epic Impact
- Epic 7 remains valid and in scope. No new epic required. Epic 7's goal statement is unaffected.

### Story Impact
- Story 7.1: acceptance criteria reduced from 11 to 10 (AC 10 removed, ACs renumbered); AC 7 (now AC 7) reworded to drop the scripted symbol-resolution requirement.
- With AC 7 and the old AC 10 no longer blocking, every remaining AC in Story 7.1 is implemented and verified. Status moves to `review`.

### Artifact Conflicts
- `_bmad-output/planning-artifacts/epics.md`: Epic 7 / Story 7.1 acceptance criteria list (drop the benchmark AC, relax the symbol-resolution AC and the doc-requirements bullet that names "token benchmark execution").
- `_bmad-output/implementation-artifacts/7-1-devcontainer-repo-local-mcp-tooling-codegraphcontext.md`: AC list, Tasks/Subtasks, Testing Requirements, Status header.
- `_bmad-output/implementation-artifacts/sprint-status.yaml`: `7-1-devcontainer-repo-local-mcp-tooling-codegraphcontext` moves `in-progress` -> `review`.

### Technical Impact
- No WigAI runtime or MCP tool-contract change. This is scope/documentation only; no code changes result from this proposal beyond the planning/tracking artifacts.

## 3. Recommended Approach

### Selected Path
- Direct Adjustment: remove AC 10 and relax AC 7 in place, matching what was actually decided.

### Why This Path
- The remaining, unmodified acceptance criteria fully capture the value delivered (deterministic devcontainer, upstream tool installed and configured for all three clients, live-synced code graph, documentation). The dropped criteria were carried forward from a withdrawn sibling story and were never re-justified against this story's actual goal.

### Effort, Risk, Timeline
- Effort: Low (planning artifacts only; implementation already covers the retained criteria)
- Risk: Low
- Timeline impact: none — this unblocks the story rather than adding work

## 4. Detailed Change Proposals

### 4.1 Epic Definition

#### Artifact: `_bmad-output/planning-artifacts/epics.md`

OLD:
- Story 7.1 AC: indexing verified by "a check that resolves known symbols ... and their relationships."
- Story 7.1 AC: token-usage benchmark recorded as a repo artifact.
- Documentation AC bullet includes "token benchmark execution."

NEW:
- Story 7.1 AC: indexing completes over `src/` and the result is queryable.
- Token-usage benchmark AC removed.
- Documentation AC bullet drops "token benchmark execution."

Rationale:
- The epic definition must not commit to verification work that was descoped.

### 4.2 Story Artifact

#### Artifact: Story 7.1

OLD:
- 11 acceptance criteria, including a scripted symbol-resolution check and a token-usage benchmark artifact requirement. Status `in-progress`.

NEW:
- 10 acceptance criteria. AC 7 relaxed to queryable-indexing verification. Status `review`.

Rationale:
- Matches what was actually implemented and verified, and what the product owner has confirmed is the right bar for this story.

### 4.3 Sprint Tracker

#### Artifact: `_bmad-output/implementation-artifacts/sprint-status.yaml`

OLD:
```
  7-1-devcontainer-repo-local-mcp-tooling-codegraphcontext: in-progress
```

NEW:
```
  7-1-devcontainer-repo-local-mcp-tooling-codegraphcontext: review
```

Rationale:
- Reflects the retained criteria being complete. `./scripts/check-story-status.sh` enforces that the tracker and story header agree.

## 5. Implementation Handoff

### Scope Classification
- `Minor` (planning artifacts only; no code change results from this proposal)

### Handoff Recipients and Responsibilities
- Product Owner: this proposal records a decision already made verbally in-session; no further approval action needed.
- Reviewer of the Story 7.1 PR: review against the 10 retained acceptance criteria, not the two dropped ones.

### Success Criteria
- Story 7.1 and `epics.md` agree on 10 acceptance criteria with no benchmark requirement.
- `./scripts/check-story-status.sh` passes.

## Approval Record

- Descope AC 10 (token-usage benchmark) and relax AC 7 (drop scripted symbol-resolution check): Approved 2026-08-20, product owner, in-session.

## Supersedes

- `sprint-change-proposal-2026-08-19.md` is superseded **only** in the two acceptance criteria named above. Everything else it approved (single-tool scope, story rename, upstream-only rule) remains in force.
