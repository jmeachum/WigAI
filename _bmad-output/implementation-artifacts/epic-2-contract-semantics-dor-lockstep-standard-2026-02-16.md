# Epic 2 Contract Semantics DoR + Runtime/Test/Docs Lockstep Standard

Date: 2026-02-16  
Owner: Bob (SM)  
Scope: Epic 2 stories (`2.2+`) and review gates  

## Purpose

Define a reusable, artifact-level contract that ensures Epic 2 story readiness and review quality without requiring Story 2.1 to modify runtime or test source code.

## Contract Semantics DoR Template (Required Before `ready-for-dev`)

Copy this section into each Epic 2 story context and fill every field.

### Contract Semantics DoR

- Contract scope:
  - Define exactly what behavior is in scope and what is explicitly out of scope.
- Input contract:
  - List required request fields, optional fields, defaults, and mutual-exclusion rules.
- Output contract:
  - Define success payload shape and required fields.
- Error contract:
  - List expected error codes and trigger conditions.
  - Use canonical semantics from `_bmad-output/project-context.md`:
    - `INVALID_PARAMETER_INDEX` for index bounds.
    - `INVALID_RANGE` for numeric value-range.
- Canonical sources:
  - `_bmad-output/project-context.md`
  - `docs/reference/api-reference.md`
  - active story acceptance criteria
- Pass criteria:
  - DoR section complete and unambiguous.
  - Error semantics explicitly mapped to scenarios.
  - Reviewer can evaluate pass/fail without interpretation.

## Runtime/Test/Docs Lockstep Template (Required Before Story Closure)

Copy this section into each Epic 2 story context and fill every evidence item.

### Runtime/Test/Docs Lockstep

- Runtime evidence:
  - What runtime behavior changed (or explicitly did not change) and where.
- Test evidence:
  - Which tests validate the contract and where results are captured.
- Docs evidence:
  - Which docs were updated to match runtime/test behavior.
- Parity check:
  - Confirm runtime behavior, tests, and docs represent the same contract semantics.
- Failure criteria (block closure):
  - Missing runtime, test, or docs evidence.
  - Contradictory behavior between runtime, tests, and docs.
  - Missing or incorrect error-code semantics.

## Reviewer Checklist (Gate-Ready)

- [ ] Contract Semantics DoR section present and complete.
- [ ] Runtime/Test/Docs lockstep section present and complete.
- [ ] Error code semantics align with `_bmad-output/project-context.md`.
- [ ] Evidence links are present and resolvable.
- [ ] No ambiguous completion wording remains.

## Story 2.1 Implementation Evidence

- Story using this standard:
  - `_bmad-output/implementation-artifacts/2-1-contract-semantics-dor-and-runtime-test-docs-lockstep-gate-activation.md`
- Kickoff gate to update using this evidence:
  - `_bmad-output/implementation-artifacts/epic-2-kickoff-checklist-2026-02-14.md` (`G1`)
