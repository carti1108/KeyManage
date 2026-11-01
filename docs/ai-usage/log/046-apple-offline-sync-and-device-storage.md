# 2026-11-01 · Apple 오프라인 동기화 대기 및 기기 볼트 보존 구현 (#68)

- 이슈·PR: #68
- 커밋: e70375d

> **요약** Apple 클라이언트(iOS·macOS)에 오프라인 동기화 대기 엔진(VLT-002)과 기기 볼트 보존 저장소(VLT-003, ADR-APL-001)를 구현했다. 독립 코드 리뷰어가 발견한 비동기 동시성 데이터 유실(P1) 및 오프라인 잠금 해제 에러 전파(P1) 결함을 완벽히 수정하고 기계적 검증을 통과했다.

## 사용 도구

- 도구: Antigravity (Gemini), Xcode 16.4, xcodebuild
- 플러그인·스킬: `api-spec-doc`, `clean-architecture-layers`, `code-review`

## 요청

사용자가 "main 최신화하고 ios,macos 쪽 다음 구현 계획 말해 봐" 및 계획 승인("ㅇㅇ")을 전달하여 이슈 #68에 착수했다.

## AI 산출물

- `Apps/Apple/docs/adr/ADR-APL-001-apple-device-storage-and-keychain.md`: Application Support 및 Keychain 격리 아키텍처 결정.
- `Apps/Apple/Projects/Core/Domain/Sources/Sync/PendingSyncEngine.swift`: VLT-002 오프라인 동기화 대기 엔진 및 일시적 실패/영구 거절/30초 재시도 상태 머신.
- `Apps/Apple/Projects/Core/Domain/Sources/Persistence/DeviceVaultStore.swift`: VLT-003 단일 계정 기기 볼트 보존, KDF 변경 대기 폐기, 다른 계정 삭제.
- `Apps/Apple/Projects/Core/Domain/Tests/PendingSyncEngineTests.swift`: VLT-002 단위 테스트 18개.
- `Apps/Apple/Projects/Core/Domain/Tests/DeviceVaultStoreTests.swift`: VLT-003 단위 테스트 11개.
- `Scripts/check-policy-acceptance.sh`: VLT-002, VLT-003 정책 합격 기준 7종 확장.

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 채택 | ADR-APL-001 Apple 전용 Keychain 세션 보존 및 App Support 볼트 분리 채택 |
| 채택 | VLT-002, VLT-003 서브에이전트 역할 분리 파이프라인(RED ➡️ GREEN ➡️ REVIEW ➡️ VERIFY) 적용 채택 |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `bash Scripts/check-docs.sh` | 통과 |
| `bash Scripts/check-apple-lint.sh` | 통과 (위반 0건) |
| `bash Scripts/check-policy-acceptance.sh` | 통과 (누락 0건) |
| `bash Scripts/verify-tdd-integrity.sh` | 통과 (TDD 불변성 100%) |
| `xcodebuild -scheme KeyManageDomain test` | 통과 (102 passed, 0 failed) |

## 검증하지 못한 것

1. 실제 물리 iOS 디바이스에서의 Keychain 하드웨어 보안 칩(Secure Enclave) 연동 (시뮬레이터/macOS 호스트 환경에서 단위 테스트로 검증함)

## AI의 오류와 정정

| 오류 | 발견 | 조치 |
| :--- | :--- | :--- |
| `performResync` 통신 중 신규 `save` 발생 시 `pendingVault = nil`로 덮어써 사용자 데이터가 유실되는 경쟁 상태 (P1) | 독립 코드 리뷰어 서브에이전트 | 동기화 시작 스냅샷과 완료 시점 상태를 비교하여 신규 변경이 발생한 경우 대기열을 유지하도록 수정 |
| 오프라인 시 잠금 해제(`triggerUnlock`) 호출 시 네트워크 에러가 그대로 throw되어 잠금 해제가 차단되는 결함 (P1) | 독립 코드 리뷰어 서브에이전트 | 원격 조회가 일시적 실패일 경우 로컬 사본과의 병합본을 반환하고 대기열에 보존하여 오프라인 잠금 해제를 허용하도록 수정 |
| `PendingSyncEngine.init`에 더미 이메일("current_user@example.com")이 하드코딩되어 실환경 재로그인 시 대기열이 오폐기되는 결함 (P1) | 독립 코드 리뷰어 서브에이전트 | 기본값을 `nil`로 바꾸고 외부에서 계정 이메일을 바인딩하도록 수정 |
