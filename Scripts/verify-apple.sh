#!/usr/bin/env bash
set -euo pipefail

echo "=========================================="
echo " [Apple 플랫폼 6중 기계적 통합 검증 시작]"
echo "=========================================="

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
APPLE_DIR="${ROOT_DIR}/Apps/Apple"

if [ -n "${TUIST_BIN:-}" ] && [ -x "${TUIST_BIN}" ]; then
  :
elif [ -x "${HOME}/.local/share/mise/installs/tuist/4.191.5/tuist" ]; then
  TUIST_BIN="${HOME}/.local/share/mise/installs/tuist/4.191.5/tuist"
elif command -v tuist >/dev/null 2>&1; then
  TUIST_BIN="$(command -v tuist)"
else
  echo "오류: 실행 가능한 tuist 바이너리를 찾을 수 없습니다." >&2
  exit 2
fi

# 1. 문서 거버넌스 및 링크 무결성 검증
echo ">> [1/6] 문서 거버넌스 및 ADR 무결성 검증..."
bash "${ROOT_DIR}/Scripts/check-docs.sh"

# 2. Apple 정적 아키텍처·보안·스텁 린트 검증 (Hard Constraints)
echo ">> [2/6] Apple 정적 아키텍처·보안·스텁 린트 검증..."
bash "${ROOT_DIR}/Scripts/check-apple-lint.sh" "${ROOT_DIR}"

# 3. 정책 합격 기준 및 경계값 어설션 감사 + TDD 무결성 검증
echo ">> [3/6] 정책 합격 기준 감사 및 TDD 테스트 불변성 검증..."
bash "${ROOT_DIR}/Scripts/check-policy-acceptance.sh" "${ROOT_DIR}"
bash "${ROOT_DIR}/Scripts/verify-tdd-integrity.sh"

# 4. Tuist 프로젝트 생성
echo ">> [4/6] Tuist 프로젝트 생성..."
cd "${APPLE_DIR}"
"${TUIST_BIN}" generate --no-open

# 5. macOS 빌드 검증 (Swift 6 Strict Concurrency 포함)
echo ">> [5/6] KeyManageMac 빌드 검증..."
xcodebuild -workspace KeyManageApple.xcworkspace \
  -scheme KeyManageMac \
  -destination 'platform=macOS' \
  build CODE_SIGNING_ALLOWED=NO -quiet

# 6. 단위 테스트 전수 검증
echo ">> [6/6] Apple 타깃 단위 테스트 실행..."
xcodebuild -workspace KeyManageApple.xcworkspace \
  -scheme KeyManageDomain \
  -destination 'platform=macOS' \
  test CODE_SIGNING_ALLOWED=NO -quiet

xcodebuild -workspace KeyManageApple.xcworkspace \
  -scheme KeyManageData \
  -destination 'platform=macOS' \
  test CODE_SIGNING_ALLOWED=NO -quiet

xcodebuild -workspace KeyManageApple.xcworkspace \
  -scheme UnlockFeature \
  -destination 'platform=macOS' \
  test CODE_SIGNING_ALLOWED=NO -quiet

xcodebuild -workspace KeyManageApple.xcworkspace \
  -scheme KeyManageDesignSystem \
  -destination 'platform=macOS' \
  test CODE_SIGNING_ALLOWED=NO -quiet

xcodebuild -workspace KeyManageApple.xcworkspace \
  -scheme KeyManageUIAdapter \
  -destination 'platform=macOS' \
  test CODE_SIGNING_ALLOWED=NO -quiet

echo "=========================================="
echo " ✔ 모든 Apple 플랫폼 6중 기계적 검증 통과!"
echo "=========================================="
