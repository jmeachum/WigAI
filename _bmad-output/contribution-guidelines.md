# Contribution Guidelines

## Branching and PR Target Rules

- Work from cycle branch context (`develop/cycle-*`) for planned work.
- Typical feature branch: `implementation/<id>-<short-name>`.
- PRs for cycle delivery target `develop/cycle-*`.
- PRs to `main` are restricted to `develop/cycle-*` promotion or `hotfix/*`.

## Commit Standards

- Use Conventional Commits:
  - `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`
- Breaking changes: `feat!` or `BREAKING CHANGE:` footer.
- Commit style drives Nyx semantic versioning/release behavior.

## Local Quality Gate

Before opening a PR:

1. Run tests: `./gradlew test`
2. Build extension: `./gradlew build`
3. Validate artifact: `build/extensions/WigAI.bwextension`
4. (Optional) Host verification in Bitwig environment

## CI Expectations

- Branch policy validation must pass.
- PR validation workflow runs code-change checks and build/test pipeline.
- Docs/config-only changes may skip heavy tests but still require passing status summary checks.

## Code and Documentation Practices

- Follow established patterns in `src/main/java/io/github/fabb/wigai/*`.
- Keep tool contracts and handler behavior aligned with API reference docs.
- Update docs under `docs/` and/or `_bmad-output/` when behavior changes.

## Review and Merge

- Ensure automated checks pass before merge.
- Merge into cycle branch, then promote cycle to `main` when complete.
- Official release publication is handled by GitHub Actions + Nyx on `main`.

## Security/Operational Notes for Contributors

- Preserve loopback-only host safeguards unless explicitly redesigning security architecture.
- Treat MCP response schemas and error envelopes as stable contracts; avoid untracked breaking changes.
