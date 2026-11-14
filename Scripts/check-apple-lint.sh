#!/usr/bin/env bash
# Apple 플랫폼 정적 아키텍처·보안·스텁 린트 (Hard Constraint Gate)
#
# 범용 Clean Architecture·스텁·동시성 린트(check-swift-architecture.sh)와
# KeyManage 전용 도메인 보안 불변식(규칙 4~5)을 모듈화하여 순차 실행한다.
#
# 검사 항목:
#   [범용 규칙 — Scripts/check-swift-architecture.sh 위임]
#   1. Clean Architecture 도메인 격리: Domain/**/Sources 내 UI/플랫폼 프레임워크 import 금지
#   2. 스텁 및 미완성 코드 잔존 금지: 프로덕션 Sources 내 case notImplemented, fatalError( (init(coder:) 제외), TODO:, FIXME: 금지
#   3. 비재진입 락(NSLock/NSRecursiveLock) 보유 중 외부 콜백 동기 호출 데드락 패턴 차단
#
#   [KeyManage 도메인 전용 규칙 — 본 스크립트 수행]
#   4. 잠금/세션 모듈 보안 불변식: Lock/ 또는 Session/ 존재 시 memset_s 제로화 및 LocalizedError 표준 문구 구현 필수
#   5. 생체 인증 키 복호화 우회 차단: unlockWithBiometrics 시그니처에 복호화 키(*Key: Data) 비옵셔널 강제

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="${1:-$(cd "${SCRIPT_DIR}/.." && pwd)}"
APPLE_PROJECTS_DIR="${ROOT_DIR}/Apps/Apple/Projects"

if [ ! -d "${APPLE_PROJECTS_DIR}" ]; then
  echo "오류: ${APPLE_PROJECTS_DIR} 디렉터리를 찾을 수 없습니다." >&2
  exit 2
fi

# 1~3. 범용 Clean Architecture·스텁·동시성 정적 린트 실행
bash "${SCRIPT_DIR}/check-swift-architecture.sh" "${APPLE_PROJECTS_DIR}"

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

# 4~5. KeyManage 도메인 보안 불변식 정적 린트 실행
PY_ROOT_DIR="$(cygpath -m "${ROOT_DIR}" 2>/dev/null || printf "%s" "${ROOT_DIR}")"
PYTHONIOENCODING=utf-8 "$PYTHON" - "${PY_ROOT_DIR}" <<'PY'
import glob
import os
import re
import sys

for stream in (sys.stdout, sys.stderr):
    if hasattr(stream, "reconfigure"):
        stream.reconfigure(encoding="utf-8")

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

# 4 & 5. 잠금/세션 모듈(Lock 또는 Session)이 존재할 때의 KeyManage 보안 불변식 검사
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
                sig_chunk = " ".join(
                    re.sub(r"//.*$", "", lines[j]).strip()
                    for j in range(idx - 1, min(idx + 5, len(lines)))
                )
                sig_only = sig_chunk.split("{")[0]
                has_non_optional_key = bool(
                    re.search(r"[A-Za-z0-9_]*key\s*:\s*Data\b(?!\s*\?)", sig_only, re.IGNORECASE)
                )
                has_default_nil = "= nil" in sig_only
                if not has_non_optional_key or has_default_nil:
                    violations.append(
                        f"[규칙5: 생체 복호화 우회 금지] {rel(path)}:{idx} — unlockWithBiometrics는 반드시 비옵셔널 복호화 키(*Key: Data)를 인자로 받아 볼트 복호화를 검증해야 합니다."
                    )

# 6. 디자인 토큰 리터럴 금지: Feature 화면 코드는 간격·모서리 숫자를 직접 쓰지 않는다 (ADR-0025 결정 7)
literal_patterns = [
    re.compile(r"(?<![A-Za-z])spacing:\s*[1-9][0-9.]*"),
    re.compile(r"\.padding\(\s*(?:\.[A-Za-z]+\s*,\s*)?[0-9][0-9.]*\s*\)"),
    re.compile(r"(?<![A-Za-z])cornerRadius(?::\s*|\(\s*)[0-9][0-9.]*"),
]
features_dir = os.path.join(projects_dir, "Features")
for path in glob.glob(os.path.join(features_dir, "**", "*.swift"), recursive=True):
    norm = path.replace(os.sep, "/")
    if "/Tests/" in norm or "/Testing/" in norm:
        continue
    with open(path, encoding="utf-8") as f:
        lines = strip_comments(f.read()).splitlines()
    for idx, line in enumerate(lines, start=1):
        if any(p.search(line) for p in literal_patterns):
            violations.append(
                f"[규칙6: 디자인 토큰 리터럴 금지] {rel(path)}:{idx} — 간격·모서리 숫자 대신 DesignToken.Spacing·DesignToken.Radius를 쓰세요."
            )

if violations:
    print("KeyManage 도메인 보안 불변식 린트 위반 발견:")
    for v in violations:
        print(f"  ✖ {v}")
    sys.exit(1)

print("✔ KeyManage 도메인 보안 불변식 린트 통과 (위반 0건)")
PY
