#!/usr/bin/env bash
#
# Enforces single status authority between story files and the sprint tracker.
#
# Authority model:
#   _bmad-output/implementation-artifacts/sprint-status.yaml is the tracker of record.
#   Each story file carries a `Status:` header that MUST match its tracker entry.
#   A story is not reviewable while the two disagree.
#
# Implements the intent of Epic 3 Story 3-01
# (enforce-single-status-authority-change-log-discipline).
#
# Usage: ./scripts/check-story-status.sh
# Exit:  0 = consistent, 1 = drift or malformed status found.

set -euo pipefail

# Run from the repository root regardless of how this script was invoked.
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

ARTIFACTS_DIR="${ARTIFACTS_DIR:-_bmad-output/implementation-artifacts}"
TRACKER="${TRACKER:-${ARTIFACTS_DIR}/sprint-status.yaml}"

VALID_STORY_STATUS="backlog ready-for-dev in-progress review done"
VALID_EPIC_STATUS="backlog in-progress done"

echo "== WigAI Story Status Check =="
echo "Tracker: ${TRACKER}"
echo ""

if [[ ! -f "${TRACKER}" ]]; then
  echo "ERROR: tracker not found: ${TRACKER}"
  exit 1
fi

# Extract the development_status block into "key<TAB>value" lines.
TRACKER_ENTRIES="$(awk '
  /^development_status:/ { inblock = 1; next }
  inblock && /^[^[:space:]]/ { inblock = 0 }
  inblock && /^[[:space:]]+[A-Za-z0-9_-]+:[[:space:]]*[a-z-]+[[:space:]]*$/ {
    line = $0
    sub(/^[[:space:]]+/, "", line)
    split(line, parts, ":")
    key = parts[1]
    value = parts[2]
    gsub(/[[:space:]]/, "", value)
    print key "\t" value
  }
' "${TRACKER}")"

if [[ -z "${TRACKER_ENTRIES}" ]]; then
  echo "ERROR: no development_status entries parsed from ${TRACKER}"
  exit 1
fi

lookup() {
  # $1 = key; echoes the tracked value, or nothing when absent.
  awk -F'\t' -v want="$1" '$1 == want { print $2; exit }' <<<"${TRACKER_ENTRIES}"
}

in_list() {
  # $1 = needle, $2 = space-separated haystack
  [[ " $2 " == *" $1 "* ]]
}

PROBLEMS=0
CHECKED=0

report() {
  echo "  DRIFT  $*"
  PROBLEMS=$((PROBLEMS + 1))
}

# --- 1. Every story file agrees with the tracker -----------------------------
for file in "${ARTIFACTS_DIR}"/[0-9]*-*.md; do
  [[ -e "${file}" ]] || continue
  key="$(basename "${file}" .md)"

  header="$(grep -m1 -E '^Status:[[:space:]]*' "${file}" | sed -E 's/^Status:[[:space:]]*//; s/[[:space:]]*$//' || true)"
  tracked="$(lookup "${key}")"
  CHECKED=$((CHECKED + 1))

  if [[ -z "${header}" ]]; then
    report "${key}: story file has no 'Status:' header"
    continue
  fi

  if ! in_list "${header}" "${VALID_STORY_STATUS}"; then
    report "${key}: story header status '${header}' is not one of: ${VALID_STORY_STATUS}"
    continue
  fi

  if [[ -z "${tracked}" ]]; then
    report "${key}: story file exists but has no entry in the tracker"
    continue
  fi

  if [[ "${header}" != "${tracked}" ]]; then
    report "${key}: story header says '${header}', tracker says '${tracked}'"
  fi
done

# --- 2. Tracker statuses are well-formed -------------------------------------
while IFS=$'\t' read -r key value; do
  [[ -n "${key}" ]] || continue
  case "${key}" in
    epic-*-retrospective)
      in_list "${value}" "optional done" \
        || report "${key}: retrospective status '${value}' is not one of: optional done"
      ;;
    epic-*)
      in_list "${value}" "${VALID_EPIC_STATUS}" \
        || report "${key}: epic status '${value}' is not one of: ${VALID_EPIC_STATUS}"
      ;;
    *)
      in_list "${value}" "${VALID_STORY_STATUS}" \
        || report "${key}: tracker status '${value}' is not one of: ${VALID_STORY_STATUS}"
      ;;
  esac
done <<<"${TRACKER_ENTRIES}"

# --- 3. Epic rollup: an epic whose stories are all done should be done --------
while IFS=$'\t' read -r key value; do
  [[ "${key}" =~ ^epic-([0-9]+)$ ]] || continue
  epic_num="${BASH_REMATCH[1]}"
  [[ "${value}" == "done" ]] && continue

  total=0
  done_count=0
  while IFS=$'\t' read -r skey svalue; do
    [[ "${skey}" =~ ^${epic_num}-[0-9]+- ]] || continue
    total=$((total + 1))
    [[ "${svalue}" == "done" ]] && done_count=$((done_count + 1))
  done <<<"${TRACKER_ENTRIES}"

  if [[ "${total}" -gt 0 && "${total}" -eq "${done_count}" ]]; then
    report "epic-${epic_num}: all ${total} stories are done but the epic is still '${value}'"
  fi
done <<<"${TRACKER_ENTRIES}"

echo ""
echo "Story files checked: ${CHECKED}"

if [[ "${PROBLEMS}" -gt 0 ]]; then
  echo "Status problems found: ${PROBLEMS}"
  echo ""
  echo "Resolve by making the tracker and the story header agree, and record the change"
  echo "in the story's Change Log. The tracker is the authority for what the status IS;"
  echo "the story Change Log is the authority for WHY it changed."
  exit 1
fi

echo "OK - story headers and tracker agree"
