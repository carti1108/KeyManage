#!/usr/bin/env bash
set -euo pipefail

echo "=========================================="
echo " [Apple 플랫폼 4중 기계적 통합 검증 시작]"
echo "=========================================="

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
APPLE_DIR="${ROOT_DIR}/Apps/Apple"
TUIST_BIN="${TUIST_BIN:-/Users/carti/.local/share/mise/installs/tuist/4.191.5/tuist}"

# 1. 문서 거버넌스 및 링크 무결성 검증
echo ">> [1/4] 문서 거버넌스 및 ADR 무결성 검증..."
bash "${ROOT_DIR}/Scripts/check-docs.sh"

# 2. Tuist 프로젝트 생성
echo ">> [2/4] Tuist 프로젝트 생성..."
cd "${APPLE_DIR}"
"${TUIST_BIN}" generate --no-open

# 3. macOS 빌드 검증 (Swift 6 Strict Concurrency 포함)
echo ">> [3/4] KeyManageMac 빌드 검증..."
xcodebuild -workspace KeyManageApple.xcworkspace \
  -scheme KeyManageMac \
  -destination 'platform=macOS' \
  build CODE_SIGNING_ALLOWED=NO -quiet

# 4. 단위 테스트 전수 검증
echo ">> [4/4] Apple 타깃 단위 테스트 실행..."
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
echo " ✔ 모든 Apple 플랫폼 기계적 검증 통과!"
echo "=========================================="
