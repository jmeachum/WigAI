# Story 3.02 — Scoped Refactor for Context-Bloat Reduction in High-Churn Files

Branch: `implementation/story-3-02-context-bloat-refactor`
Baseline: 747 tests / 64 classes / 0 failures (`./gradlew test`, captured before any edit)

## Evidence base

Churn (commits touching file, all history) x size:

| File | Lines | Methods | Commits | Last-60 |
|---|---:|---:|---:|---:|
| bitwig/BitwigApiFacade.java | 2076 | 65 | 33 | 18 |
| mcp/McpServerManager.java | 178 | 5 | 28 | 6 |
| server/JettyServerManager.java | 435 | 16 | 16 | 15 |
| mcp/McpErrorHandler.java | 732 | 29 | 7 | 5 |
| features/ClipSceneController.java | 529 | 31 | 10 | 6 |
| common/logging/StructuredLogger.java | 413 | 32 | - | - |
| (test) McpErrorHandlerTest.java | 1718 | - | - | - |
| (test) BitwigApiFacadeTest.java | 1634 | - | 15 | 15 |

High churn AND oversized = `BitwigApiFacade` (primary), then `McpErrorHandler`,
`ClipSceneController`. `McpServerManager` is high-churn but only 178 lines — not a
bloat target.

## Dead code: verified negative result

`find_dead_code` (codegraphcontext MCP) reported 363 "high confidence" unused
functions. Cross-checking every one of the 28 it flagged in `BitwigApiFacade`
against grep showed **all 28 are live** (called externally or as private helpers).
The Java index holds only 221 `CALLS` edges for 1251 functions, and reports
`cyclomatic_complexity = 1` for every Java method — so the graph's call-graph and
complexity outputs are not trustworthy here. Its **structural** output (file /
method inventory, line spans, imports) is accurate and is what this plan is built on.

A full grep cross-check of all 364 `src/main` methods found **10** with zero
references. Verified individually:

| Method | Lines | Verdict |
|---|---:|---|
| `McpErrorHandler.upgradeLegacyErrorResponse` | 20 | dead — remove |
| `McpErrorHandler.getIdempotencyCache` | 3 | dead — remove |
| `JettyServerManager.getContextHandler` | 9 | dead — remove |
| `StructuredLogger.addContext/removeContext/clearContext` | ~20 | dead feature — nothing ever writes `contextMetadata`; its only reader (L333-334) is then also dead |
| `StructuredLogger.TimedOperation.getDuration/getOperationId` | ~14 | dead — remove |
| `WigAIExtensionDefinition.isUsingBitwigMidiAPI` | 5 | dead — confirmed absent from `ControllerExtensionDefinition` in extension-api v19 |
| `BitwigApiFacade.getTrackDetailsByName` | 12 | **keep** — directly adjacent to Story 3.03 (`get_track_details` targeting) |

Total removable: ~85 lines. The codebase has very little dead code; deletion is not
where this story's value is.

## Performance: stated honestly

Splitting Java files produces **no runtime performance change** — same bytecode,
same JIT behavior, same class count on the hot path. The real gains, and the ones
Story 3.02's ACs actually ask for:
- **Incremental compile**: a change to device logic stops recompiling the whole
  2076-line facade's dependents.
- **Review/context surface**: the story's stated goal — per-change diff and
  per-question context load drop with file size.

No claim of runtime speedup will be made in commits or evidence.

## Chunks

Each chunk is independently committable and ends with `./gradlew test` at 747/747.
Public API surface of every touched class is preserved — callers do not change.

### Chunk 1 — Dead-code removal (~85 lines)
The verified list above, minus `getTrackDetailsByName`. No new tests; existing
suite proves nothing regressed.

### Chunk 2 — `ClipSceneController`: extract result types (529 → ~380)
`ClipLaunchResult` (L379-492, ~115 lines) and `SceneLaunchResult` (L503-527) are
static nested value classes. Move each to its own file in `features/`. Pure
mechanical move; lowest risk in the plan.

### Chunk 3 — `McpErrorHandler`: extract pure helpers (732 → ~460)
Two cohesive, side-effect-free clusters:
- `PayloadFingerprint` — `computePayloadFingerprint`, `canonicalizeValue`,
  `normalizeNumber`, `normalizeBigDecimal`, `sha256Hex` (~160 lines)
- `RequestContextExtractor` — `extractLoggingParameters`, `extractRawRequestId`,
  `sanitizeRequestId` (~110 lines)

Both become directly unit-testable without the MCP response machinery. TDD: new
focused tests against the extracted classes first, then move.

### Chunk 4 — `BitwigApiFacade`: extract domain sub-facades (2076 → ~450)
Follows the pattern the codebase already established with `SceneBankFacade`: a
collaborator owning its own Bitwig bank/cursor state, constructed in
`BitwigApiFacade`'s constructor, with the facade delegating in 3-4 line methods.
Sub-chunks, each its own commit:

- **4a** `TrackTargetingFacade` — L243-546 (~300): `findTrackByIndex`,
  `getTrackCandidatesByName`, `matchPrecedence`, `determineTrackMatchType`,
  `resolveTrack`, `collectResolveTrackCandidates`,
  `captureVisibleResolveTrackCandidates`, `getTrackNameByIndex`,
  `resolveTrackIndex`, `requireTrackByIndex`
- **4b** `DeviceFacade` — L578-675, 1431-1470, 1725-2075 (~450)
- **4c** `TransportFacade` — L551-562, 976-1081 (~150)
- **4d** `TrackInfoFacade` — L1090-1229, 1340-1422, 1475-1714 (~400)
- **4e** `ClipSlotFacade` — L750-948, 1238-1332 (~300)

Risk concentrates in the 165-line constructor (L67-231), which wires every cursor
and bank. Each sub-chunk moves only its own wiring, and the facade keeps handing
out the same objects, so `BitwigApiFacadeTest`'s Mockito graph keeps working
unchanged — that test passing is the behavior-unchanged proof.

### Chunk 5 — Test-file split (optional, gated separately)
`BitwigApiFacadeTest` (1634) carries an existing in-repo TODO from a prior review
asking for exactly this. Split along the Chunk 4 seams; same for
`McpErrorHandlerTest` (1718) along Chunk 3's. Largest line count, lowest risk,
zero production impact.

## Verification per chunk
1. `./gradlew test` → 747/747, 0 failures
2. `git diff --stat` recorded as review-surface evidence (AC 3)
3. `code-reviewer` after each production-code chunk
