# Sprint Change Proposal - 2026-02-22

## 1. Issue Summary

### Trigger
- Story: `7-1-devcontainer-repo-local-mcp-tooling-serena-claude-context`
- Discovery point: implementation completed and marked `review`, then scope clarification identified.

### Problem Statement
- The implemented Story 7.1 path uses repo-local custom MCP server wrappers (`scripts/mcp/serena_server.py`, `scripts/mcp/claude_context_server.py`) as the canonical solution.
- Required direction is different: use upstream MCP projects as canonical tools, and provide repo-local installation scripts for in-container setup.

### Evidence
- Current MCP server wiring points to local wrappers in `.vscode/mcp.json`.
- A previous implementation attempt described local wrappers as canonical:
  - `README.md`
  - `docs/engineering/devcontainer-mcp-setup.md`
- Story and planning artifacts do not explicitly enforce upstream installation-source requirements.
- Post-revert note: some previously referenced implementation files were deleted before commit; this proposal remains the source of truth for the correction scope.

### Approved Direction
- Mode: `Incremental`
- Chosen target: `B` (replace local custom servers as canonical path)
- Upstream sources:
  - `serena`: https://github.com/oraios/serena
  - `claude-context`: https://github.com/zilliztech/claude-context

## 2. Impact Analysis

### Epic Impact
- Epic 7 remains valid and in scope.
- No new epic required.
- Story 7.1 must be corrected and reopened from `review` to `in-progress`.

### Story Impact
- Affected: Story 7.1 only.
- Impact type: scope correction and implementation-path replacement.

### Artifact Conflicts
- `_bmad-output/planning-artifacts/epics.md`: Story 7.1 acceptance criteria need explicit upstream-source language.
- `_bmad-output/implementation-artifacts/7-1-devcontainer-repo-local-mcp-tooling-serena-claude-context.md`: status/dev notes/tasks currently validate local wrapper approach; requires correction.
- `_bmad-output/implementation-artifacts/sprint-status.yaml`: Story 7.1 status should return to `in-progress`.
- `.vscode/mcp.json`: should invoke upstream-installed entrypoints, not local replacement servers.
- `README.md`, `docs/engineering/devcontainer-mcp-setup.md`: must document upstream installation workflow.

### Technical Impact
- Replace canonical implementation model without changing product runtime behavior.
- Remove/rework local wrapper path and tests built around it.
- Preserve container-to-host Bitwig endpoint strategy (`host.docker.internal`).

## 3. Recommended Approach

### Selected Path
- Hybrid:
  - Option 1 (Direct Adjustment): update Story 7.1 contract + implementation path.
  - Targeted Option 2 (Rollback): roll back canonical reliance on local wrapper servers.

### Why This Path
- Minimal disruption to Epic structure.
- Corrects scope mismatch directly where it occurred.
- Keeps progress already made on devcontainer/networking/token-benchmark foundations.

### Effort, Risk, Timeline
- Effort: Medium
- Risk: Low-Medium
- Timeline impact: +1 focused story iteration before final `review`

## 4. Detailed Change Proposals

### 4.1 Planning Artifact Changes

#### Artifact: `_bmad-output/planning-artifacts/epics.md` (Story 7.1 section)

OLD:
- Story language supports in-container MCP setup but does not enforce upstream install-source constraints.

NEW:
- Explicitly require upstream canonical tools:
  - `serena` from https://github.com/oraios/serena
  - `claude-context` from https://github.com/zilliztech/claude-context
- Require repo-local installation scripts to install/configure upstream tools in devcontainer.
- Require `.vscode/mcp.json` commands to point to upstream-installed entrypoints.

Rationale:
- Prevents future drift into maintaining custom replacement servers.

### 4.2 Story Artifact Changes

#### Artifact: `_bmad-output/implementation-artifacts/7-1-devcontainer-repo-local-mcp-tooling-serena-claude-context.md`

OLD:
- Status: `review`
- Completion notes/file list validate local custom server implementations as delivery.

NEW:
- Status: `in-progress`
- Update acceptance criteria/tasks/dev notes to:
  - require install scripts for upstream tools,
  - require MCP config to use upstream-installed entrypoints,
  - classify local wrapper server implementation as superseded.
- Add explicit course-correction change-log entry with rationale.

Rationale:
- Story cannot remain in `review` while core implementation approach is incorrect.

### 4.3 Sprint Tracker Changes

#### Artifact: `_bmad-output/implementation-artifacts/sprint-status.yaml`

OLD:
- `7-1-devcontainer-repo-local-mcp-tooling-serena-claude-context: review`

NEW:
- `7-1-devcontainer-repo-local-mcp-tooling-serena-claude-context: in-progress`

Rationale:
- Accurately reflects reopened execution state.

### 4.4 Implementation/Documentation Changes (Execution Scope)

Planned updates:
- Add install scripts (example):
  - `scripts/mcp/install-serena.sh`
  - `scripts/mcp/install-claude-context.sh`
- Rewire `.vscode/mcp.json` to upstream-installed command entrypoints.
- Update validation scripts to verify upstream tool startup/handshake.
- Update docs (`README.md`, `docs/engineering/devcontainer-mcp-setup.md`) to use upstream installation flow.
- Remove or deprecate local wrapper scripts from canonical path.
- Post-revert state reminder: these files are planned deliverables and may not exist yet in the current branch state.

## 5. Implementation Handoff

### Scope Classification
- `Moderate`

### Handoff Recipients and Responsibilities
- Product Owner / Scrum Master:
  - approve and synchronize corrected story contract and tracker status.
- Development team:
  - execute implementation correction and update tests/docs/config.
- Reviewer (code-review workflow):
  - verify canonical path is upstream-install based and local wrapper approach is no longer canonical.

### Success Criteria
- Story 7.1 acceptance criteria explicitly reference upstream sources and install-script approach.
- Story status and sprint tracking are synchronized (`in-progress` during correction).
- MCP config and docs point to upstream-installed tools.
- Healthchecks and benchmark workflow pass with corrected setup.

## Approval Record

- Incremental Proposal 1 (Story contract correction): Approved
- Incremental Proposal 2 (Planning artifact update): Approved
- Incremental Proposal 3 (Story/tracker reopening): Approved
- Final Sprint Change Proposal approval: `yes` (2026-02-22)

## Handoff Log

- Scope classification: `Moderate`
- Routed to: Product Owner / Scrum Master coordination + Development team execution
- Handoff deliverables:
  - Sprint Change Proposal document (this file)
  - Story 7.1 reopened to `in-progress`
  - Sprint tracker synchronized to `in-progress` for Story 7.1
