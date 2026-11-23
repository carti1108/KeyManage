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

# 4. 공유 Kotlin 묶음 프레임워크(운영용·테스트용)와 Tuist 프로젝트 생성
#    Apps/Shared는 carti-RIBs-KMP를 형제 폴더 또는 CARTI_RIBS_DIR에서 가져온다
echo ">> [4/6] 공유 Kotlin 묶음 프레임워크 빌드·Apple 포트 테스트, Tuist 프로젝트 생성..."
(
  cd "${ROOT_DIR}/Apps/Shared"
  ./gradlew --console=plain -q \
    :apple-session:macosArm64Test \
    :apple-session:assembleKeyManageSharedDebugXCFramework \
    :apple-example:assembleKeyManageSharedDebugXCFramework
)
cd "${APPLE_DIR}"
"${TUIST_BIN}" install
"${TUIST_BIN}" generate --no-open

# 5. iOS·macOS 앱 빌드 검증 (Swift 6 Strict Concurrency 포함)
echo ">> [5/6] KeyManageMac·KeyManageIOS 빌드 검증..."
xcodebuild -workspace KeyManageApple.xcworkspace \
  -scheme KeyManageMac \
  -destination 'platform=macOS' \
  build CODE_SIGNING_ALLOWED=NO -quiet

xcodebuild -workspace KeyManageApple.xcworkspace \
  -scheme KeyManageIOS \
  -destination 'generic/platform=iOS Simulator' \
  build CODE_SIGNING_ALLOWED=NO ARCHS=arm64 -quiet  # 공유 Kotlin 묶음은 arm64 시뮬레이터만 낸다

# 6. 단위 테스트: 디자인 시스템(macOS), 피처 뷰 테스트(iOS 시뮬레이터)
#    앱 UI 테스트(가입→잠금 해제)는 로컬 백엔드가 필요해 여기서 돌리지 않는다. 백엔드를 띄우고 KeyManageIOS 스킴의 test로 돌린다
echo ">> [6/6] Apple 단위 테스트·피처 뷰 테스트 실행..."
xcodebuild -workspace KeyManageApple.xcworkspace \
  -scheme KeyManageDesignSystem \
  -destination 'platform=macOS' \
  test CODE_SIGNING_ALLOWED=NO -quiet

SIMULATOR_ID="${APPLE_SIMULATOR_ID:-$(xcrun simctl list devices available -j | python3 -c '
import json, sys
devices = json.load(sys.stdin)["devices"]
ids = [d["udid"] for runtime, items in sorted(devices.items(), reverse=True) if "iOS" in runtime for d in items if d["name"].startswith("iPhone")]
print(ids[0] if ids else "")
')}"
if [ -z "${SIMULATOR_ID}" ]; then
  echo "오류: 피처 뷰 테스트를 돌릴 iPhone 시뮬레이터가 없습니다." >&2
  exit 2
fi
for feature in Unlock Auth PasswordGenerator VaultList Settings Main; do
  xcodebuild -workspace KeyManageApple.xcworkspace \
    -scheme "${feature}ViewTests" \
    -destination "id=${SIMULATOR_ID}" \
    test CODE_SIGNING_ALLOWED=NO -quiet
done

echo "=========================================="
echo " ✔ 모든 Apple 플랫폼 6중 기계적 검증 통과!"
echo "=========================================="
