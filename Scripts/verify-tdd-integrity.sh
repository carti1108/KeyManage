#!/usr/bin/env bash
# TDD GREEN 단계 테스트 불변성 기계 감사 (TDD Integrity Gate — qa-tester 플러그인 번들 스크립트)
#
# 구현 에이전트가 GREEN 단계(feat: 또는 fix: 커밋, 혹은 워킹 트리/스테이징 수정)에서
# 실패하는 기존 단위 테스트의 기대값이나 어설션(#expect, XCTAssert, expect(, assertEquals 등)을
# 임의로 삭제·수정(Silent Test Modification)하는 행위를 git diff 기반으로 0초 만에 적발한다.
#
# 사용법:
#   bash Scripts/verify-tdd-integrity.sh [--cached|--staged | 검사할_커밋_또는_범위]
#   (의도적인 테스트 리팩터링 시 ALLOW_TEST_EDIT=1 환경변수로 우회 가능)

set -euo pipefail

if [ "${ALLOW_TEST_EDIT:-${KM_ALLOW_TEST_EDIT:-0}}" = "1" ]; then
  echo "✔ [TDD 무결성 건너뜀] ALLOW_TEST_EDIT=1 설정으로 인해 검사를 건너뜁니다."
  exit 0
fi

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || (cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd))"
TARGET_REF="${1:-}"

cd "${ROOT_DIR}"

TEST_GLOBS=(
  '*Tests.swift'
  '*.test.ts'
  '*.test.tsx'
  '*.spec.ts'
  '*.spec.tsx'
  '*Test.kt'
  '*Tests.kt'
  '*Test.java'
  '*Tests.java'
)

check_diff_args() {
  local label="$1"
  shift
  local diff_args=("$@")

  # 기존에 존재하던 테스트 파일(Modified/Deleted)만 추출 (신규 추가된 테스트 파일 A는 허용)
  local modified_tests
  modified_tests="$(git diff --name-status "${diff_args[@]}" -- "${TEST_GLOBS[@]}" \
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
    removed_assertions="$(git diff -U0 "${diff_args[@]}" -- "${test_file}" \
      | grep -E '^-[^-]' \
      | grep -E '(#expect|#require|XCTAssert|expect\(|assertEquals|assertTrue|assertFalse|assertThrows|assertThat)' || true)"

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

if [ "${TARGET_REF}" = "--cached" ] || [ "${TARGET_REF}" = "--staged" ]; then
  check_diff_args "스테이징 영역(Staged)" "--cached"
elif [ -n "${TARGET_REF}" ]; then
  if [[ "${TARGET_REF}" == *".."* ]]; then
    check_diff_args "범위(${TARGET_REF})" "${TARGET_REF}"
  else
    check_diff_args "커밋(${TARGET_REF})" "${TARGET_REF}~1..${TARGET_REF}"
  fi
else
  # 1) 워킹 트리(스테이징 포함) 변경이 있으면 HEAD 대비 변경 검사
  if ! git diff --quiet HEAD -- "${TEST_GLOBS[@]}" 2>/dev/null; then
    check_diff_args "워킹 트리(Uncommitted)" "HEAD"
  # 2) GitHub Actions PR 환경(GITHUB_BASE_REF 설정)에서는 Merge 커밋 뒤에 숨은 PR 내 모든 feat:/fix: 커밋을 개별 전수 검사
  elif [ -n "${GITHUB_BASE_REF:-}" ] && git rev-parse --verify "origin/${GITHUB_BASE_REF}" >/dev/null 2>&1; then
    pr_commits="$(git rev-list --no-merges "origin/${GITHUB_BASE_REF}..HEAD" 2>/dev/null || true)"
    checked_count=0
    while IFS= read -r sha; do
      [ -z "${sha}" ] && continue
      subj="$(git log -1 --format=%s "${sha}" 2>/dev/null || true)"
      if [[ "${subj}" =~ ^(feat|fix): ]]; then
        check_diff_args "PR 구현 커밋(${sha:0:7}: ${subj})" "${sha}~1..${sha}"
        checked_count=$((checked_count + 1))
      fi
    done <<< "${pr_commits}"
    if [ "${checked_count}" -eq 0 ]; then
      echo "✔ [TDD 무결성 통과] PR 범위(origin/${GITHUB_BASE_REF}..HEAD) 내 feat:/fix: 커밋 0건"
    fi
  else
    # 3) 로컬 깨끗한 워킹 트리: 최신 커밋 제목이 feat: 또는 fix:인 경우 검사
    last_subject="$(git log -1 --format=%s HEAD 2>/dev/null || true)"
    if [[ "${last_subject}" =~ ^(feat|fix): ]]; then
      check_diff_args "최신 구현 커밋(${last_subject})" "HEAD~1..HEAD"
    else
      echo "✔ [TDD 무결성 통과] 최신 커밋(${last_subject})은 비구현(test/docs/chore/Merge) 커밋입니다."
    fi
  fi
fi
