# Sprint Change Proposal - Epic 2 Gate Cycle Fix for G1 and G2

Date: 2026-02-16  
Project: WigAI  
Trigger: G1/G2 are kickoff gates and must not be blocked by downstream feature stories  
Mode: Incremental

## 1. Issue Summary

Epic 2 kickoff had a dependency cycle risk: if G1/G2 are required before development, they cannot rely on later feature-story completion.

Context and evidence:
- G1 and G2 are kickoff gates in `_bmad-output/implementation-artifacts/epic-2-kickoff-checklist-2026-02-14.md`.
- Prior Epic 2 ordering allowed interpreting G1/G2 closure as dependent on stories that are also part of Epic 2 feature implementation.
- This creates potential blocking of story creation/start if gates are treated as strict preconditions.

Problem statement:
- Gate dependencies must be modeled as executable kickoff stories that occur before feature implementation stories.

## 2. Impact Analysis

### Epic Impact
- Epic 2 scope remains unchanged.
- Story sequencing changes to remove gate dependency cycle.

### Story Impact
- Keep `2.0` as G6 checkpoint story.
- Add/define kickoff gate stories:
  - `2.1` = G1 activation (Contract Semantics DoR + Runtime/Test/Docs lockstep)
  - `2.2` = G2 ambiguity behavior defined and tested
- Shift feature stories to start at `2.3`:
  - old `2.1` -> `2.3`
  - old `2.2` -> `2.4`
  - old `2.3` -> `2.5`

### Artifact Impact
- `_bmad-output/planning-artifacts/epics.md`: add/renumber Epic 2 stories and kickoff sequencing note.
- `_bmad-output/implementation-artifacts/sprint-status.yaml`: replace Epic 2 story keys with new sequence.
- `_bmad-output/implementation-artifacts/epic-2-kickoff-checklist-2026-02-14.md`: tie G1/G2 closure to kickoff story completion + evidence.

### Technical Impact
- No architecture or product-scope changes.
- Governance/sequencing correction only.

## 3. Recommended Approach

Selected path: **Direct Adjustment with sequencing correction**

Why this path:
- Removes dependency cycle immediately.
- Preserves Epic 2 functional scope.
- Makes kickoff gates auditable and executable.

Effort/Risk/Timeline:
- Effort: Low
- Risk: Low
- Timeline impact: minimal (artifact-only adjustment)

## 4. Detailed Change Proposals

### Proposal A: Epic 2 renumbering and kickoff sequencing

Artifact: `_bmad-output/planning-artifacts/epics.md`

- Add Epic 2 sequencing note: kickoff stories `2.0..2.2`; feature implementation starts at `2.3`.
- Add Story `2.1` for G1 gate activation.
- Add Story `2.2` for G2 ambiguity behavior/test closure.
- Renumber existing feature stories to `2.3`, `2.4`, `2.5`.

### Proposal B: Sprint-status story key synchronization

Artifact: `_bmad-output/implementation-artifacts/sprint-status.yaml`

- Replace Epic 2 keys with:
  - `2-0-dependency-version-refresh-checkpoint-g6-closure`
  - `2-1-contract-semantics-dor-and-runtime-test-docs-lockstep-gate-activation`
  - `2-2-duplicate-track-name-ambiguity-behavior-defined-and-tested`
  - `2-3-standard-track-targeting-contract-index-exact-name-selected-default`
  - `2-4-resolve-track-tool-deterministic-fuzzy-matching-candidate-list-ambiguity`
  - `2-5-apply-track-targeting-contract-to-existing-mutating-tools-documentation`

### Proposal C: Kickoff checklist gate contract hardening

Artifact: `_bmad-output/implementation-artifacts/epic-2-kickoff-checklist-2026-02-14.md`

- G1 note explicitly requires Story `2.1` done + contract/lockstep sections present with pass criteria.
- G2 note explicitly requires Story `2.2` done + ambiguity safety evidence links.
- Exit criteria includes explicit rule: feature implementation starts at Story `2.3` only after gates `G1..G6` are complete.

## 5. Implementation Handoff

### Scope Classification
- **Moderate**: backlog/story sequencing and kickoff governance updates

### Handoff Recipients
- Scrum Master / Product Owner
- Development lead
- QA

### Responsibilities
- SM/PO:
  - Maintain Epic 2 sequence and gate contracts in planning artifacts.
- Dev lead:
  - Execute kickoff stories `2.0`, `2.1`, `2.2` in order and attach evidence.
- QA:
  - Verify gate evidence quality before gate status transitions.

### Success Criteria
- No dependency cycle exists between kickoff gates and feature-story start.
- Epic 2 kickoff stories are explicit and trackable.
- G1/G2 closure criteria are objective and evidence-backed.
- Feature stories begin at `2.3` only after `G1..G6` are done.

## 6. Approval Record

- Approved by Josh on 2026-02-16.
- Decision: Approved for implementation.
