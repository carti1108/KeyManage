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
#
# 이동·삭제 판정 (rename-aware):
#   - 워킹 트리 검사는 임시 인덱스(GIT_INDEX_FILE 사본)에 추적하지 않은 새 파일까지 올린 뒤 HEAD와 비교한다.
#     그래서 폴더를 옮긴 테스트도 새 위치와 짝지어(R) 본다. 실제 인덱스는 건드리지 않는다.
#   - git diff -M의 이름 바꾸기 짝(R: 옛 경로 → 새 경로)만 이동으로 본다. 내용 그대로(R100)면 수정이 아니고,
#     내용이 조금 바뀐 짝은 옛 파일에서 지운 어설션이 그 짝의 새 파일에 글자 그대로(앞뒤 공백 제외) 더해졌을 때만 옮긴 것이다
#     (지운 줄 하나에 더한 줄 하나씩). 짝이 아닌 다른 테스트 파일에 같은 줄을 더해도 옮긴 것으로 보지 않는다.
#   - 테스트 파일 통째 삭제(D)는 그 파일의 모듈을 통째로 지웠을 때만 허용한다. 모듈은 기준 트리에서 그 파일에 가장 가까운
#     상위 빌드 정의 파일(Project.swift, Package.swift, build.gradle.kts, build.gradle, package.json)이 있는 폴더다.
#     허용 조건: ① 그 정의 파일이 이 diff에서 삭제(D)됐고(이름 바꾸기 R의 옛 쪽이면 아님) ② 대상 트리에 그 폴더 아래 파일이 하나도 없다.
#   - 예시 (이 규칙으로 판정한 결과):
#       허용  Features/Auth 전체(Project.swift·Sources·Tests)를 지움 → 모듈 삭제
#       허용  ios-app → apple-app 폴더 이동. 테스트 파일은 R 짝이고 어설션이 새 파일에 그대로 있음
#       위반  Mods/A/Tests/LockTests.swift에서 `#expect(sut.isLocked == true)`를 지우고 Mods/B/Tests/NoopTests.swift(새 파일)에
#             같은 줄을 더함 → 짝이 아니라 이동이 아님
#       위반  git mv Mods/A Mods/A2 뒤 LockTests.swift를 지움 → 정의 파일이 R(이동)이라 모듈 삭제가 아님
#       위반  Mods/A/Project.swift를 Mods/A/x/Project.swift로 옮기고 테스트를 지움 → 정의 파일이 R이고 폴더에 파일이 남음
#       위반  남은 모듈(Core/DesignSystem)에서 테스트 파일 하나만 지움
#       위반  LockTests.swift → LockTests.swift.disabled 이름 바꾸기(R100 포함) → 테스트 파일이 테스트가 아닌 이름이 됨
#       위반  테스트 파일에 비활성화 표시를 더함: `.disabled(`, `@Test(.disabled`, `@Ignore`, `@Disabled`, `XCTSkip`,
#             `it.skip`, `describe.skip`, `xit(`, `test.skip`, `@kotlin.test.Ignore` (새 테스트 파일 포함)

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

# check_diff 라벨 기준_리비전 대상(리비전 또는 "index": 지금 GIT_INDEX_FILE이 가리키는 인덱스)
check_diff() {
  PYTHONIOENCODING=utf-8 python3 - "$1" "$2" "$3" "${TEST_GLOBS[@]}" <<'PY'
import collections
import fnmatch
import re
import subprocess
import sys

label, base, target, *globs = sys.argv[1:]
MANIFESTS = ("Project.swift", "Package.swift", "build.gradle.kts", "build.gradle", "package.json")
ASSERTION = re.compile(r"(#expect|#require|XCTAssert|expect\(|assertEquals|assertTrue|assertFalse|assertThrows|assertThat)")
DISABLE = re.compile(r"(\.disabled\(|@Test\(\.disabled|@Ignore\b|@Disabled\b|XCTSkip|\bit\.skip|describe\.skip|\bxit\(|test\.skip|@kotlin\.test\.Ignore)")
range_args = ["--cached", base] if target == "index" else [base, target]


def git(*args):
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout


def is_test(path):
    name = path.rsplit("/", 1)[-1]
    return any(fnmatch.fnmatchcase(name, g) for g in globs)


def in_base(path):
    return subprocess.run(["git", "cat-file", "-e", f"{base}:{path}"], capture_output=True).returncode == 0


def target_files_under(directory):
    if target == "index":
        out = git("ls-files", "--", directory + "/")
    else:
        out = git("ls-tree", "-r", "--name-only", target, "--", directory + "/")
    return [line for line in out.splitlines() if line]


def changed_lines(paths, sign):
    out = git("diff", "-M", "-U0", *range_args, "--", *paths)
    return [l for l in out.splitlines() if l.startswith(sign) and not l.startswith(sign * 3)]


fields = git("diff", "-M", "--name-status", "-z", *range_args).split("\0")
deleted, renamed_from, pairs, modified = set(), set(), [], []
renamed_out, touched_tests = [], []
i = 0
while i < len(fields) and fields[i]:
    status = fields[i]
    if status[0] in "RC":
        old, new = fields[i + 1], fields[i + 2]
        i += 3
        if is_test(new):
            touched_tests.append(new)
        if status[0] == "R":
            renamed_from.add(old)
            if is_test(old) and not is_test(new):
                renamed_out.append((old, new))
            if status != "R100" and (is_test(old) or is_test(new)):
                pairs.append((old, new))
        continue
    path = fields[i + 1]
    i += 2
    if status != "D" and is_test(path):
        touched_tests.append(path)
    if status == "D":
        deleted.add(path)
    elif status != "A" and is_test(path):
        modified.append(path)

violations = []

# 이름 바꾸기 짝: 지운 어설션은 그 짝의 새 파일에 글자 그대로 있어야 한다
for old, new in pairs:
    added = collections.Counter(l[1:].strip() for l in changed_lines([old, new], "+"))
    for line in changed_lines([old, new], "-"):
        if not ASSERTION.search(line):
            continue
        key = line[1:].strip()
        if added[key] > 0:
            added[key] -= 1
        else:
            violations.append(f"{old} → {new} 이동 중 어설션 삭제·변조:\n      {line}")

# 테스트 파일을 테스트가 아닌 이름으로 바꾸면(예: LockTests.swift → LockTests.swift.disabled) 실행 대상에서 빠진다
for old, new in renamed_out:
    violations.append(f"{old} → {new} 테스트 파일을 테스트가 아닌 이름으로 바꿈 — 테스트가 실행되지 않습니다.")

# 테스트 파일에 더한 비활성화 표시(.disabled, @Ignore, XCTSkip, it.skip 등)
if touched_tests:
    for line in changed_lines(touched_tests, "+"):
        if DISABLE.search(line):
            violations.append(f"테스트 비활성화 추가:\n      {line}")

for path in modified:
    for line in changed_lines([path], "-"):
        if ASSERTION.search(line):
            violations.append(f"{path} 어설션 삭제·변조:\n      {line}")


def module_of(path):
    parts = path.split("/")[:-1]
    while parts:
        directory = "/".join(parts)
        for manifest in MANIFESTS:
            if in_base(f"{directory}/{manifest}"):
                return directory, f"{directory}/{manifest}"
        parts.pop()
    return None, None


removed_with_module = 0
for path in sorted(p for p in deleted if is_test(p)):
    directory, manifest = module_of(path)
    module_removed = (
        directory is not None
        and manifest in deleted
        and manifest not in renamed_from
        and not target_files_under(directory)
    )
    if module_removed:
        removed_with_module += 1
    else:
        violations.append(f"{path} 통째 삭제 — 모듈({directory or '없음'})을 함께 지우지 않았습니다(정의 파일 이동·남은 파일 포함).")

if violations:
    print(f"✖ [TDD 무결성 위반] {label}", file=sys.stderr)
    for v in violations:
        print(f"  ✖ {v}", file=sys.stderr)
    print("오류: GREEN(구현) 단계에서 기존 테스트 기대값을 수정하는 것(Silent Test Modification)은 금지됩니다.", file=sys.stderr)
    sys.exit(1)

print(f"✔ [TDD 무결성 통과] {label}: 어설션 삭제·변조 0건 (이동한 테스트 {len(pairs)}건, 모듈과 함께 지운 테스트 {removed_with_module}건)")
PY
}

# 워킹 트리 검사용 임시 인덱스. 추적하지 않은 새 파일(.gitignore 제외)까지 올려 이름 바꾸기를 짝짓는다
make_worktree_index() {
  WORKTREE_INDEX="$(mktemp "${TMPDIR:-/tmp}/tdd-integrity-index.XXXXXX")"
  trap 'rm -f "${WORKTREE_INDEX}"' EXIT
  local real_index
  real_index="$(git rev-parse --git-path index)"
  if [ -f "${real_index}" ]; then
    cp "${real_index}" "${WORKTREE_INDEX}"
  else
    rm -f "${WORKTREE_INDEX}"
  fi
  GIT_INDEX_FILE="${WORKTREE_INDEX}" git add -A
}

if [ "${TARGET_REF}" = "--cached" ] || [ "${TARGET_REF}" = "--staged" ]; then
  check_diff "스테이징 영역(Staged)" HEAD index
elif [ -n "${TARGET_REF}" ]; then
  if [[ "${TARGET_REF}" == *".."* ]]; then
    range_base="${TARGET_REF%%..*}"; range_target="${TARGET_REF##*..}"
    check_diff "범위(${TARGET_REF})" "${range_base:-HEAD}" "${range_target:-HEAD}"
  else
    check_diff "커밋(${TARGET_REF})" "${TARGET_REF}~1" "${TARGET_REF}"
  fi
else
  # 1) 워킹 트리(스테이징·추적하지 않은 새 파일 포함) 변경이 있으면 HEAD 대비 변경 검사
  make_worktree_index
  if ! GIT_INDEX_FILE="${WORKTREE_INDEX}" git diff --cached --quiet HEAD -- "${TEST_GLOBS[@]}" 2>/dev/null; then
    GIT_INDEX_FILE="${WORKTREE_INDEX}" check_diff "워킹 트리(Uncommitted)" HEAD index
  # 2) GitHub Actions PR 환경(GITHUB_BASE_REF 설정)에서는 Merge 커밋 뒤에 숨은 PR 내 모든 feat:/fix: 커밋을 개별 전수 검사
  elif [ -n "${GITHUB_BASE_REF:-}" ] && git rev-parse --verify "origin/${GITHUB_BASE_REF}" >/dev/null 2>&1; then
    pr_commits="$(git rev-list --no-merges "origin/${GITHUB_BASE_REF}..HEAD" 2>/dev/null || true)"
    checked_count=0
    while IFS= read -r sha; do
      [ -z "${sha}" ] && continue
      subj="$(git log -1 --format=%s "${sha}" 2>/dev/null || true)"
      if [[ "${subj}" =~ ^(feat|fix): ]]; then
        check_diff "PR 구현 커밋(${sha:0:7}: ${subj})" "${sha}~1" "${sha}"
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
      check_diff "최신 구현 커밋(${last_subject})" HEAD~1 HEAD
    else
      echo "✔ [TDD 무결성 통과] 최신 커밋(${last_subject})은 비구현(test/docs/chore/Merge) 커밋입니다."
    fi
  fi
fi
