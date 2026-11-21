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
# 2. Web 플랫폼 감사 (Apps/Web/src/**/*.test.ts*, Apps/Shared/**/src/*Test/**/*.kt)
# ============================================================================
web_src_dir = os.path.join(root, "Apps", "Web", "src")
if os.path.isdir(web_src_dir):
    audited_platforms.append("Web")
    web_test_files = sorted(
        glob.glob(os.path.join(web_src_dir, "**", "*.test.ts"), recursive=True)
        + glob.glob(os.path.join(web_src_dir, "**", "*.test.tsx"), recursive=True)
        # 웹 화면 로직은 공유 RIB(Kotlin)으로 옮겼다. 그 테스트도 웹 정책 근거로 본다 (domain-migration-plan 11.4)
        + glob.glob(os.path.join(root, "Apps", "Shared", "**", "src", "*Test", "**", "*.kt"), recursive=True)
    )
    web_tests = "\n".join(open(p, encoding="utf-8").read() for p in web_test_files)

    # 웹의 암호·세션·화면 로직은 공유 Kotlin으로 옮겼으므로 아래 규칙은 TS 표기(옛 형태)와 Kotlin 표기를 모두 받는다.
    # Kotlin 오류 타입은 *Exception, 옛 TS 오류 클래스는 *Error 이름을 쓴다
    web_crypto_rules = [
        ("교차 플랫폼 테스트 벡터(vectors.keyDerivationCases) 검증", r"""vectors\.keyDerivationCases|getValue\("keyDerivationCases"\)"""),
        ("PBKDF2 600,000회 하한 경계값(MIN_ITERATIONS - 1) 검증", r"MIN_ITERATIONS\s*-\s*1"),
        # Kotlin의 암호화 키(EncryptionKey)는 WebCrypto extractable 대신, 바이트를 밖으로 내지 않고 문자열로 찍어도 내용이 나오지 않는 것으로 검증한다
        ("암호화 키 추출 불가(extractable === false 또는 EncryptionKey 바이트 비노출) 검증", r"""encryptionKey\.extractable|EncryptionKey\(\*\*\*\)"""),
        ("AES-GCM 변조/AAD 복호화 거부(VaultDecryptError / VaultDecryptException / VaultDecryptionException) 검증", r"VaultDecrypt(?:ion)?(?:Error|Exception)"),
    ]
    check_invariants("Web", "1단계: 암호 모듈(vault-spec)", web_tests, web_crypto_rules)

    web_vault_rules = [
        ("상위 버전 읽기 전용(ReadOnlyVaultError / ReadOnlyVaultException / readOnly) 검증", r"ReadOnlyVault(?:Error|Exception)"),
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

    # SEC-003 규칙 8: 세션 만료 뒤에는 생체 해제 대신 마스터 비밀번호를 요구한다. 규칙은 공유 Kotlin 세션에 있어 생체 해제를 제공하는 모든 클라이언트가 따른다
    shared_biometric_rules = [
        ("SEC-003 세션 만료 뒤 생체 해제 대신 마스터 비밀번호 요구(requirePassword / PasswordRequired) 검증", r"requirePassword\(\)|BiometricError\.PasswordRequired"),
        ("SEC-003 세션 만료 뒤 감싼 키를 남기는지(isBiometricPasswordRequired) 검증", r"isBiometricPasswordRequired"),
        ("SEC-003 세션 만료 뒤 잠금 화면 표준 문구('보안을 위해 마스터 비밀번호를 입력해 주세요.') 검증", r"보안을 위해 마스터 비밀번호를 입력해 주세요\."),
        ("SEC-003 14일 경과 시 마스터 비밀번호 재요구(14.days) 검증", r"14\.days"),
    ]
    check_invariants("Web", "3단계: 생체 해제(SEC-003, 공유 Kotlin)", web_tests, shared_biometric_rules)

    # 공유 RIB이 보이는 안내 문구. 뷰만 있는 플랫폼(Android 등)은 이 문구를 그대로 그린다
    shared_notice_rules = [
        ("SEC-003 가입 직후 생체 해제 안내 제목('생체 인증으로 잠금을 풀까요?') 검증", r"생체 인증으로 잠금을 풀까요\?"),
        ("SEC-003 가입 직후에만 생체 해제를 묻는지(offerBiometricUnlock) 검증", r"offerBiometricUnlock"),
        ("COM-001 복사 직후 표준 제목('클립보드 보안 소각') 검증", r"클립보드 보안 소각"),
        ("COM-001 복사 직후 표준 본문('보안을 위해 복사된 내용이 30초 후 클립보드에서 자동 삭제됩니다.') 검증", r"보안을 위해 복사된 내용이 30초 후 클립보드에서 자동 삭제됩니다\."),
        ("COM-001 다른 텍스트를 복사하면 타이머 무효(onExternalChange) 검증", r"onExternalChange\(\)"),
    ]
    check_invariants("Web", "공유 RIB 안내 문구(SEC-003, COM-001)", web_tests, shared_notice_rules)

    web_generator_rules = [
        ("길이 하한(8자)·상한(64자) 및 범위 밖(7, 65) 거부 검증", r"\b64\b"),
        ("거부 샘플링 1,000회 반복 검증", r"\b1000\b"),
        # TS 표(['123456', 19])와 Kotlin 표("123456" to 19, assertEquals(19, scorePassword("123456")))를 모두 받는다
        ("플랫폼 공통 샘플('123456' -> 19점) 검증", r"""\['123456',\s*19\]|"123456"\s+to\s+19\b|assertEquals\(\s*19\s*,\s*scorePassword\(\s*"123456"\s*\)"""),
        ("플랫폼 공통 샘플('abc~~~~~' -> 40점) 검증", r"""\['abc~~~~~',\s*40\]|"abc~~~~~"\s+to\s+40\b|assertEquals\(\s*40\s*,\s*scorePassword\(\s*"abc~~~~~"\s*\)"""),
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

    web_backup_rules = [
        ("VLT-004 파일 이름(keymanage-backup-YYYYMMDD.json) 검증", r"keymanage-backup-"),
        ("VLT-004 백업 형식 버전(formatVersion) 검증", r"formatVersion"),
        ("VLT-004 파일 크기 한도(MAX_BACKUP_FILE_BYTES) 경계값 검증", r"MAX_BACKUP_FILE_BYTES"),
        ("VLT-004 복원 규칙(restoreItems: 백업 시점으로 되돌리고 새 항목은 남김) 검증", r"restoreItems"),
        ("VLT-004 내보내기 전 안내 문구 검증", r"이 파일은 내보낸 시점의 마스터 비밀번호로만 열 수 있습니다\."),
        ("VLT-004 복원 전 안내 문구 검증", r"백업에 있던 항목은 백업 시점 내용으로 돌아갑니다\."),
        ("VLT-004 비밀번호 오류·손상 문구('백업을 열 수 없습니다.') 검증", r"백업을 열 수 없습니다\."),
        ("VLT-004 형식 오류 문구('KeyManage 백업 파일이 아닙니다.') 검증", r"KeyManage 백업 파일이 아닙니다\."),
        ("VLT-004 새 버전 백업 문구 검증", r"더 새 버전의 앱에서 만든 백업입니다\."),
        ("VLT-004 복원 완료 문구('백업에서 항목 N개를 복원했습니다.') 검증", r"백업에서 항목 .*복원했습니다"),
    ]
    check_invariants("Web", "8단계: 암호화 백업 내보내기와 복원(VLT-004)", web_tests, web_backup_rules)

    web_password_change_rules = [
        ("SEC-004 변경 흐름(changeMasterPassword) 검증", r"changeMasterPassword"),
        ("SEC-004 새 salt 16바이트 경계값 검증", r"""kdf\.salt\)\)\.toHaveLength\(16\)|assertEquals\(\s*16\s*,\s*fromBase64\([^)]*salt\)\.size\)"""),
        ("SEC-004 새 비밀번호 SEC-002 길이 미달 거절(NewMasterPasswordTooShortError / Exception) 검증", r"NewMasterPasswordTooShort(?:Error|Exception)"),
        ("SEC-004 현재와 같음 거절(SameMasterPasswordError / Exception) 검증", r"SameMasterPassword(?:Error|Exception)"),
        ("SEC-004 오프라인 거절(PasswordChangeOfflineError / Exception) 검증", r"PasswordChangeOffline(?:Error|Exception)"),
        ("SEC-004 변경 전 안내 본문 검증", r"비밀번호를 바꾸면 다른 기기는 모두 로그아웃되고, 새 비밀번호로 다시 로그인해야 합니다\. 다른 기기에서 아직 올리지 못한 변경 사항은 사라집니다\. 예전에 내보낸 백업은 예전 비밀번호로 엽니다\."),
        ("SEC-004 현재와 같음 문구('새 비밀번호가 지금 비밀번호와 같습니다.') 검증", r"새 비밀번호가 지금 비밀번호와 같습니다\."),
        ("SEC-004 오프라인 문구('인터넷에 연결된 상태에서만 비밀번호를 바꿀 수 있습니다.') 검증", r"인터넷에 연결된 상태에서만 비밀번호를 바꿀 수 있습니다\."),
        ("SEC-004 완료 문구('마스터 비밀번호를 바꿨습니다.') 검증", r"마스터 비밀번호를 바꿨습니다\."),
    ]
    check_invariants("Web", "9단계: 마스터 비밀번호 변경(SEC-004)", web_tests, web_password_change_rules)

    web_signup_strength_rules = [
        ("SEC-002 가입 강도 게이지 59/60 경계값 검증", r"aria-valuenow', '59'[\s\S]*aria-valuenow', '60'"),
        ("SEC-002 가입 비밀번호 취약 문구 검증", r"마스터 비밀번호가 보안에 취약합니다\. 더 길게 만들거나 여러 종류의 문자를 섞어 주세요\."),
    ]
    check_invariants("Web", "10단계: 가입 강도 게이지(SEC-002)", web_tests, web_signup_strength_rules)

    web_account_deletion_rules = [
        ("SEC-005 삭제 대화상자('계정 삭제') 흐름 검증", r"getByRole\('dialog', \{ name: '계정 삭제' \}\)"),
        # 확인 문구 판정(앞뒤 공백·일치 시 활성)은 Kotlin Interactor 테스트로 옮겼다. 따옴표 관용구(TS '…', Kotlin "…")에 묶지 않고 두 형태를 모두 받는다
        ("SEC-005 확인 문구 앞뒤 공백 불일치 경계값 검증", r"""["']계정 삭제 ["'][\s\S]*["'] 계정 삭제["']"""),
        ("SEC-005 확인 문구 일치 시 삭제 버튼 활성 검증", r"""(?:fill\(dialog, \{ phrase: '계정 삭제' \}\)\s*expect\(deleteButton\(dialog\)\)\.toBeEnabled\(\)|EditDeletionPhrase\("계정 삭제"\)\)\s*assertTrue\(state\.canSubmitDeletion\))"""),
        ("SEC-005 확인 문구 NFC 정규화 경계값 검증", r"'계정 삭제'\.normalize\('NFD'\)"),
        ("SEC-005 오프라인 거절(AccountDeletionOfflineError / Exception) 검증", r"AccountDeletionOffline(?:Error|Exception)"),
        ("SEC-005 기기 기록 삭제 실패(AccountDeletedDeviceNotClearedError / Exception) 검증", r"AccountDeletedDeviceNotCleared(?:Error|Exception)"),
        ("SEC-005 삭제 전 안내 본문 검증", r"계정과 볼트가 서버에서 바로 지워지고 되돌릴 수 없습니다\. 다른 기기도 모두 로그아웃되고, 아직 올리지 못한 변경 사항은 사라집니다\. 필요하면 먼저 백업을 내보내 주세요\."),
        ("SEC-005 확인 문구 입력 칸 문구 검증", r"확인을 위해 '계정 삭제'를 입력해 주세요\."),
        ("SEC-005 비밀번호 틀림 문구('마스터 비밀번호가 올바르지 않습니다.') 검증", r"마스터 비밀번호가 올바르지 않습니다\."),
        ("SEC-005 오프라인 문구('인터넷에 연결된 상태에서만 계정을 삭제할 수 있습니다.') 검증", r"인터넷에 연결된 상태에서만 계정을 삭제할 수 있습니다\."),
        ("SEC-005 완료 문구('계정을 삭제했습니다.') 검증", r"계정을 삭제했습니다\."),
        ("SEC-005 기기 기록 삭제 실패(웹) 문구 검증", r"계정은 삭제했지만 이 기기에 남은 볼트 기록을 지우지 못했습니다\. 브라우저의 사이트 데이터를 직접 지워 주세요\."),
    ]
    check_invariants("Web", "10단계: 계정 삭제(SEC-005)", web_tests, web_account_deletion_rules)


# ============================================================================
# 3. 공유 Kotlin을 쓰는 클라이언트 감사 (Android, Apple)
#    두 앱은 뷰와 진입점만 있고 도메인·화면 로직·플랫폼 구현은 공유 Kotlin(Apps/Shared)에 있다
#    (android-migration-plan 4단계, modularization-plan F단계). 그래서 테스트 근거는 공유 모듈의 테스트(Apps/Shared/**/src/*Test),
#    표준 문구 원문은 공유 모듈과 각 앱 뷰의 소스에서 확인한다. 다른 플랫폼 전용 소스셋은 뺀다
# ============================================================================
shared_kotlin_dir = os.path.join(root, "Apps", "Shared")


def source_files(base, source_set_pattern, extension):
    return sorted(
        p for p in glob.glob(os.path.join(base, "**", "src", source_set_pattern, "**", "*." + extension), recursive=True)
        if os.sep + "build" + os.sep not in p
    )


def read_all(paths):
    return "\n".join(open(p, encoding="utf-8").read() for p in paths)


def without_source_sets(paths, excluded):
    return [p for p in paths if not any(os.sep + name + os.sep in p for name in excluded)]


def audit_shared_kotlin_client(platform, tests, sources):
    check_invariants(platform, "1단계: 암호 모듈(vault-spec)", tests, [
        ("교차 플랫폼 테스트 벡터(keyDerivationCases) 검증", r"keyDerivationCases"),
        ("PBKDF2 600,000회 하한 경계값(599_999) 검증", r"599_999"),
        ("AES-GCM 변조 복호화 거부(VaultDecryptException) 검증", r"VaultDecryptException"),
        ("다른 플랫폼과 같은 암호문 테스트 벡터(encryptionCases)를 여는 교차 검증", r"encryptionCases"),
    ])
    check_invariants(platform, "2단계: 볼트 동기화(VLT-001)", tests, [
        ("상위 버전 읽기 전용(ReadOnlyVault) 검증", r"ReadOnlyVault"),
        ("톰스톤(deletedAt) 삭제 및 병합 검증", r"\bdeletedAt\b"),
        ("서버 revision 역행(RevisionRollback) 검증", r"RevisionRollback"),
        ("사용자 확인 뒤에만 되돌림 수락(acceptRollback = true) 검증", r"acceptRollback\s*=\s*true"),
        ("409 충돌 재시도 소진(VaultSyncConflictException) 검증", r"VaultSyncConflictException"),
        ("5MB 용량 상한(MAX_PAYLOAD_BYTES) 경계값 검증", r"MAX_PAYLOAD_BYTES"),
    ])
    check_invariants(platform, "2단계: 볼트 동기화 표준 문구(VLT-001)", sources, [
        ("상위 버전 읽기 전용 표준 문구", r"새 버전 앱에서 저장한 볼트입니다\. 앱을 업데이트하기 전까지는 읽기만 할 수 있습니다\."),
        ("409 동기화 충돌 표준 문구", r"다른 기기의 변경 사항과 충돌했습니다\. 잠시 후 다시 시도해 주세요\."),
        ("5MB 용량 초과 표준 문구", r"볼트 허용 용량\(5MB\)을 초과했거나 데이터 규격이 올바르지 않습니다\."),
        ("revision 역행 표준 제목", r"서버 볼트가 이전 상태로 돌아감"),
        ("revision 역행 수락 버튼 표준 문구", r"서버 볼트 받아들이기"),
    ])
    check_invariants(platform, "3단계: 잠금 해제와 생체 해제(SEC-003)", tests, [
        ("생체 해제 14일 경계값(14.days) 검증", r"14\.days"),
        ("생체 키 무효화(BiometricError.Invalidated) 검증", r"BiometricError\.Invalidated"),
        ("14일 만료(BiometricError.Expired) 검증", r"BiometricError\.Expired"),
        ("비밀번호 오류 문구('마스터 비밀번호가 올바르지 않습니다.') 검증", r"마스터 비밀번호가 올바르지 않습니다\."),
    ])
    check_invariants(platform, "3단계: 잠금 해제 표준 문구(SEC-003)", sources, [
        ("비밀번호 오류 표준 문구", r"마스터 비밀번호가 올바르지 않습니다\."),
        ("빈 볼트 준비 실패 표준 문구", r"계정은 만들었지만 볼트를 준비하지 못했습니다\."),
        ("가입 직후 생체 인증 안내 제목", r"생체 인증으로 잠금을 풀까요\?"),
    ])
    check_invariants(platform, "4단계: 비밀번호 생성기(GEN-001)", tests, [
        ("길이 상한(64자) 검증", r"\b64\b"),
        ("거부 샘플링 1,000회 반복 검증", r"\b1_?000\b"),
        ("연속 숫자 감지(hasSequentialDigits) 검증", r"hasSequentialDigits"),
        ("문자군 미선택(Messages.NO_CHARSET) 검증", r"Messages\.NO_CHARSET"),
    ])
    check_invariants(platform, "4단계: 비밀번호 생성기 표준 문구(GEN-001)", sources, [
        ("표준 길이 에러 문구", r"비밀번호 길이는 최소 8자 이상 64자 이하이어야 합니다\."),
        ("표준 문자군 에러 문구", r"최소 1개 이상의 문자 종류를 선택해야 합니다\."),
    ])
    check_invariants(platform, "5·6단계: 동기화 대기와 기기 볼트(VLT-002·003)", tests, [
        ("오프라인 대기 재시도(retryPending) 검증", r"retryPending"),
        ("대기 변경 표시(pendingSync) 검증", r"pendingSync"),
        ("기기 볼트 기록(DeviceRecordStore) 검증", r"DeviceRecordStore"),
    ])
    check_invariants(platform, "5·6단계: 동기화 대기와 기기 볼트 표준 문구(VLT-002·003)", sources, [
        ("대기 중 로그아웃 확인 제목", r"동기화되지 않은 변경 사항"),
        ("오프라인 경고 표준 문구", r"오프라인 상태입니다\. 변경 사항이 서버에 동기화되지 않습니다\."),
        ("마스터 비밀번호 변경 감지 표준 문구", r"마스터 비밀번호가 바뀌었습니다\. 새 비밀번호로 로그인해 주세요\."),
    ])
    check_invariants(platform, "7단계: 클립보드 자동 비우기(COM-001)", tests, [
        ("클립보드 30초(30_000ms) 타이머 검증", r"30_000"),
    ])
    check_invariants(platform, "7단계: 클립보드 자동 비우기 표준 문구(COM-001)", sources, [
        ("복사 직후 표준 제목", r"클립보드 보안 소각"),
        ("복사 직후 표준 본문", r"보안을 위해 복사된 내용이 30초 후 클립보드에서 자동 삭제됩니다\."),
    ])
    check_invariants(platform, "8단계: 암호화 백업(VLT-004)", tests, [
        ("파일 이름(keymanage-backup-YYYYMMDD.json) 검증", r"keymanage-backup-\d{8}\.json"),
        ("백업 형식 버전(formatVersion) 검증", r"formatVersion"),
        ("파일 크기 한도(MAX_BACKUP_FILE_BYTES) 경계값 검증", r"MAX_BACKUP_FILE_BYTES"),
        ("새 버전 백업 거절(NewerVersion) 검증", r"NewerVersion"),
        ("비밀번호 오류·손상(CannotOpen) 검증", r"CannotOpen"),
    ])
    check_invariants(platform, "8단계: 암호화 백업 표준 문구(VLT-004)", sources, [
        ("내보내기 전 안내 문구", r"이 파일은 내보낸 시점의 마스터 비밀번호로만 열 수 있습니다\."),
        ("복원 전 안내 문구", r"백업에 있던 항목은 백업 시점 내용으로 돌아갑니다\."),
        ("비밀번호 오류·손상 문구", r"백업을 열 수 없습니다\."),
        ("형식 오류 문구", r"KeyManage 백업 파일이 아닙니다\."),
        ("새 버전 백업 문구", r"더 새 버전의 앱에서 만든 백업입니다\."),
    ])
    check_invariants(platform, "9단계: 마스터 비밀번호 변경(SEC-004)", tests, [
        ("현재와 같음 거절(SAME_MASTER_PASSWORD) 검증", r"SAME_MASTER_PASSWORD"),
        ("오프라인 거절(PASSWORD_CHANGE_OFFLINE) 검증", r"PASSWORD_CHANGE_OFFLINE"),
        ("SEC-002 미달 새 비밀번호 거절('마스터 비밀번호는 12자 이상이어야 합니다.') 검증", r"마스터 비밀번호는 12자 이상이어야 합니다\.|NewMasterPasswordProblem\.TOO_SHORT"),
        ("완료 문구(PASSWORD_CHANGE_DONE) 검증", r"PASSWORD_CHANGE_DONE"),
    ])
    check_invariants(platform, "9단계: 마스터 비밀번호 변경 표준 문구(SEC-004)", sources, [
        ("현재와 같음 문구", r"새 비밀번호가 지금 비밀번호와 같습니다\."),
        ("오프라인 문구", r"인터넷에 연결된 상태에서만 비밀번호를 바꿀 수 있습니다\."),
        ("완료 문구", r"마스터 비밀번호를 바꿨습니다\."),
    ])
    check_invariants(platform, "10단계: 가입 강도·계정 삭제(SEC-002·005)", tests, [
        ("SEC-002 강도 59/60 경계값 검증", r"\b59\b"),
        ("SEC-005 오프라인 거절(ACCOUNT_DELETION_OFFLINE) 검증", r"ACCOUNT_DELETION_OFFLINE"),
        ("SEC-005 완료 문구(ACCOUNT_DELETED) 검증", r"ACCOUNT_DELETED"),
    ])
    check_invariants(platform, "10단계: 가입 강도·계정 삭제 표준 문구(SEC-002·005)", sources, [
        ("SEC-002 가입 비밀번호 취약 문구", r"마스터 비밀번호가 보안에 취약합니다\."),
        ("SEC-005 오프라인 문구", r"인터넷에 연결된 상태에서만 계정을 삭제할 수 있습니다\."),
        ("SEC-005 확인 문구 입력 칸 문구", r"확인을 위해 '계정 삭제'를 입력해 주세요\."),
        ("SEC-005 완료 문구", r"계정을 삭제했습니다\."),
    ])



android_dir = os.path.join(root, "Apps", "Android")
if os.path.isdir(android_dir):
    audited_platforms.append("Android")
    audit_shared_kotlin_client(
        "Android",
        read_all(without_source_sets(source_files(shared_kotlin_dir, "*Test", "kt"), ["jsTest", "appleTest", "iosTest", "macosTest"])
                 + source_files(android_dir, "*est", "kt")),
        read_all(without_source_sets(source_files(shared_kotlin_dir, "*Main", "kt"), ["jsMain", "appleMain", "iosMain", "macosMain"])
                 + source_files(android_dir, "main", "kt")),
    )

apple_dir = os.path.join(root, "Apps", "Apple", "Projects")
if os.path.isdir(apple_dir):
    audited_platforms.append("Apple")
    apple_swift_sources = sorted(
        p for p in glob.glob(os.path.join(apple_dir, "**", "Sources", "**", "*.swift"), recursive=True)
    )
    audit_shared_kotlin_client(
        "Apple",
        read_all(without_source_sets(source_files(shared_kotlin_dir, "*Test", "kt"), ["jsTest", "androidDeviceTest"])),
        read_all(without_source_sets(source_files(shared_kotlin_dir, "*Main", "kt"), ["jsMain", "androidMain"]) + apple_swift_sources),
    )

if not audited_platforms:
    print("오류: 감사할 플랫폼 테스트 디렉터리(Apps/Apple, Apps/Web 또는 Apps/Android)를 찾을 수 없습니다.", file=sys.stderr)
    sys.exit(2)

if violations:
    print("멀티 플랫폼 정책 합격 기준(Policy Acceptance Audit) 위반 발견:")
    for v in violations:
        print(f"  ✖ {v}")
    sys.exit(1)

print(f"✔ 멀티 플랫폼({', '.join(audited_platforms)}) 정책 합격 기준 및 경계값 어설션 감사 통과 (누락 0건)")
PY
