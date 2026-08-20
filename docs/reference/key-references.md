# Key Reference Documents

The architecture and reference documents in this folder should be read alongside the following. Paths are relative to this file unless noted otherwise.

## Requirements and Planning

Planning artifacts live under `_bmad-output/planning-artifacts/`, not under `docs/`.

  * `../../_bmad-output/planning-artifacts/prd.md` — Product Requirements Document (current cycle).
  * `../../_bmad-output/planning-artifacts/epics.md` — Epics and stories for Epics 1-7.
  * `../../_bmad-output/planning-artifacts/architecture.md` — Solutioning-phase architecture document.
  * `../../_bmad-output/planning-artifacts/project-brief.md` — Vision, target users, MVP scope, constraints.
  * `../../_bmad-output/planning-artifacts/archive/cycle-1-2025-12-15/` — Cycle 1 baseline (`prd.md`, `epic-1.md` … `epic-8.md`, readiness reports).

## Delivery State

  * `../../_bmad-output/implementation-artifacts/sprint-status.yaml` — the tracker of record for epic and story status, including epic kickoff gates.
  * `../../_bmad-output/implementation-artifacts/` — one file per story, plus kickoff checklists, retrospectives, and validation reports.

## Architecture and Design

  * `component-view.md` — component responsibilities and interactions.
  * `component-architecture-deep-dive.md` — detailed component breakdown.
  * `data-models.md` — internal and API data structures.
  * `project-structure.md` — directory and package layout.
  * `tech-stack.md` — technologies, frameworks, and library versions.
  * `sequence-diagrams.md` — placeholder; key workflows are currently described in the deep dive.

## API

  * `api-reference.md` — full MCP command specification: JSON structures, parameters, responses.
  * `../mcp-tools-reference.md` — per-tool quick reference. Note: it currently documents 15 tools and
    omits `resolve_track`, which is implemented and specified in `api-reference.md`. Treat
    `api-reference.md` as authoritative where the two disagree.

## Development Standards

  * `operational-guidelines.md` — **coding standards, error handling, security, and the testing strategy.** There is no separate `coding-standards.md` or `testing-strategy.md`; both live here.
  * `../engineering/git-workflow.md` — branch types, PR targeting, merge strategy.
  * `../engineering/story-status-authority.md` — which status wins, and how it is enforced.
  * `semantic-versioning-guide.md` — Nyx-driven versioning and Conventional Commits.
  * `environment-vars.md` — configuration parameters (compiled-in constants; no `.env` file).
  * `infra-deployment.md` — distribution and installation of the `.bwextension`.

## Testing and CI

  * `../engineering/mcp-smoke-test-runbook.md` — running the MCP smoke harness against a live Bitwig.
  * `../engineering/mcp-host-functional-test-matrix.md` — per-tool manual functional coverage.
  * `testing/mcp-endpoints-verification.md` — endpoint verification notes.
  * `../ci.md` — GitHub Actions workflows and local equivalents.
  * `../ci-secrets-checklist.md` — required CI secrets (none beyond `GITHUB_TOKEN`).

## External References

  * Bitwig Java Extension API, version 19 — [local scraped copy](bitwig-api/v19/index.md); upstream at <https://resources.bitwig.com/studios/controller-api/>.
  * Model Context Protocol specification — <https://modelcontextprotocol.io/>.
  * MCP Java SDK — <https://github.com/modelcontextprotocol/java-sdk>.

## Change Log

| Change | Date | Description | Author |
| ------ | ---- | ----------- | ------ |
| Update | 2026-08-19 | Repointed PRD, epics, and architecture links from the removed `docs/sprint-artifacts/` tree to `_bmad-output/`. Removed the "(To be created)" entries for `docs/coding-standards.md` and `docs/testing-strategy.md`, whose content is in `operational-guidelines.md`. Added delivery-state, testing, and CI sections. | Claude |
