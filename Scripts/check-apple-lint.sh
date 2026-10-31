#!/usr/bin/env bash
# Apple 플랫폼 정적 아키텍처·보안·스텁 린트 (Hard Constraint Gate)
#
# 프롬프트 지시(Soft Rule)나 리뷰어의 수동 지적에만 의존하던 반복 결함 패턴을
# 컴파일 전 0초 만에 기계적으로 차단한다.
#
# 검사 항목:
#   1. Clean Architecture 도메인 격리: Domain/Sources 내 UI/플랫폼 프레임워크(UIKit, AppKit, SwiftUI, LocalAuthentication) import 금지
#   2. 스텁 및 미완성 코드 잔존 금지: Projects/**/Sources 내 case notImplemented, fatalError( (단, init(coder:) 제외), TODO:, FIXME: 금지
#   3. 비재진입 락(NSLock/NSRecursiveLock) 보유 중 외부 콜백 동기 호출 데드락 패턴 차단
#   4. 잠금/세션 모듈 보안 불변식: Lock/ 또는 Session/ 존재 시 memset_s 제로화 및 LocalizedError 표준 문구 구현 필수
#   5. 생체 인증 키 복호화 우회 차단: unlockWithBiometrics 시그니처에 복호화 키(Key: Data) 비옵셔널 강제

set -euo pipefail

ROOT_DIR="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
APPLE_PROJECTS_DIR="${ROOT_DIR}/Apps/Apple/Projects"

if [ ! -d "${APPLE_PROJECTS_DIR}" ]; then
  echo "오류: ${APPLE_PROJECTS_DIR} 디렉터리를 찾을 수 없습니다." >&2
  exit 2
fi

PYTHON=""
for candidate in python3 python; do
  if command -v "$candidate" >/dev/null 2>&1; then
    PYTHON="$candidate"
    break
  fi
done

if [ -z "$PYTHON" ]; then
  echo "오류: 실행 가능한 python3를 찾을 수 없습니다." >&2
  exit 2
fi

"$PYTHON" - "$ROOT_DIR" <<'PY'
import glob
import os
import re
import sys

root = sys.argv[1]
projects_dir = os.path.join(root, "Apps", "Apple", "Projects")
domain_sources_dir = os.path.join(projects_dir, "Core", "Domain", "Sources")

violations = []

def rel(path):
    return os.path.relpath(path, root).replace(os.sep, "/")

def strip_comments(text):
    out = []
    for line in text.splitlines():
        stripped = re.sub(r"//.*$", "", line)
        out.append(stripped)
    return "\n".join(out)

# 1. Domain/Sources 프레임워크 격리 검사
forbidden_domain_imports = ("UIKit", "AppKit", "SwiftUI", "LocalAuthentication", "WebKit")
for path in sorted(glob.glob(os.path.join(domain_sources_dir, "**", "*.swift"), recursive=True)):
    with open(path, encoding="utf-8") as f:
        lines = f.read().splitlines()
    for idx, line in enumerate(lines, start=1):
        code = re.sub(r"//.*$", "", line).strip()
        m = re.match(r"^import\s+([A-Za-z0-9_]+)", code)
        if m and m.group(1) in forbidden_domain_imports:
            violations.append(
                f"[규칙1: 도메인 격리 위반] {rel(path)}:{idx} — Domain 계층에서 '{m.group(1)}'을 import할 수 없습니다."
            )

# 2. 프로덕션 Sources 내 미구현 스텁·미완성 잔재 금지
all_source_files = [
    p for p in sorted(glob.glob(os.path.join(projects_dir, "**", "*.swift"), recursive=True))
    if "/Tests/" not in p.replace(os.sep, "/")
    and "/Testing/" not in p.replace(os.sep, "/")
    and os.path.basename(p) != "Project.swift"
]

for path in all_source_files:
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

# 3. 락 보유 중 외부 클립보드/이벤트 콜백 동기 호출(데드락 위험) 검사
# 클립보드 콜백을 호출하는 함수 내부에는 반드시 콜백 호출 이전에 명시적 .unlock()이 선행되어야 한다.
callback_pat = re.compile(r"\b(_?onClipboardClear(ed)?|clipboardAction|callback)\s*\?\s*\(")
for path in all_source_files:
    with open(path, encoding="utf-8") as f:
        content = strip_comments(f.read())
    if not re.search(r"\bNS(Recursive)?Lock\b", content):
        continue
    lines = content.splitlines()
    current_func_lines = []
    for idx, line in enumerate(lines, start=1):
        if re.search(r"\bfunc\s+[A-Za-z0-9_]+\s*\(", line):
            current_func_lines = []
        current_func_lines.append((idx, line))
        if callback_pat.search(line):
            # 현재 함수 내에서 콜백 호출 줄 이전에 명시적 .unlock() (defer 제외)이 있는지 확인
            has_explicit_unlock_before = any(
                ".unlock()" in prev_line and "defer" not in prev_line
                for _, prev_line in current_func_lines[:-1]
            )
            if not has_explicit_unlock_before:
                violations.append(
                    f"[규칙3: 동시성 데드락 위험] {rel(path)}:{idx} — Lock을 사용하는 객체에서 명시적 unlock() 없이 외부 콜백({line.strip()})을 동기 호출하면 재진입 데드락이 발생합니다."
                )

# 4 & 5. 잠금/세션 모듈(Lock 또는 Session)이 존재할 때의 보안 불변식 검사
lock_dir_candidates = [
    os.path.join(domain_sources_dir, "Lock"),
    os.path.join(domain_sources_dir, "Session"),
]
existing_lock_dirs = [d for d in lock_dir_candidates if os.path.isdir(d)]

for lock_dir in existing_lock_dirs:
    lock_files = sorted(glob.glob(os.path.join(lock_dir, "*.swift")))
    combined = ""
    for path in lock_files:
        with open(path, encoding="utf-8") as f:
            combined += "\n" + strip_comments(f.read())

    if "memset_s(" not in combined:
        violations.append(
            f"[규칙4: 메모리 보안 위반] {rel(lock_dir)} — 잠금 모듈에 C11 memset_s() 기반 민감 키 제로화 구현이 없습니다."
        )
    if "LocalizedError" not in combined or "errorDescription" not in combined:
        violations.append(
            f"[규칙4: 정책 문구 계약 위반] {rel(lock_dir)} — 잠금 에러 타입이 LocalizedError(errorDescription)를 구현하지 않아 SEC-003 표준 사용자 문구가 누락되었습니다."
        )

    for path in lock_files:
        with open(path, encoding="utf-8") as f:
            lines = f.read().splitlines()
        for idx, line in enumerate(lines, start=1):
            code = re.sub(r"//.*$", "", line)
            if "func unlockWithBiometrics" in code:
                # 함수 시그니처 전체(최대 6줄)를 합쳐서 검사
                sig_chunk = " ".join(
                    re.sub(r"//.*$", "", lines[j]).strip()
                    for j in range(idx - 1, min(idx + 5, len(lines)))
                )
                sig_only = sig_chunk.split("{")[0]
                has_non_optional_key = bool(re.search(r"Key\s*:\s*Data\b(?!\s*\?)", sig_only))
                has_default_nil = "= nil" in sig_only
                if not has_non_optional_key or has_default_nil:
                    violations.append(
                        f"[규칙5: 생체 복호화 우회 금지] {rel(path)}:{idx} — unlockWithBiometrics는 반드시 비옵셔널 복호화 키(Key: Data)를 인자로 받아 볼트 복호화를 검증해야 합니다."
                    )

if violations:
    print("Apple 정적 아키텍처·보안·스텁 린트 위반 발견:")
    for v in violations:
        print(f"  ✖ {v}")
    sys.exit(1)

print("✔ Apple 정적 아키텍처·보안·스텁 린트 통과 (위반 0건)")
PY
