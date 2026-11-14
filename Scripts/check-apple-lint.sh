#!/usr/bin/env bash
# Apple 플랫폼 정적 아키텍처·보안·스텁 린트 (Hard Constraint Gate)
#
# 범용 Clean Architecture·스텁·동시성 린트(check-swift-architecture.sh)와
# KeyManage 전용 도메인 보안 불변식(규칙 4~5)을 모듈화하여 순차 실행한다.
#
# 검사 항목:
#   [범용 규칙 — Scripts/check-swift-architecture.sh 위임]
#   2. 스텁 및 미완성 코드 잔존 금지: 프로덕션 Sources 내 case notImplemented, fatalError( (init(coder:) 제외), TODO:, FIXME: 금지
#   3. 비재진입 락(NSLock/NSRecursiveLock) 보유 중 외부 콜백 동기 호출 데드락 패턴 차단
#
#   [KeyManage 도메인 전용 규칙 — 본 스크립트 수행]
#   4. Swift는 뷰·진입점·DesignSystem만 둔다 (domain-migration-plan 12.3 4단계). 도메인·RIB은 공유 Kotlin(KeyManageShared)에 있다.
#      - Apps/Apple/Projects 아래 모듈은 App, Core/DesignSystem만 허용
#      - 옛 Swift RIB 프레임워크(MicroRIBs)·옛 Swift 모듈(KeyManageDomain, KeyManageData, KeyManageUIAdapter, *FeatureInterface/Implementation) import 금지
#   (옛 규칙 4 "Swift 잠금 모듈 memset_s·LocalizedError", 5 "unlockWithBiometrics 키 인자"는 Swift 도메인을 지워 뺐다.
#    키 지움·생체 해제 키 규칙은 Kotlin :domain·:feature 테스트(VaultSessionUnlockKeyWipeTest, UnlockBiometricInteractorTest)가 확인한다)

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

# 4. Swift 계층 경계 정적 린트 실행
PYTHONIOENCODING=utf-8 "$PYTHON" - "$ROOT_DIR" <<'PY'
import os
import re
import sys

for stream in (sys.stdout, sys.stderr):
    if hasattr(stream, "reconfigure"):
        stream.reconfigure(encoding="utf-8")

root = sys.argv[1]
projects_dir = os.path.join(root, "Apps", "Apple", "Projects")
ALLOWED_MODULES = {"App", "Core/DesignSystem"}
SKIP_DIRS = {".build", "build", "Derived", "DerivedData", "checkouts"}
FORBIDDEN_IMPORT = re.compile(
    r"^\s*(@testable\s+)?import\s+(MicroRIBs|KeyManageDomain|KeyManageData|KeyManageUIAdapter|[A-Za-z]+Feature(Interface|Implementation|Testing))\b"
)

violations = []

def rel(path):
    return os.path.relpath(path, root).replace(os.sep, "/")

modules = set()
for group in sorted(os.listdir(projects_dir)):
    group_dir = os.path.join(projects_dir, group)
    if not os.path.isdir(group_dir):
        continue
    if os.path.isfile(os.path.join(group_dir, "Project.swift")):
        modules.add(group)
        continue
    for child in sorted(os.listdir(group_dir)):
        if os.path.isfile(os.path.join(group_dir, child, "Project.swift")):
            modules.add(f"{group}/{child}")

for module in sorted(modules - ALLOWED_MODULES):
    violations.append(
        f"[규칙4: Swift 계층 경계] Apps/Apple/Projects/{module} — Swift 모듈은 App, Core/DesignSystem만 둡니다. 도메인·RIB은 공유 Kotlin에 둡니다."
    )

for current_root, dirs, files in os.walk(projects_dir):
    dirs[:] = sorted(d for d in dirs if d not in SKIP_DIRS)
    for fname in sorted(files):
        if not fname.endswith(".swift"):
            continue
        path = os.path.join(current_root, fname)
        with open(path, encoding="utf-8") as f:
            for idx, line in enumerate(f.read().splitlines(), start=1):
                m = FORBIDDEN_IMPORT.match(line)
                if m:
                    violations.append(
                        f"[규칙4: Swift 계층 경계] {rel(path)}:{idx} — 옛 Swift 모듈 '{m.group(2)}'을 import할 수 없습니다."
                    )

if violations:
    print("KeyManage Swift 계층 경계 린트 위반 발견:")
    for v in violations:
        print(f"  ✖ {v}")
    sys.exit(1)

print("✔ KeyManage Swift 계층 경계 린트 통과 (위반 0건)")
PY
