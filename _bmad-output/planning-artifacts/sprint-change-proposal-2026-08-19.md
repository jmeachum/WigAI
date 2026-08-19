# Sprint Change Proposal - 2026-08-19

## 1. Issue Summary

### Trigger
- Epic: Epic 7 (Developer Environment Standardization)
- Discovery point: direct scope decision by the product owner while Story 7.1 was `in-progress` and Story 7.2 was `backlog`.

### Problem Statement
- Epic 7 was scoped around two upstream context tools, `serena` and `claude-context`, with `codegraphcontext` added on 2026-08-19 as a third (Story 7.2).
- The required direction is different: `codegraphcontext` **replaces** both tools. `serena` and `claude-context` leave Epic 7 entirely.

### Evidence
- Story 7.1 acceptance criteria name `serena` and `claude-context` as the tools to install and wire.
- `.vscode/mcp.json` still carries stdio entries for both, pointing at `scripts/mcp/serena_server.py` and `scripts/mcp/claude_context_server.py` — files that were reverted and no longer exist.
- No implementation of Story 7.1 exists in the tree, so nothing built is lost by the change.

### Approved Direction
- Mode: `Direct Adjustment`
- `serena` and `claude-context` are removed from Epic 7 scope.
- `codegraphcontext` (https://github.com/CodeGraphContext/CodeGraphContext) is the single context tool.
- Epic 7 returns to a **single story**: Story 7.2 is withdrawn and Story 7.1 is rescoped to cover the devcontainer and the `codegraphcontext` install end to end.

## 2. Impact Analysis

### Epic Impact
- Epic 7 remains valid and in scope. No new epic required.
- Epic 7 goal statement must name one tool instead of three.

### Story Impact
- Story 7.1: rescoped and renamed. Stays `in-progress`.
  - Old key: `7-1-devcontainer-repo-local-mcp-tooling-serena-claude-context`
  - New key: `7-1-devcontainer-repo-local-mcp-tooling-codegraphcontext`
- Story 7.2: withdrawn. Its acceptance criteria are absorbed into the rescoped Story 7.1 rather than discarded.

### Artifact Conflicts
- `_bmad-output/planning-artifacts/epics.md`: Epic 7 summary lines and the Story 7.1/7.2 sections.
- `_bmad-output/implementation-artifacts/sprint-status.yaml`: story key renamed, `7-2-*` entry removed.
- `.vscode/mcp.json`: `serena` and `claude-context` entries removed.

### Technical Impact
- No WigAI runtime or MCP tool-contract change. This is developer-environment scope only.
- Carried forward from the withdrawn Story 7.2: the container has no Python package installer, and the graph backend choice is constrained by the container's Python version.
- Preserved from the original Story 7.1: container-to-host Bitwig reachability via `host.docker.internal`, the token-usage benchmark, secrets hygiene, and in-repo documentation.

## 3. Recommended Approach

### Selected Path
- Direct Adjustment: rewrite the Story 7.1 contract in place and withdraw Story 7.2.

### Why This Path
- Nothing was implemented against either story, so there is no rollback surface.
- Keeps Epic 7 at one story, matching its now-single-tool scope.
- The non-tool-specific criteria (container build, host networking, benchmark, hygiene, docs) survive intact instead of being rewritten from scratch.

### Effort, Risk, Timeline
- Effort: Low (planning artifacts only)
- Risk: Low
- Timeline impact: none; Story 7.1 has not started implementation

## 4. Detailed Change Proposals

### 4.1 Epic Definition

#### Artifact: `_bmad-output/planning-artifacts/epics.md`

OLD:
- Epic 7 goal names `serena`, `claude-context`, and `codegraphcontext`.
- Two story sections: 7.1 (two tools) and 7.2 (`codegraphcontext`).

NEW:
- Epic 7 goal names `codegraphcontext` only.
- One story section: Story 7.1, covering the devcontainer and the `codegraphcontext` install.

Rationale:
- The epic definition must not reference tools that are out of scope.

### 4.2 Story Artifact

#### Artifact: Story 7.1

OLD:
- `7-1-devcontainer-repo-local-mcp-tooling-serena-claude-context.md`, 8 acceptance criteria built around two tools.

NEW:
- `7-1-devcontainer-repo-local-mcp-tooling-codegraphcontext.md`, 11 acceptance criteria: the retained environment criteria plus the `codegraphcontext` criteria absorbed from Story 7.2 (Python installer provisioning, pinned upstream install, backend selection, indexing with symbol-resolution verification).
- Status unchanged: `in-progress`.

Rationale:
- One tool, one story. The slug must not name tools that were removed.

### 4.3 Sprint Tracker

#### Artifact: `_bmad-output/implementation-artifacts/sprint-status.yaml`

OLD:
```
  7-1-devcontainer-repo-local-mcp-tooling-serena-claude-context: in-progress
  7-2-devcontainer-install-codegraphcontext-graph-backed-code-context: backlog
```

NEW:
```
  7-1-devcontainer-repo-local-mcp-tooling-codegraphcontext: in-progress
```

Rationale:
- Reflects the renamed story and the withdrawn one. `./scripts/check-story-status.sh` enforces that the tracker and story header agree.

### 4.4 Configuration

#### Artifact: `.vscode/mcp.json`

OLD:
- `serena` and `claude-context` stdio entries invoking reverted wrapper scripts that do not exist.

NEW:
- Those entries removed. The `WigAI` HTTP entry is retained. The `codegraphcontext` stdio entry is added during Story 7.1 implementation, pointing at the upstream-installed entrypoint.

Rationale:
- The file currently references files that were deleted; leaving them is a broken configuration regardless of scope.

## 5. Implementation Handoff

### Scope Classification
- `Minor` (planning artifacts and one config file; no implementation exists to unwind)

### Handoff Recipients and Responsibilities
- Product Owner / Scrum Master: approve the rescoped Story 7.1 contract and confirm the tracker.
- Development team: implement the rescoped Story 7.1.

### Success Criteria
- Epic 7 and Story 7.1 name `codegraphcontext` and no other context tool.
- Story 7.2 no longer appears in the tracker or the epic definition.
- `./scripts/check-story-status.sh` passes.
- `.vscode/mcp.json` contains no entries pointing at nonexistent scripts.

## Approval Record

- Scope replacement (remove `serena` + `claude-context`, adopt `codegraphcontext`): Approved 2026-08-19
- Single-story consolidation (withdraw Story 7.2, rescope Story 7.1): Approved 2026-08-19

## Supersedes

- `sprint-change-proposal-2026-02-22.md` is superseded **only** in its choice of tools. Its canonical-tool rule — install upstream projects, never maintain repo-local replacement or wrapper servers — remains in force and applies to `codegraphcontext`.
