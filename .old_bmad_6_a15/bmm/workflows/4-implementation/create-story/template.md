# Story {{epic_num}}.{{story_num}}: {{story_title}}

Status: drafted

## Story

As a {{role}},
I want {{action}},
so that {{benefit}}.

## Acceptance Criteria

1. [Add acceptance criteria from epics/PRD]

## Tasks / Subtasks

- [ ] Task 1 (AC: #)
  - [ ] Subtask 1.1
- [ ] Task 2 (AC: #)
  - [ ] Subtask 2.1

## Dev Notes

- Relevant architecture patterns and constraints
- Source tree components to touch
- Testing standards summary

### Error Scenarios

<!-- Define error conditions and expected error codes per docs/project-context.md -->
<!-- This section prevents review churn by specifying error behavior upfront -->

| Condition | Error Code | Message Pattern |
|-----------|------------|-----------------|
| [Required param not provided] | MISSING_REQUIRED_PARAMETER | "[param_name] is required" |
| [Param provided but empty] | EMPTY_PARAMETER | "[param_name] cannot be empty" |
| [Invalid type/structure] | INVALID_PARAMETER | "[param_name] must be [type]" |
| [Index outside bounds] | INVALID_PARAMETER_INDEX | "[param_name] must be 0-N" |
| [Value outside range] | INVALID_RANGE | "[param_name] must be between X-Y" |
| [Bitwig API failure] | BITWIG_API_ERROR | "[operation] failed: [reason]" |

<!-- Remove unused rows. Add tool-specific scenarios as needed. -->
<!-- Reference: docs/project-context.md - Error Code Semantics -->

### Project Structure Notes

- Alignment with unified project structure (paths, modules, naming)
- Detected conflicts or variances (with rationale)

### References

- Cite all technical details with source paths and sections, e.g. [Source: docs/<file>.md#Section]

## Dev Agent Record

### Context Reference

<!-- Path(s) to story context XML will be added here by context workflow -->

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List

<!-- Use these categories to prevent confusion during review -->

**Source Modified:** (implementation changes - subject to review)
- `src/main/java/...` — implementation files
- `src/test/java/...` — test files
- `docs/reference/...` — API documentation

**Generated Artifacts:** (out-of-scope for implementation review)
- `docs/sprint-artifacts/validation-report-*.md` — validation run outputs
- Build outputs, logs, IDE-generated files

**Removed:** (files deleted during implementation)
- List any intentionally removed files
