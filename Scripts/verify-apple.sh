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

# 3. 멀티 플랫폼 정책 합격 기준·API 계약 감사 + TDD 무결성 검증
echo ">> [3/6] 멀티 플랫폼 정책 합격 기준·API 계약 감사 및 TDD 테스트 불변성 검증..."
bash "${ROOT_DIR}/Scripts/check-policy-acceptance.sh" "${ROOT_DIR}"
bash "${ROOT_DIR}/Scripts/check-api-contract.sh" "${ROOT_DIR}"
bash "${ROOT_DIR}/Scripts/verify-tdd-integrity.sh"

# 4. Tuist 외부 의존성 설치 및 프로젝트 생성
echo ">> [4/6] 공유 Kotlin XCFramework 생성, Tuist 의존성 확인 및 프로젝트 생성..."
# KeyManageIOS·KeyManageMac이 참조하는 KeyManageShared.xcframework를 tuist generate 전에 만든다 (domain-migration-plan 12.2)
# Gradle·Java가 실패하면 예전 XCFramework로 빌드되지 않도록 여기서 멈춘다
if [ -z "${JAVA_HOME:-}" ]; then
  if JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null)"; then
    export JAVA_HOME
  else
    echo "오류: JAVA_HOME이 없고 /usr/libexec/java_home -v 21로도 JDK 21을 찾지 못했습니다." >&2
    exit 2
  fi
fi
if ! (cd "${ROOT_DIR}/Apps/Shared" && ./gradlew :apple-app:assembleKeyManageSharedDebugXCFramework); then
  echo "오류: KeyManageShared XCFramework 생성에 실패했습니다." >&2
  exit 1
fi
cd "${APPLE_DIR}"
"${TUIST_BIN}" install
"${TUIST_BIN}" generate --no-open

# 5. 두 앱 빌드 검증 (Swift 6 Strict Concurrency 포함)
echo ">> [5/6] KeyManageMac·KeyManageIOS 빌드 검증..."
xcodebuild -workspace KeyManageApple.xcworkspace \
  -scheme KeyManageMac \
  -destination 'platform=macOS' \
  build CODE_SIGNING_ALLOWED=NO -quiet

# KeyManageShared에는 iosSimulatorArm64 조각만 있어 시뮬레이터 빌드는 arm64로만 한다
xcodebuild -workspace KeyManageApple.xcworkspace \
  -scheme KeyManageIOS \
  -destination 'generic/platform=iOS Simulator' \
  build CODE_SIGNING_ALLOWED=NO ARCHS=arm64 -quiet

# 6. Swift 단위 테스트. 남은 Swift 모듈은 DesignSystem뿐이다.
# 도메인·RIB 행동은 Apps/Shared의 :domain·:feature·:apple-app Kotlin 테스트가 확인한다 (domain-migration-plan 12.3 4단계)
echo ">> [6/6] KeyManageDesignSystem 단위 테스트 실행..."
xcodebuild -workspace KeyManageApple.xcworkspace \
  -scheme KeyManageDesignSystem \
  -destination 'platform=macOS' \
  test CODE_SIGNING_ALLOWED=NO -quiet

echo "=========================================="
echo " ✔ 모든 Apple 플랫폼 6중 기계적 검증 통과!"
echo "=========================================="
