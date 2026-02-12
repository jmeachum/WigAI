# Contribution Guide

## Branching Rules

- Planned work targets cycle branches (`develop/cycle-*`)
- Typical implementation branch: `implementation/<id>-<short-name>`
- PRs to `main` are restricted to cycle promotion or hotfix flows

## Commit Rules

Use Conventional Commits:
- `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`
- Use `feat!` / `BREAKING CHANGE` for breaking changes

## Pre-PR Checklist

1. `./gradlew test`
2. `./gradlew build`
3. Verify `build/extensions/WigAI.bwextension`
4. Update docs if behavior/contracts changed

## CI Expectations

- Branch policy validation must pass
- PR validation status must pass
- Docs/config-only changes may skip heavy tests but still require passing summary checks

## Code and Architecture Discipline

- Keep tool APIs consistent with documented contracts
- Keep Bitwig API calls within facade/controller boundaries
- Preserve loopback-only default posture unless architecture/security model explicitly changes
