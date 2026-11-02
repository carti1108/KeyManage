# 2026-11-02 · Apple SEC-004 마스터 비밀번호 변경 및 원격 KDF 변경 감지 (#110)

- 이슈·PR: #110
- 커밋: 0af8912

> **요약** SEC-004(마스터 비밀번호 변경 및 원자적 자격증명 교체), SEC-003 규칙 11(마스터 비밀번호 원격 변경 감지), ADR-0014(Apple microRIBs)에 따라 Apple 플랫폼의 마스터 비밀번호 변경 및 KDF 원격 감지 파이프라인을 구현했다. `VaultLockManager`에 영지식(Zero-Knowledge) 기반 클라이언트 PBKDF2 600,000회 키 유도, HKDF 도메인 분리, AES-256-GCM 볼트 재암호화, 5MB 용량 검증, 원자적 자격증명 교체 로직을 구축했다. `SettingsFeature`에 비밀번호 변경 모달 폼 UI를 추가하고, `UnlockFeature`에 잠금 해제 실패 시 서버 KDF 불일치를 비동기 감지해 사용자에게 재로그인 안내를 노출하도록 연동했다. Argon(Medium 수준 추론) 독립 코드 리뷰어가 적발한 비동기 대기 중 자동 잠금/로그아웃 레이스 방어(P1), 임시 키 버퍼 scope-bound 제로화(P2), 신규 비밀번호 예외 매핑(P2), UnlockInteractor 원격 감지 단위 테스트(P2)를 완치하고 Apple 6중 기계적 검증을 통과했다.

## 사용 도구

- 도구: Antigravity (Gemini), Xcode 16.4, xcodebuild, Tuist 4.x
- 플러그인·스킬: `clean-architecture-layers`, `modern-swift-testing`, `code-review`, `policy-manager`

## 요청

이전 PR #108(PasswordGeneratorFeature) 및 PR #107(Web 마스터 비밀번호 변경) 완료 후, 사용자가 최우선으로 지시한 이슈 #110(`[Apple] SEC-004 마스터 비밀번호 변경 대응`)을 구현했다. "작업전엔 항상 main 최신화하고 pr전엔 항상 main에 리베이스 하고 진행해", "테스트 작성이랑 코드 리뷰만 서브 에이전트 두고 나머지 총괄을 단일로 가", "리뷰 서브 에이전트는 argon으로 해 medium 수준으로" 워크플로우를 준수했다.

## AI 산출물

- `Apps/Apple/Projects/Core/Domain/Sources/Auth/`:
  - `MasterPasswordChangeStrings.swift`: SEC-004 및 SEC-003 정책 표준 한국어 문구 상수 정의.
  - `MasterPasswordChangeError.swift`: 11종 세부 에러 케이스 및 LocalizedError 매핑.
  - `AuthNetworkClientProtocol.swift`: `PreloginResponse`, `ChangePasswordRequestPayload`, `ChangePasswordResponsePayload` 및 비동기 REST 클라이언트 프로토콜 정의.
  - `URLSessionAuthNetworkClient.swift`: `URLSession` 기반 prelogin 및 changePassword 엔드포인트 통신 구현.
- `Apps/Apple/Projects/Core/Domain/Sources/Lock/VaultLockManager.swift`:
  - `VaultLockManagerProtocol`에 `revision: Int`, `changeMasterPassword`, `checkIfPasswordChangedOnServer` 추가.
  - 비동기 키 유도/통신 중 락 방출(비재진입 락) 및 동기 스냅샷/원자적 상태 갱신 분리.
  - 비동기 대기 중 자동 잠금/로그아웃 레이스 발생 시 키 메모리 노출 차단 및 제로화.
  - `defer { zeroizeData(...) }` 기반 임시 키 버퍼 scope-bound 제로화 보장.
- `Apps/Apple/Projects/Core/Domain/Tests/MasterPasswordChangeTests.swift`:
  - 14종 단위 테스트 (성공 경로, 잘못된 현재 비밀번호, 12자 미만, 동일 비밀번호, 오프라인/잠금, 읽기전용 볼트 거절, 통신 에러 전파, 서버 KDF 변경 감지 4종, 표준 문구 일치성 검증) 작성 및 통과.
- `Apps/Apple/Projects/Features/Settings/`:
  - `SettingsDependency.swift` & `AppDependency.swift`: `networkClient: AuthNetworkClientProtocol` 주입.
  - `SettingsViewModel.swift`: 비밀번호 변경 모달 상태 및 필드 바인딩.
  - `SettingsView.swift`: 마스터 비밀번호 변경 진입 버튼 및 서브뷰 분리 모달 폼 UI 구현.
  - `SettingsInteractor.swift` & `SettingsViewController.swift`: 변경 완료 토스트 및 모달 닫기 연동.
  - `SettingsTests.swift`: 변경 성공 및 거절 시나리오 5종 단위 테스트 추가.
- `Apps/Apple/Projects/Features/Unlock/`:
  - `UnlockDependency.swift` & `UnlockBuilder.swift`: `networkClient` 의존성 주입.
  - `UnlockInteractor.swift`: 잠금 해제 실패 시 서버 KDF 변경 비동기 감지 및 안전 가드 추가.
  - `UnlockTesting.swift` & `UnlockTests.swift`: 서버 KDF 변경 감지 시 표준 안내 문구 갱신 단위 테스트 추가.
- `Scripts/check-policy-acceptance.sh`: Apple 9단계 SEC-004 불변식 검증 규칙 9종 추가.

## 사람의 판단

| 판단 | 내용 |
| --- | --- |
| 1순위 구현 선택 | 사용자의 "1순위 먼저" 지시에 따라 Apple 플랫폼 SEC-004 구현을 최우선으로 착수. |
| 비동기 락 경계 분리 | Swift 6 Concurrency 환경에서 async 함수 내 NSLock 획득 불가 문제를 스냅샷 추출과 원자적 갱신으로 분리해 해결. |
| Argon 피드백 100% 수용 | 비동기 대기 중 자동 잠금 시 키 누수(P1), 임시 버퍼 제로화(P2), Unlock 테스트 누락(P2) 등 6개 지적 사항을 전원 반영. |
