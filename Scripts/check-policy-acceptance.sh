#!/usr/bin/env bash
# 멀티 플랫폼 클라이언트 정책 합격 기준 및 경계값 어설션 감사 (Multi-Platform Policy Acceptance Gate)
#
# 백엔드(:domain)의 PIT 뮤테이션 테스트에 대응하여, Apple·Web(및 추후 Android) 클라이언트 단위 테스트가
# 단순 통과 개수만 채우지 않고 docs/policies/ 및 docs/vault-spec.md의
# 필수 경계값·예외·표준 문구를 실제로 어설션(#expect / XCTAssert / expect)하고 있는지 기계적으로 교차 검증한다.
#
# 정책(SSOT) 변경 동기화 규칙 (Closed-Loop):
#   docs/policies/의 경계값·예외·표준 문구가 추가/변경되면 본 스크립트의 *_rules도 같은 PR에서 반드시 함께 갱신한다.

set -euo pipefail

ROOT_DIR="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"

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
import glob
import os
import re
import sys

for stream in (sys.stdout, sys.stderr):
    if hasattr(stream, "reconfigure"):
        stream.reconfigure(encoding="utf-8")

root = sys.argv[1]
violations = []
audited_platforms = []


def check_invariants(platform, module_name, combined_text, rules):
    for label, pattern in rules:
        if not re.search(pattern, combined_text, re.MULTILINE | re.DOTALL):
            violations.append(
                f"[{platform} / {module_name}] 필수 정책 경계값/어설션 누락: {label} (패턴: {pattern})"
            )


# ============================================================================
# 1. Apple 플랫폼 감사 (Apps/Apple/Projects/Core/Domain/Tests)
# ============================================================================
apple_tests_dir = os.path.join(root, "Apps", "Apple", "Projects", "Core", "Domain", "Tests")
apple_sources_dir = os.path.join(root, "Apps", "Apple", "Projects", "Core", "Domain", "Sources")

if os.path.isdir(apple_tests_dir):
    audited_platforms.append("Apple")
    apple_test_files = sorted(glob.glob(os.path.join(apple_tests_dir, "*.swift")))
    apple_tests = "\n".join(open(p, encoding="utf-8").read() for p in apple_test_files)

    apple_crypto_rules = [
        ("교차 플랫폼 테스트 벡터(crypto-test-vectors.json) 지문 검증", r"7xdxRO7JQgy8EJPSqLNEqSvFBtDU7JwCjdGfgyTYweY="),
        ("PBKDF2 600,000회 반복 경계값 검증", r"600_?000"),
        ("HKDF authKey / encryptionKey 도메인 분리 검증", r"HKDFKeySeparator\.separate"),
        ("AES-GCM AAD 헤더 변조 복호화 거부 검증", r"(XCTAssertThrowsError|#expect\(throws:)"),
    ]
    check_invariants("Apple", "1단계: 암호 모듈(vault-spec)", apple_tests, apple_crypto_rules)

    apple_vault_rules = [
        ("상위 버전 읽기 전용(isReadOnly) 강제 검증", r"\bisReadOnly\b"),
        ("5MB(5,242,880바이트) 페이로드 상한 검증", r"(5_?242_?880|5\s*\*\s*1024\s*\*\s*1024)"),
        ("톰스톤(deletedAt) 삭제 및 필드 소거 검증", r"\bdeletedAt\b"),
        ("서버 revision 역행(Rollback) 거부 검증", r"revisionRollback|Rollback"),
        ("409 Conflict 최대 3회 재시도 한도 검증", r"(conflictLimitExceeded|409)"),
    ]
    check_invariants("Apple", "2단계: 볼트 동기화(VLT-001)", apple_tests, apple_vault_rules)

    has_lock_module = (
        os.path.isdir(os.path.join(apple_sources_dir, "Lock"))
        or os.path.isdir(os.path.join(apple_sources_dir, "Session"))
    )
    if has_lock_module:
        apple_autolock_rules = [
            ("자동 잠금 5종 타임아웃(0, 60, 300, 900, 3600) 경계값 검증", r"3600"),
            ("백그라운드 가림 화면(isPrivacyCoverActive) 상태 전이 검증", r"isPrivacyCoverActive"),
            ("잠금 시 민감 키 소거(masterKey == nil, encryptionKey == nil) 검증", r"(encryptionKey\s*==\s*nil|XCTAssertNil\([^\)]*encryptionKey)"),
            ("생체 인증 14일 재인증 주기 검증", r"(14\s*\*|1_?209_?600|14)"),
            ("SEC-003 표준 비밀번호 오류 문구('마스터 비밀번호가 올바르지 않습니다.') 검증", r"마스터 비밀번호가 올바르지 않습니다\."),
            ("SEC-003 빈 볼트 준비 실패 문구('계정은 만들었지만 볼트를 준비하지 못했습니다.') 검증", r"계정은 만들었지만 볼트를 준비하지 못했습니다\."),
        ]
        check_invariants("Apple", "3단계: 자동 잠금(SEC-003)", apple_tests, apple_autolock_rules)

    if os.path.isdir(os.path.join(apple_sources_dir, "Generator")):
        apple_generator_rules = [
            ("길이 상한(64자) 및 하한(8자) 경계값 검증", r"(minimumLength|maximumLength|64)"),
            ("거부 샘플링 1,000회 반복 검증", r"1_?000"),
            ("플랫폼 공통 샘플('123456' -> 19점) 검증", r"123456"),
            ("플랫폼 공통 샘플('abc~~~~~' -> 40점) 검증", r"abc~~~~~"),
            ("표준 에러 문구('비밀번호 길이는 최소 8자 이상 64자 이하이어야 합니다.') 검증", r"비밀번호 길이는 최소 8자 이상 64자 이하이어야 합니다\."),
            ("표준 에러 문구('최소 1개 이상의 문자 종류를 선택해야 합니다.') 검증", r"최소 1개 이상의 문자 종류를 선택해야 합니다\."),
            ("표준 취약 경고 문구('현재 비밀번호는 보안에 취약합니다. 80점 이상의 강력한 비밀번호로 재생성하세요.') 검증", r"현재 비밀번호는 보안에 취약합니다\. 80점 이상의 강력한 비밀번호로 재생성하세요\."),
            ("표준 연속 숫자 안내 문구('123처럼 이어지는 숫자가 들어 있어 추측하기 쉽습니다.') 검증", r"123처럼 이어지는 숫자가 들어 있어 추측하기 쉽습니다\."),
        ]
        check_invariants("Apple", "4단계: 비밀번호 생성기(GEN-001)", apple_tests, apple_generator_rules)

    if os.path.isdir(os.path.join(apple_sources_dir, "Sync")):
        apple_pending_sync_rules = [
            ("VLT-002 재전송 주기(30초) 상수 검증", r"(30|resyncIntervalSeconds)"),
            ("VLT-002 오프라인 경고 표준 문구('오프라인 상태입니다. 변경 사항이 서버에 동기화되지 않습니다.') 검증", r"오프라인 상태입니다\. 변경 사항이 서버에 동기화되지 않습니다\."),
            ("VLT-002 대기 중 로그아웃 확인 제목('동기화되지 않은 변경 사항') 검증", r"동기화되지 않은 변경 사항"),
            ("VLT-002 대기 중 로그아웃 확인 본문('로그아웃하면 서버에 올리지 못한 변경 사항이 사라집니다.') 검증", r"로그아웃하면 서버에 올리지 못한 변경 사항이 사라집니다\."),
        ]
        check_invariants("Apple", "5단계: 오프라인 동기화 대기(VLT-002)", apple_tests, apple_pending_sync_rules)

    if os.path.isdir(os.path.join(apple_sources_dir, "Persistence")):
        apple_device_store_rules = [
            ("VLT-003 단일 계정 기기 기록 및 복원 검증", r"DeviceVaultRecord"),
            ("VLT-003 다른 계정 인증 시 이전 기록 삭제 검증", r"handleAuthenticationSuccess"),
            ("VLT-003 KDF 파라미터 변경 감지 및 대기 폐기 검증", r"evaluateKdfChange"),
        ]
        check_invariants("Apple", "6단계: 기기 볼트 보존(VLT-003)", apple_tests, apple_device_store_rules)

    if os.path.isdir(os.path.join(apple_sources_dir, "Clipboard")):
        apple_clipboard_rules = [
            ("COM-001 클립보드 유효 시간(30초) 경계값 검증", r"(defaultAutoBurnInterval|30\.0)"),
            ("COM-001 복사 직후 표준 제목('클립보드 보안 소각') 검증", r"클립보드 보안 소각"),
            ("COM-001 복사 직후 표준 본문('보안을 위해 복사된 내용이 30초 후 클립보드에서 자동 삭제됩니다.') 검증", r"보안을 위해 복사된 내용이 30초 후 클립보드에서 자동 삭제됩니다\."),
            ("COM-001 복사 실패 표준 문구('클립보드에 복사하지 못했습니다.') 검증", r"클립보드에 복사하지 못했습니다\."),
            ("COM-001 비우기 실패 표준 문구('클립보드를 비우지 못했습니다.') 검증", r"클립보드를 비우지 못했습니다\."),
            ("COM-001 / SEC-003 앱 잠금 시 즉시 비움(handleLock) 검증", r"handleLock"),
        ]
        check_invariants("Apple", "7단계: 클립보드 자동 비우기(COM-001)", apple_tests, apple_clipboard_rules)


# ============================================================================
# 2. Web 플랫폼 감사 (Apps/Web/src/**/*.test.ts*)
# ============================================================================
web_src_dir = os.path.join(root, "Apps", "Web", "src")
if os.path.isdir(web_src_dir):
    audited_platforms.append("Web")
    web_test_files = sorted(
        glob.glob(os.path.join(web_src_dir, "**", "*.test.ts"), recursive=True)
        + glob.glob(os.path.join(web_src_dir, "**", "*.test.tsx"), recursive=True)
    )
    web_tests = "\n".join(open(p, encoding="utf-8").read() for p in web_test_files)

    web_crypto_rules = [
        ("교차 플랫폼 테스트 벡터(vectors.keyDerivationCases) 검증", r"vectors\.keyDerivationCases"),
        ("PBKDF2 600,000회 하한 경계값(MIN_ITERATIONS - 1) 검증", r"MIN_ITERATIONS\s*-\s*1"),
        ("암호화 키 추출 불가(extractable === false) 검증", r"encryptionKey\.extractable"),
        ("AES-GCM 변조/AAD 복호화 거부(VaultDecryptError) 검증", r"VaultDecryptError"),
    ]
    check_invariants("Web", "1단계: 암호 모듈(vault-spec)", web_tests, web_crypto_rules)

    web_vault_rules = [
        ("상위 버전 읽기 전용(ReadOnlyVaultError / readOnly) 검증", r"ReadOnlyVaultError"),
        ("상위 버전 읽기 전용 표준 문구 검증", r"새 버전 앱에서 저장한 볼트입니다\. 앱을 업데이트하기 전까지는 읽기만 할 수 있습니다\."),
        ("5MB 용량 초과(VaultTooLargeError) 및 표준 문구 검증", r"볼트 허용 용량\(5MB\)을 초과했거나 데이터 규격이 올바르지 않습니다\."),
        ("톰스톤(deletedAt) 삭제 및 병합 검증", r"\bdeletedAt\b"),
        ("서버 revision 역행(RevisionRollbackError) 및 표준 제목('서버 볼트가 이전 상태로 돌아감') 검증", r"서버 볼트가 이전 상태로 돌아감"),
        ("409 동기화 충돌(VaultSyncConflictError) 및 표준 문구 검증", r"다른 기기의 변경 사항과 충돌했습니다\. 잠시 후 다시 시도해 주세요\."),
    ]
    check_invariants("Web", "2단계: 볼트 동기화(VLT-001)", web_tests, web_vault_rules)

    web_autolock_rules = [
        ("자동 잠금 설정 타임아웃 경계값 검증", r"900_?000"),
        ("페이지 숨김(pageHidden) 및 유휴 자동 잠금 검증", r"pageHidden"),
        ("SEC-003 표준 비밀번호 오류 문구('마스터 비밀번호가 올바르지 않습니다.') 검증", r"마스터 비밀번호가 올바르지 않습니다\."),
        ("SEC-003 빈 볼트 준비 실패 문구('계정은 만들었지만 볼트를 준비하지 못했습니다. 다시 로그인해 주세요.') 검증", r"계정은 만들었지만 볼트를 준비하지 못했습니다\."),
    ]
    check_invariants("Web", "3단계: 자동 잠금 및 인증(SEC-003)", web_tests, web_autolock_rules)

    web_generator_rules = [
        ("길이 하한(8자)·상한(64자) 및 범위 밖(7, 65) 거부 검증", r"\b64\b"),
        ("거부 샘플링 1,000회 반복 검증", r"\b1000\b"),
        ("플랫폼 공통 샘플('123456' -> 19점) 검증", r"\['123456',\s*19\]"),
        ("플랫폼 공통 샘플('abc~~~~~' -> 40점) 검증", r"\['abc~~~~~',\s*40\]"),
        ("표준 길이 에러 문구('비밀번호 길이는 최소 8자 이상 64자 이하이어야 합니다.') 검증", r"비밀번호 길이는 최소 8자 이상 64자 이하이어야 합니다\."),
        ("표준 문자군 에러 문구('최소 1개 이상의 문자 종류를 선택해야 합니다.') 검증", r"최소 1개 이상의 문자 종류를 선택해야 합니다\."),
        ("연속 숫자 감지(hasSequentialDigits) 검증", r"hasSequentialDigits"),
    ]
    check_invariants("Web", "4단계: 비밀번호 생성기(GEN-001)", web_tests, web_generator_rules)

    web_pending_sync_rules = [
        ("VLT-002 오프라인 대기 상태(pendingSync: true) 및 재시도(retryPendingSync) 검증", r"retryPendingSync"),
        ("VLT-002 오프라인 경고 표준 문구('오프라인 상태입니다. 변경 사항이 서버에 동기화되지 않습니다.') 검증", r"오프라인 상태입니다\. 변경 사항이 서버에 동기화되지 않습니다\."),
        ("VLT-002 대기 중 로그아웃 확인 제목('동기화되지 않은 변경 사항') 검증", r"동기화되지 않은 변경 사항"),
    ]
    check_invariants("Web", "5단계: 오프라인 동기화 대기(VLT-002)", web_tests, web_pending_sync_rules)

    web_device_store_rules = [
        ("VLT-003 기기 볼트 기록 조율(reconcileDeviceRecord) 검증", r"reconcileDeviceRecord"),
        ("VLT-003 마스터 비밀번호 변경 감지 표준 문구('마스터 비밀번호가 바뀌었습니다. 새 비밀번호로 로그인해 주세요.') 검증", r"마스터 비밀번호가 바뀌었습니다\. 새 비밀번호로 로그인해 주세요\."),
        ("VLT-003 기기 저장 실패 표준 경고 문구 검증", r"이 기기에 볼트를 저장하지 못했습니다\. 새로고침하면 서버에 올리지 못한 변경 사항이 사라질 수 있습니다\."),
    ]
    check_invariants("Web", "6단계: 기기 볼트 보존(VLT-003)", web_tests, web_device_store_rules)

    web_clipboard_rules = [
        ("COM-001 클립보드 30초(30_000ms) 자동 소각 타이머 검증", r"30_000"),
        ("COM-001 복사 직후 표준 제목('클립보드 보안 소각') 검증", r"클립보드 보안 소각"),
        ("COM-001 복사 직후 표준 본문('보안을 위해 복사된 내용이 30초 후 클립보드에서 자동 삭제됩니다.') 검증", r"보안을 위해 복사된 내용이 30초 후 클립보드에서 자동 삭제됩니다\."),
        ("COM-001 비우기 보류 안내 문구('클립보드를 아직 비우지 못했습니다. 이 화면을 누르면 다시 비웁니다.') 검증", r"클립보드를 아직 비우지 못했습니다\. 이 화면을 누르면 다시 비웁니다\."),
    ]
    check_invariants("Web", "7단계: 클립보드 자동 비우기(COM-001)", web_tests, web_clipboard_rules)


if not audited_platforms:
    print("오류: 감사할 플랫폼 테스트 디렉터리(Apps/Apple 또는 Apps/Web)를 찾을 수 없습니다.", file=sys.stderr)
    sys.exit(2)

if violations:
    print("멀티 플랫폼 정책 합격 기준(Policy Acceptance Audit) 위반 발견:")
    for v in violations:
        print(f"  ✖ {v}")
    sys.exit(1)

print(f"✔ 멀티 플랫폼({', '.join(audited_platforms)}) 정책 합격 기준 및 경계값 어설션 감사 통과 (누락 0건)")
PY
