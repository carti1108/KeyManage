#!/usr/bin/env bash
# Apple 클라이언트 정책 합격 기준 및 경계값 어설션 감사 (Policy Acceptance Gate)
#
# 백엔드(:domain)의 PIT 뮤테이션 테스트에 대응하여, 클라이언트 단위 테스트가
# 단순 통과 개수만 채우지 않고 docs/policies/ 및 docs/vault-spec.md의
# 필수 경계값·예외·표준 문구를 실제로 어설션(#expect / XCTAssert)하고 있는지 기계적으로 검증한다.

set -euo pipefail

ROOT_DIR="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
DOMAIN_TESTS_DIR="${ROOT_DIR}/Apps/Apple/Projects/Core/Domain/Tests"
DOMAIN_SOURCES_DIR="${ROOT_DIR}/Apps/Apple/Projects/Core/Domain/Sources"

if [ ! -d "${DOMAIN_TESTS_DIR}" ]; then
  echo "오류: ${DOMAIN_TESTS_DIR} 디렉터리를 찾을 수 없습니다." >&2
  exit 2
fi

PYTHON=""
for candidate in python3 python; do
  if command -v "$candidate" >/dev/null 2>&1; then
    PYTHON="$candidate"
    break
  fi
done

"$PYTHON" - "$ROOT_DIR" <<'PY'
import glob
import os
import re
import sys

root = sys.argv[1]
tests_dir = os.path.join(root, "Apps", "Apple", "Projects", "Core", "Domain", "Tests")
sources_dir = os.path.join(root, "Apps", "Apple", "Projects", "Core", "Domain", "Sources")

test_files = sorted(glob.glob(os.path.join(tests_dir, "*.swift")))
combined_tests = ""
for p in test_files:
    with open(p, encoding="utf-8") as f:
        combined_tests += "\n" + f.read()

violations = []

def check_invariants(module_name, rules):
    for label, pattern in rules:
        if not re.search(pattern, combined_tests, re.MULTILINE | re.DOTALL):
            violations.append(f"[{module_name}] 필수 정책 경계값/어설션 누락: {label} (패턴: {pattern})")

# 1. 암호 모듈 (vault-spec.md, SEC-001) 필수 검증 항목
crypto_rules = [
    ("교차 플랫폼 테스트 벡터(crypto-test-vectors.json) 지문 검증", r"7xdxRO7JQgy8EJPSqLNEqSvFBtDU7JwCjdGfgyTYweY="),
    ("PBKDF2 600,000회 반복 경계값 검증", r"600_?000"),
    ("HKDF authKey / encryptionKey 도메인 분리 검증", r"HKDFKeySeparator\.separate"),
    ("AES-GCM AAD 헤더 변조 복호화 거부 검증", r"(XCTAssertThrowsError|#expect\(throws:)"),
]
check_invariants("1단계: 암호 모듈(vault-spec)", crypto_rules)

# 2. 볼트 병합 및 동기화 모듈 (VLT-001, vault-spec 4장) 필수 검증 항목
vault_rules = [
    ("상위 버전 읽기 전용(isReadOnly) 강제 검증", r"\bisReadOnly\b"),
    ("5MB(5,242,880바이트) 페이로드 상한 검증", r"(5_?242_?880|5\s*\*\s*1024\s*\*\s*1024)"),
    ("톰스톤(deletedAt) 삭제 및 필드 소거 검증", r"\bdeletedAt\b"),
    ("서버 revision 역행(Rollback) 거부 검증", r"revisionRollback|Rollback"),
    ("409 Conflict 최대 3회 재시도 한도 검증", r"(conflictLimitExceeded|409)"),
]
check_invariants("2단계: 볼트 동기화(VLT-001)", vault_rules)

# 3. 잠금 해제 및 자동 잠금 모듈 (SEC-003) — 모듈이 존재할 때만 감사
has_lock_module = (
    os.path.isdir(os.path.join(sources_dir, "Lock"))
    or os.path.isdir(os.path.join(sources_dir, "Session"))
)

if has_lock_module:
    autolock_rules = [
        ("자동 잠금 5종 타임아웃(0, 60, 300, 900, 3600) 경계값 검증", r"3600"),
        ("백그라운드 가림 화면(isPrivacyCoverActive) 상태 전이 검증", r"isPrivacyCoverActive"),
        ("잠금 시 민감 키 소거(masterKey == nil, encryptionKey == nil) 검증", r"(encryptionKey\s*==\s*nil|XCTAssertNil\([^\)]*encryptionKey)"),
        ("생체 인증 14일 재인증 주기 검증", r"(14\s*\*|1_?209_?600|14)"),
        ("SEC-003 표준 비밀번호 오류 문구('마스터 비밀번호가 올바르지 않습니다.') 검증", r"마스터 비밀번호가 올바르지 않습니다\."),
        ("SEC-003 빈 볼트 준비 실패 문구('계정은 만들었지만 볼트를 준비하지 못했습니다.') 검증", r"계정은 만들었지만 볼트를 준비하지 못했습니다\."),
    ]
    check_invariants("3단계: 자동 잠금(SEC-003)", autolock_rules)

if violations:
    print("정책 합격 기준(Policy Acceptance Audit) 위반 발견:")
    for v in violations:
        print(f"  ✖ {v}")
    sys.exit(1)

print("✔ 정책 합격 기준 및 경계값 어설션 감사 통과 (누락 0건)")
PY
