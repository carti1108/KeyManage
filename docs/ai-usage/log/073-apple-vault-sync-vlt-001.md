# 2026-11-02 · Apple VLT-001 & VLT-002 원격 볼트 동기화 파이프라인 및 백그라운드 동기화 (#118)

- 이슈·PR: #118
- 커밋: 378a422

> **요약** Apple 플랫폼(iOS/macOS)에 VLT-001 및 VLT-002 정책에 따른 원격 볼트 저장(`POST /api/v1/vault`), 3-way 병합, 409 충돌 시 최대 3회 재시도(총 4회 시도), 롤백 거절, 5MB 용량 사전 검증, 그리고 오프라인 대기 큐(`hasPendingSync`) 및 백그라운드 주기적/수동 동기화 파이프라인을 완성했다. 비동기 통신 중 잠금 상태 전환 시 메모리 평문 부활 방지, 401 세션 만료 시 즉시 세션 소거, 상위 스키마 읽기 전용 저장 차단, 그리고 VLT-002 규칙 4(순차 revision 불변식)를 구현 및 검증했다. `verify-apple.sh` 6중 기계적 통합 검증과 Argon Medium 독립 코드 리뷰(APPROVE)를 통과했다.

## 사용 도구

- 도구: Antigravity (Gemini), Xcode 16.4, xcodebuild, Tuist 4.x
- 플러그인·스킬: `clean-architecture-layers`, `solid-testable-design`, `modern-swift-testing`, `code-review`

## 요청

남은 앱 완성 관점 1순위 과제인 `[Apple] VLT-001 & VLT-002 원격 볼트 동기화(Sync) 파이프라인 및 백그라운드 주기적 동기화` 구현 요청을 수신하여 단일 총괄 에이전트와 서브에이전트(RED Test Writer, Argon Medium Code Reviewer) 위임 구조로 진행했다.

## AI 산출물

- `Apps/Apple/Projects/Core/Domain/Sources/Auth/AuthNetworkClientProtocol.swift`: `SaveVaultRequestPayload`, `SaveVaultResponsePayload`, `saveVault(accessToken:expectedRevision:payload:)` 프로토콜 메서드 추가.
- `Apps/Apple/Projects/Core/Domain/Sources/Auth/URLSessionAuthNetworkClient.swift`: `saveVault` 엔드포인트 구현 (200 OK 새 revision 반환, 409 Conflict 매핑, 413 Payload Too Large 매핑, 401 Unauthorized 매핑, 429 Too Many Requests 매핑, URLError 오프라인 매핑).
- `Apps/Apple/Projects/Core/Domain/Sources/Vault/VaultSyncStrings.swift`: VLT-001 & VLT-002 표준 사용자 문구(용량 초과, 동기화 충돌, 되돌림, 오프라인 등) 정의.
- `Apps/Apple/Projects/Core/Domain/Sources/Vault/VaultSyncError.swift`: LocalizedError 준수 및 사용자 친화적 에러 메시지 매핑.
- `Apps/Apple/Projects/Core/Domain/Sources/Sync/RemoteVaultSyncService.swift`: `RemoteVaultSyncServiceProtocol` 실제 운영 구현체 작성 (5MB 사전 검증, 읽기 전용 가드, AES-256-GCM 암·복호화).
- `Apps/Apple/Projects/Core/Domain/Sources/Lock/VaultLockManager.swift`:
  - `hasPendingSync: Bool`, `isOffline: Bool`, `isSyncing: Bool`, `setOffline(_:)` 추가.
  - Swift 6 비동기 락 스코프 분리(`prepareSync()`, `applySyncSuccess()`, `markPendingSync()`, `endSyncing()`).
  - `sync(networkClient:now:) async throws`: 5MB 사전 검증, 3-way 병합, 롤백 거절, 409 충돌 3회 재시도(총 4회), 401 세션 만료 소거, 상위 스키마 읽기 전용 저장 차단, 통신 중 잠금 시 평문 메모리 복원 차단, VLT-002 규칙 4 순차 증가 검증.
- `Apps/Apple/Projects/Features/VaultList/`:
  - `VaultListViewModel.swift`: `hasPendingSync`, `isOffline`, `isSyncing` 바인딩, `triggerSync()` 핸들러, 토스트 시간 상수화.
  - `VaultListView.swift`: 상단 오프라인/대기 안내 배너, 툴바 동기화 버튼, 시스템 `.refreshable` 당겨서 새로고침 연동.
  - `VaultListInteractor.swift`: 항목 추가/삭제 후 백그라운드 동기화 트리거(`didRequestSync()`), 검색 필터 보존(`refreshList()`).
- `Apps/Apple/Projects/Features/Settings/`:
  - `SettingsViewModel.swift` & `SettingsView.swift`: `hasPendingSync == true`일 때 VLT-002 표준 로그아웃 확인 대화상자 노출 및 확인 후 로그아웃 처리.
- `Apps/Apple/Projects/Core/Domain/Tests/VaultSyncPipelineTests.swift`: 신규 단위 테스트 20종 작성 (HTTP 매핑, RemoteVaultSyncService 암·복호화, 3-way 병합, 롤백 거절, 409 충돌 3회 초과, in-flight 잠금 메모리 소거 검증, 5MB 경계값 등).

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 비동기 락 스코프 분리 | Swift 6 async 메서드 내부에서 `syncLock.lock()` 직접 호출 시 발생하는 컴파일 에러를 방지하고 데드락을 원천 차단하기 위해 동기 헬퍼 메서드로 락 범위를 엄격히 격리하도록 채택. |
| in-flight 잠금 시 평문 메모리 부활 차단 | 네트워크 요청 중에 사용자가 잠금하거나 유휴 타임아웃이 발생한 경우, 응답 완료 시 `_isLocked == true`이면 `_decryptedVault` 복원을 차단하여 메모리 보안 완결성 확보. |
| VLT-002 규칙 4 엄격 준수 | 저장 응답의 revision이 `expectedRev + 1`이 아니면 서버 규약 위반으로 보고 대기열로 침묵시키지 않고 즉시 예외를 발생시키도록 보강. |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `bash Scripts/check-docs.sh` | 통과 |
| `bash Scripts/check-apple-lint.sh` | 통과 (위반 0건) |
| `bash Scripts/check-policy-acceptance.sh` | 통과 (누락 0건) |
| `bash Scripts/check-api-contract.sh` | 통과 (엔드포인트 8종, 표준 오류 코드 11종 일치) |
| `bash Scripts/verify-tdd-integrity.sh` | 통과 (워킹 트리 어설션 삭제·변조 0건) |
| `xcodebuild -scheme KeyManageMac build` | 통과 (macOS 빌드 성공) |
| Apple 9개 타깃 단위 테스트 전수 검증 | 통과 (KeyManageDomain, KeyManageData, UnlockFeature, KeyManageDesignSystem, KeyManageUIAdapter, MainFeature, VaultListFeature, SettingsFeature, PasswordGeneratorFeature) |
| Argon Medium 독립 코드 리뷰 | APPROVE (수정 요청 5건 반영 후 최종 승인) |

## 검증하지 못한 것

1. 실제 물리 iOS 디바이스 네트워크 단절/복구 현장 수동 테스트: 시뮬레이터 및 목/네트워크 레이어 단위 테스트로 충돌/오프라인 시나리오를 대체 검증함.

## AI의 오류와 정정

| 오류 | 발견 | 조치 |
| :--- | :--- | :--- |
| `sync` 메서드 내 `syncLock.lock()` 직접 호출로 인한 Swift 6 async 컴파일 에러 | build 단계 컴파일러 에러 | 동기 헬퍼 메서드(`prepareSync`, `applySyncSuccess`, `markPendingSync`, `endSyncing`)로 락 스코프를 완전히 분리하여 해결. |
| in-flight 비동기 동기화 통신 중 잠금 시 평문 볼트 메모리 노출 취약점 | Argon Medium 코드 리뷰 | `applySyncSuccess` 내부에서 `_isLocked == true`일 때 `_decryptedVault` 할당을 스킵하도록 가드 추가. |
| 401 세션 만료 시 예외 삼킴 및 로컬 성공 처리 | Argon Medium 코드 리뷰 | `!hasPending` 조회 경로에서도 `AuthError.sessionExpired` 수신 시 `logout()` 호출 후 즉시 throw하도록 수정. |
| 상위 스키마 병합 후 읽기 전용 볼트 서버 재전송 시도 | Argon Medium 코드 리뷰 | 병합 직후 `guard !mergedVault.isReadOnly`를 검사하여 `VaultError.vaultIsReadOnly`를 발생시키도록 수정. |
| revision 순차 증가 불변식 위반 시 대기열 묵살 | Argon Medium 코드 리뷰 | `newRev != expectedRev + 1`일 때 서버 규약 위반으로 보고 에러를 상위로 전파하도록 수정. |
