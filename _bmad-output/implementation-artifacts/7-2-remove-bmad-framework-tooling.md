# Story 7.2: Remove BMAD Framework Tooling

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a WigAI contributor,
I want the BMAD-METHOD runtime tooling removed from the repository,
so that the repo is no longer cluttered with framework scaffolding for a planning method whose outputs (stories, epics, sprint tracking) already live independently in `_bmad-output/`.

**Scope decision:** full removal of the `_bmad/` runtime and every integration surface wired to it — not just the `bmm` module — confirmed with the product owner. `_bmad-output/` (planning and implementation artifacts, including this file and `sprint-status.yaml`) is explicitly out of scope and stays.

## Acceptance Criteria

1. **Given** the `_bmad/` directory (the `bmm`, `core`, `tea`, and `_config`/`_memory` modules — agents, workflows, manifests)
   **When** the cleanup is complete
   **Then** `_bmad/` no longer exists in the repository.

2. **Given** `.claude/commands/bmad-*.md`, `.codex/prompts/bmad-*.md`, and `.github/agents/bmd-custom-*.agent.md` all load their instructions directly from paths under `_bmad/`
   **When** `_bmad/` is removed
   **Then** all three sets of files are removed as well, so no slash command or agent definition points at a nonexistent path.

3. **Given** `README.md`, `CONTRIBUTING.md`, and `docs/reference/project-structure.md` describe `_bmad/` as an existing, live part of the repo layout
   **When** documentation is reviewed after the removal
   **Then** those documents are updated to reflect that the BMAD runtime tooling was removed while `_bmad-output/` (the artifacts it produced) remains as project history and the tracker of record.

4. **Given** `docs/engineering/devcontainer-mcp-setup.md` uses `_bmad/` as an example of a directory excluded from code-graph indexing
   **When** `_bmad/` no longer exists
   **Then** the stale example reference is removed so the doc doesn't cite a deleted path.

5. **Given** the Java build and `.github/workflows/*.yml` CI jobs do not reference `_bmad/` in source or build configuration
   **When** the removal is complete
   **Then** `./gradlew compileJava compileTestJava` (and the existing CI jobs) continue to pass unaffected — this is a pure documentation/tooling removal with no runtime or build impact.

6. **Given** `.github/chatmodes/*.chatmode.md` (GitHub Copilot chat modes) embed their full agent definitions inline rather than loading from `_bmad/`
   **When** `_bmad/` is removed
   **Then** these files are left in place — they don't depend on the removed path and are out of scope for this story.

## Tasks / Subtasks

- [x] Remove the BMAD runtime (AC 1)
  - [x] `git rm -r _bmad/` (`_config`, `_memory`, `bmm`, `core`, `tea` — 443 tracked files).

- [x] Remove dependent integration surfaces (AC 2)
  - [x] `git rm .claude/commands/bmad-*.md` (51 files — the entire directory's contents).
  - [x] `git rm -r .codex/` (51 files under `prompts/` — the entire directory's contents).
  - [x] `git rm -r .github/agents/` (10 `bmd-custom-*.agent.md` files — the entire directory's contents).

- [x] Update documentation that described `_bmad/` as live (AC 3)
  - [x] `README.md` — rewrote the "Method and layout" section; drops the `_bmad/` and `.claude/commands/` bullets, notes the tooling was removed in this story.
  - [x] `CONTRIBUTING.md` — rewrote the "Development Method" paragraph.
  - [x] `docs/reference/project-structure.md` — removed the `_bmad/` tree line, updated the `_bmad-output/` comment and the closing Notes bullet, updated the `.github/` tree comment (no longer has agent definitions).

- [x] Fix the stale indexing-example reference (AC 4)
  - [x] `docs/engineering/devcontainer-mcp-setup.md` — dropped `_bmad/` from the `IGNORE_DIRS` example.

- [x] Verify no build/runtime impact (AC 5)
  - [x] Confirmed no `src/` file references `_bmad`.
  - [x] Confirmed no `.github/workflows/*.yml` references `_bmad` or `bmad`.
  - [x] `./gradlew compileJava compileTestJava` passes clean after the removal.

- [x] Explicit scope exclusion recorded (AC 6)
  - [x] Confirmed `.github/chatmodes/*.chatmode.md` are self-contained (no `_bmad/` load reference) and left untouched.

## Dev Notes

### Developer Context Section

`_bmad/` was the installed BMAD-METHOD v2 framework (agent personas, workflow definitions, manifests for the `bmm`, `core`, and `tea` modules) used during planning to drive `_bmad-output/`'s stories and epics via `bmad-*` slash commands. It is tooling that authored the artifacts, not an artifact itself — `_bmad-output/` (this file included) is unaffected by its removal.

Three separate integration surfaces pointed at `_bmad/` for three different AI-agent hosts, and all three were exactly co-extensive with "bmad content" in their directory (no unrelated files mixed in), so each was removable as a whole directory/glob:

- `.claude/commands/bmad-*.md` (Claude Code slash commands) — 51 files, 100% of `.claude/commands/`.
- `.codex/prompts/bmad-*.md` (Codex CLI prompts) — 51 files, 100% of `.codex/` (only subdirectory was `prompts/`).
- `.github/agents/bmd-custom-*.agent.md` (GitHub Copilot custom agents) — 10 files, 100% of `.github/agents/`.

`.github/chatmodes/*.chatmode.md` is a fourth, older BMAD integration for GitHub Copilot, but unlike the other three it embeds its complete agent definition inline (`ACTIVATION-NOTICE: ... DO NOT load any external agent files`) rather than pointing at `_bmad/`. It was confirmed via grep to have zero references to `_bmad/`, so removing `_bmad/` does not orphan it. Scoped out per AC 6 rather than folded in, since removing it isn't required for repo cleanliness and wasn't part of the approved scope.

### Scope Decision

Two removal scopes were considered: (a) only `_bmad/bmm` plus the 32 commands that load from it, leaving `_bmad/core`, `_bmad/tea`, `_bmad/_config`, and their ~19 commands intact; or (b) the entire `_bmad/` framework plus all 51 dependent commands/prompts/agent files. Given the size of the BMAD tooling footprint relative to the actual product code, and that `_bmad-output/` (the part that matters for ongoing story tracking) is untouched either way, the product owner selected the full-removal scope (b).

### Architecture Compliance

This story touches no extension source code — verified zero references to `_bmad` under `src/`. Changes are confined to deletions under `_bmad/`, `.claude/commands/`, `.codex/`, `.github/agents/`, and documentation edits in `README.md`, `CONTRIBUTING.md`, `docs/reference/project-structure.md`, and `docs/engineering/devcontainer-mcp-setup.md`.

### Known Pre-Existing Drift (Not Fixed Here)

`CONTRIBUTING.md` and `.github/workflows/README.md` both list `.bmad/**` (note: no underscore) as a CI path-filter that skips automated testing. Grepping `.github/workflows/*.yml` found no such filter — the actual directory was always `_bmad/`, not `.bmad/`, and no workflow currently branches on either path. This inaccuracy predates this story and isn't a consequence of the removal, so it was left as-is rather than folded into this story's scope.

### Testing Requirements

No JUnit coverage applies — this is a file-removal and documentation change. Verification is `./gradlew compileJava compileTestJava` passing and the repo-wide grep sweep for dangling `_bmad`/`bmad-*` references outside `_bmad-output/` and `.github/chatmodes/` coming back clean.

### References

- Epic 7 definition: `_bmad-output/planning-artifacts/epics.md`
- Status authority rules: `docs/engineering/story-status-authority.md`
- Branch/workflow convention: `docs/engineering/git-workflow.md`

## Dev Agent Record

### Agent Model Used

- Claude Sonnet 5

### Completion Notes List

- 2026-08-20: Story created and implemented in the same session, at the user's direct request (branch, story record, and removal requested together). Scope (full `_bmad/` removal vs. `bmm`-only) confirmed with the user before any deletion.
- 2026-08-20: Discovered during implementation that the removal surface was wider than the `_bmad/` directory itself — `.codex/prompts/` and `.github/agents/` are Codex- and Copilot-specific mirrors of the same `bmad-*` command set and were included for consistency (AC 2), since leaving them would orphan pointers to a deleted path.
- 2026-08-20: `.github/chatmodes/*.chatmode.md` was investigated and deliberately excluded (AC 6) — it's a self-contained, older BMAD integration that doesn't reference `_bmad/`.

### Change Log

- 2026-08-20: Story created and implemented — full `_bmad/` framework removal, dependent command/prompt/agent files removed, documentation updated, build verified green.
