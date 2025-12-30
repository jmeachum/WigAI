# Senior Developer Review - Validation Checklist

- [ ] Story file loaded from `{{story_path}}`
- [ ] Story Status verified as reviewable (review)
- [ ] Epic and Story IDs resolved ({{epic_num}}.{{story_num}})
- [ ] Story Context located or warning recorded
- [ ] Epic Tech Spec located or warning recorded
- [ ] Architecture/standards docs loaded (as available)
- [ ] Tech stack detected and documented
- [ ] MCP doc search performed (or web fallback) and references captured
- [ ] Acceptance Criteria cross-checked against implementation
- [ ] File List reviewed and validated for completeness
- [ ] Tests identified and mapped to ACs; gaps noted
- [ ] Code quality review performed on changed files
- [ ] Error handling compliance verified (see Error Handling Compliance section below)
- [ ] Security review performed on changed files and dependencies
- [ ] Outcome decided (Approve/Changes Requested/Blocked)
- [ ] Review notes appended under "Senior Developer Review (AI)"
- [ ] Change Log updated with review entry
- [ ] Status updated according to settings (if enabled)
- [ ] Sprint status synced (if sprint tracking enabled)
- [ ] Story saved successfully

_Reviewer: {{user_name}} on {{date}}_

---

## Error Handling Compliance

Verify error responses match the contract defined in `docs/project-context.md`:

### Error Code Usage
- [ ] All error paths use `ErrorCode` enum (no ad-hoc strings)
- [ ] Error codes match contract semantics:
  - `MISSING_REQUIRED_PARAMETER` — parameter not provided in request
  - `EMPTY_PARAMETER` — parameter provided but empty (empty string, empty array)
  - `INVALID_PARAMETER` — wrong type or malformed structure
  - `INVALID_PARAMETER_INDEX` — index outside valid bounds (e.g., 0-7)
  - `INVALID_RANGE` — numeric value outside allowed range (e.g., 0.0-1.0)
  - `BITWIG_API_ERROR` — Bitwig API call failed (external system)
  - `INTERNAL_ERROR` — unexpected internal failure (code bug)

### Error Response Structure
- [ ] `error.operation` equals MCP tool name (not internal method names)
- [ ] `error.message` is actionable (not generic "operation failed")
- [ ] Error responses use `McpErrorHandler` (no bespoke envelopes)

### Documentation & Tests
- [ ] API docs (`docs/reference/api-reference.md`) error section matches implementation
- [ ] Tests assert contract behavior (not implementation accidents)
- [ ] Contract tests updated if new error scenarios added (`ErrorContractComplianceTest`)

### Story Error Scenarios
- [ ] Story "Error Scenarios" table populated (if applicable)
- [ ] Implementation matches story-defined error scenarios
