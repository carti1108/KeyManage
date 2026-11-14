#!/usr/bin/env bash
# 범용 Swift / Clean Architecture 정적 아키텍처·스텁·동시성 린트 (Universal Hard Constraint Gate)
#
# 특정 도메인에 종속되지 않는 범용 Swift 아키텍처 및 동시성 결함 패턴을
# 컴파일 전 0초 만에 기계적으로 차단한다. (ios-architect 플러그인 번들 스크립트)
#
# 검사 항목:
#   (옛 규칙 1 "Domain/**/Sources의 UI 프레임워크 import 금지"는 Swift 도메인 계층을 지워 뺐다.
#    KeyManage 도메인은 Kotlin :domain에 있고, Swift 쪽에 도메인을 다시 두지 않는 규칙은 check-apple-lint.sh 4번이 맡는다)
#   2. 스텁 및 미완성 코드 잔존 금지: 프로덕션 Sources 내 case notImplemented, fatalError( (단, init(coder:) 제외), TODO:, FIXME: 금지
#   3. 비재진입 락(NSLock/NSRecursiveLock) 보유 중 명시적 .unlock() 없이 외부 콜백 동기 호출 데드락 패턴 차단
#
# 사용법:
#   bash Scripts/check-swift-architecture.sh [검색_루트_경로]

set -euo pipefail

ROOT_DIR="${1:-$(git rev-parse --show-toplevel 2>/dev/null || (cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd))}"

if [ ! -d "${ROOT_DIR}" ]; then
  echo "오류: ${ROOT_DIR} 디렉터리를 찾을 수 없습니다." >&2
  exit 2
fi

PYTHON=""
for candidate in python3 python; do
  if command -v "$candidate" >/dev/null 2>&1 \
    && "$candidate" -c 'import sys; sys.exit(0 if sys.version_info[0] >= 3 else 1)' >/dev/null 2>&1; then
    PYTHON="$candidate"
    break
  fi
done

if [ -z "$PYTHON" ]; then
  echo "오류: 실행 가능한 python3를 찾을 수 없습니다." >&2
  exit 2
fi

PYTHONIOENCODING=utf-8 "$PYTHON" - "$ROOT_DIR" <<'PY'
import os
import re
import sys

for stream in (sys.stdout, sys.stderr):
    if hasattr(stream, "reconfigure"):
        stream.reconfigure(encoding="utf-8")

root = os.path.abspath(sys.argv[1])
SKIP_DIRS = {
    ".git", ".build", "build", "Derived", "DerivedData",
    "Pods", "Carthage", "node_modules", "SourcePackages", "checkouts",
}
MANIFEST_FILES = {"Project.swift", "Package.swift", "Workspace.swift", "Tuist.swift"}

def rel(path):
    return os.path.relpath(path, root).replace(os.sep, "/")

def strip_comments(text):
    out = []
    for line in text.splitlines():
        stripped = re.sub(r"//.*$", "", line)
        out.append(stripped)
    return "\n".join(out)

all_swift_files = []
for current_root, dirs, files in os.walk(root):
    dirs[:] = sorted(d for d in dirs if d not in SKIP_DIRS)
    for fname in sorted(files):
        if fname.endswith(".swift"):
            all_swift_files.append(os.path.join(current_root, fname))

production_source_files = [
    p for p in all_swift_files
    if "/Sources/" in f"/{rel(p)}"
    and "/Tests/" not in f"/{rel(p)}"
    and "/Testing/" not in f"/{rel(p)}"
    and "/Example/" not in f"/{rel(p)}"
    and os.path.basename(p) not in MANIFEST_FILES
]

if not production_source_files:
    print(f"오류: {root} 하위에서 검사할 Swift 프로덕션 소스(**/Sources/**/*.swift)를 찾을 수 없습니다.", file=sys.stderr)
    sys.exit(2)

violations = []

# 2. 프로덕션 Sources 내 미구현 스텁·미완성 잔재 금지
for path in production_source_files:
    with open(path, encoding="utf-8") as f:
        lines = f.read().splitlines()
    for idx, line in enumerate(lines, start=1):
        code_no_comment = re.sub(r"//.*$", "", line)
        if re.search(r"\bcase\s+notImplemented\b", code_no_comment):
            violations.append(
                f"[규칙2: 스텁 잔재 금지] {rel(path)}:{idx} — 미구현 에러 스텁(case notImplemented) 잔존: {line.strip()}"
            )
        if re.search(r"\bfatalError\s*\(", code_no_comment) and "init(coder:)" not in line:
            violations.append(
                f"[규칙2: 스텁 잔재 금지] {rel(path)}:{idx} — 프로덕션 코드 내 fatalError() 호출 금지: {line.strip()}"
            )
        if re.search(r"//\s*(TODO|FIXME)\b", line):
            violations.append(
                f"[규칙2: 스텁 잔재 금지] {rel(path)}:{idx} — 미해결 TODO/FIXME 주석 잔존: {line.strip()}"
            )

# 3. 비재진입 락(NSLock/NSRecursiveLock) 보유 중 외부 콜백 동기 호출(데드락 위험) 검사
callback_pat = re.compile(r"\b(_?on[A-Z][A-Za-z0-9_]*|[A-Za-z0-9_]*Action|callback|completion|handler)\s*\?\s*\(")
for path in production_source_files:
    with open(path, encoding="utf-8") as f:
        content = strip_comments(f.read())
    if not re.search(r"\bNS(Recursive)?Lock\b", content):
        continue
    lines = content.splitlines()
    current_func_lines = []
    for idx, line in enumerate(lines, start=1):
        if re.search(r"\b(func\s+[A-Za-z0-9_]+\s*\(|deinit\b)", line):
            current_func_lines = []
        current_func_lines.append((idx, line))
        if callback_pat.search(line):
            has_lock_before = any(".lock()" in prev_line for _, prev_line in current_func_lines[:-1])
            if has_lock_before:
                has_explicit_unlock_before = any(
                    ".unlock()" in prev_line and "defer" not in prev_line
                    for _, prev_line in current_func_lines[:-1]
                )
                if not has_explicit_unlock_before:
                    violations.append(
                        f"[규칙3: 동시성 데드락 위험] {rel(path)}:{idx} — Lock을 획득한 상태에서 명시적 unlock() 없이 외부 콜백({line.strip()})을 동기 호출하면 재진입 데드락이 발생합니다."
                    )

if violations:
    print("범용 Swift Clean Architecture·스텁·동시성 린트 위반 발견:")
    for v in violations:
        print(f"  ✖ {v}")
    sys.exit(1)

print("✔ 범용 Swift Clean Architecture·스텁·동시성 린트 통과 (위반 0건)")
PY
