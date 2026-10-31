#!/usr/bin/env bash
# TDD GREEN 단계 테스트 불변성 기계 감사 (TDD Integrity Gate)
#
# 구현 에이전트가 GREEN 단계(feat: 또는 fix: 커밋)에서 실패하는 기존 단위 테스트의
# 기대값이나 어설션(#expect, XCTAssert, expect(, assertEquals)을 임의로 삭제·수정(Silent Test Modification)하는
# 행위를 git diff 기반으로 0초 만에 적발한다.
#
# 사용법:
#   bash Scripts/verify-tdd-integrity.sh [검사할_커밋_또는_범위]
#   (인자 생략 시 HEAD 커밋이 feat: 또는 fix:인 경우 HEAD~1..HEAD를 검사하고,
#    워킹 트리에 변경이 있으면 HEAD 대비 워킹 트리 변경도 함께 검사한다.)

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_REF="${1:-}"

cd "${ROOT_DIR}"

check_diff_range() {
  local range_Spec="$1"
  local label="$2"

  # 기존에 존재하던 테스트 파일(Modified/Deleted)만 추출 (신규 추가된 테스트 파일 A는 허용)
  local modified_tests
  modified_tests="$(git diff --name-status "${range_Spec}" -- \
    '*Tests.swift' '*.test.ts' '*.test.tsx' '*Test.kt' \
    | awk '$1 != "A" { print $2 }')"

  if [ -z "${modified_tests}" ]; then
    echo "✔ [TDD 무결성 통과] ${label}: 기존 테스트 파일 수정 0건"
    return 0
  fi

  # 삭제된 어설션(- 줄에 #expect, XCTAssert, expect(, assert가 있는 경우) 검사
  local violations=0
  while IFS= read -r test_file; do
    [ -z "${test_file}" ] && continue
    local removed_assertions
    removed_assertions="$(git diff -U0 "${range_Spec}" -- "${test_file}" \
      | grep -E '^-[^-]' \
      | grep -E '(#expect|#require|XCTAssert|expect\(|assertEquals|assertTrue|assertFalse)' || true)"

    if [ -n "${removed_assertions}" ]; then
      echo "✖ [TDD 무결성 위반] ${label} — 기존 테스트 파일(${test_file})의 어설션이 임의 수정·삭제되었습니다:" >&2
      echo "${removed_assertions}" | sed 's/^/    /' >&2
      violations=$((violations + 1))
    fi
  done <<< "${modified_tests}"

  if [ "${violations}" -gt 0 ]; then
    echo "오류: GREEN(구현) 단계에서 기존 테스트 기대값을 수정하는 것(Silent Test Modification)은 금지됩니다." >&2
    return 1
  fi

  echo "✔ [TDD 무결성 통과] ${label}: 기존 어설션 삭제·변조 0건"
  return 0
}

if [ -n "${TARGET_REF}" ]; then
  if [[ "${TARGET_REF}" == *".."* ]]; then
    check_diff_range "${TARGET_REF}" "범위(${TARGET_REF})"
  else
    check_diff_range "${TARGET_REF}~1..${TARGET_REF}" "커밋(${TARGET_REF})"
  fi
else
  # 워킹 트리 변경이 있으면 워킹 트리가 feat/fix 작업 중인지 검사
  if ! git diff --quiet HEAD -- '*Tests.swift' '*.test.ts' '*.test.tsx' '*Test.kt' 2>/dev/null; then
    check_diff_range "HEAD" "워킹 트리(Uncommitted)"
  else
    # 최신 커밋 제목이 feat: 또는 fix:인 경우 해당 커밋에서 기존 테스트 어설션 변조 여부 검사
    last_subject="$(git log -1 --format=%s HEAD 2>/dev/null || true)"
    if [[ "${last_subject}" =~ ^(feat|fix): ]]; then
      check_diff_range "HEAD~1..HEAD" "최신 구현 커밋(${last_subject})"
    else
      echo "✔ [TDD 무결성 통과] 최신 커밋(${last_subject})은 비구현(test/docs/chore/Merge) 커밋입니다."
    fi
  fi
fi
