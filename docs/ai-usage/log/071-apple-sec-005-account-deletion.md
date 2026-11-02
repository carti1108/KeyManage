# 2026-11-02 · Apple SEC-005 계정 삭제 (회원 탈퇴) UI 및 도메인 연동 (#116)

- 이슈·PR: #116
- 커밋: 5d51e49

> **요약** Apple 플랫폼(iOS/macOS)에 SEC-005 정책에 따른 영지식 계정 삭제(회원 탈퇴) 도메인 파이프라인 및 SettingsFeature UI를 구현했다. 클라이언트 단에서 마스터 비밀번호로 현재 볼트 복호화 및 authHash 유도를 선행 검증하여 잘못된 비밀번호 입력 시 불필요한 서버 호출을 방지하고, 확인 문구("계정 삭제")를 공백 허용 없이 엄격히 일치 검증한다. 오프라인 상태 차단, 읽기 전용 볼트에서의 삭제 허용(SEC-005 규칙 1), 서버 성공(204 No Content) 시 즉각적인 인메모리 볼트·세션 토큰·복호화 키 영구 소각(`logout()`)을 완벽히 연동했다. Apple 6중 기계적 통합 검증 스위트(`Scripts/verify-apple.sh`)를 All Green으로 통과했다.

## 사용 도구

- 도구: Antigravity (Gemini), Xcode 16.4, xcodebuild, Tuist 4.x
- 플러그인·스킬: `clean-architecture-layers`, `solid-testable-design`, `modern-swift-testing`, `code-review`

## 요청

남은 구현 계획 1순위인 `[Apple] SEC-005 계정 삭제 (회원 탈퇴) UI 및 도메인 연동` 구현 요청을 수신하여 작업에 착수했다.

## AI 산출물

- `Apps/Apple/Projects/Core/Domain/Sources/Auth/AccountDeletionStrings.swift`: SEC-005 정책 표준 사용자 문구(제목, 확인 문구 `"계정 삭제"`, 경고 메시지, 에러 메시지 8종) 정의.
- `Apps/Apple/Projects/Core/Domain/Sources/Auth/AccountDeletionError.swift`: LocalizedError 준수 계정 삭제 에러 8종(`confirmationMismatch`, `invalidPassword`, `offline`, `sessionExpired`, `tooManyAttempts`, `deviceClearFailed`, `networkError`, `vaultLocked`) 정의.
- `Apps/Apple/Projects/Core/Domain/Sources/Auth/AuthNetworkClientProtocol.swift`: `POST /api/v1/auth/delete-account` 계약 모델 `DeleteAccountRequestPayload` 및 프로토콜 메서드(기본 구현 포함) 선언.
- `Apps/Apple/Projects/Core/Domain/Sources/Auth/URLSessionAuthNetworkClient.swift`: 네트워크 클라이언트 구현 및 HTTP 204 No Content, 401 AUTH_FAILED, 429, 네트워크/오프라인 에러 매핑.
- `Apps/Apple/Projects/Core/Domain/Sources/Lock/VaultLockManager.swift`:
  - `deleteAccount(password:confirmationPhrase:networkClient:now:) async throws` 구현.
  - SEC-005 규칙 1 준수: 읽기 전용 볼트에서도 삭제 허용.
  - 확인 문구 strict equality 검증 (`confirmationPhrase == AccountDeletionStrings.confirmationPhrase`).
  - 오프라인/미인증 검증, 마스터 비밀번호로 로컬 authHash 유도 검증, 서버 요청 성공 후 `logout()` 호출로 인메모리 민감 데이터 영구 소각.
- `Apps/Apple/Projects/Core/Domain/Tests/AccountDeletionTests.swift`: 단위 테스트 15종(성공, 읽기 전용 허용, 확인 문구 10종 파라미터화, 비밀번호 오류, 오프라인, 401/429/500 에러 매핑, 정책 문구 일치성 등) 구현.
- `Apps/Apple/Projects/Features/Settings/Implementation/SettingsViewModel.swift`: 계정 삭제 모달 상태 관리, 실시간 폼 유효성 검증(`canExecuteDeleteAccount`), 계정 삭제 실행 메서드 구현.
- `Apps/Apple/Projects/Features/Settings/Implementation/SettingsView.swift`:
  - 환경설정 계정 섹션에 빨간색 '계정 삭제' 파괴적 버튼 배치.
  - 시스템 표준 모달 컨테이너(`modalNavigationContainer`) 기반의 계정 삭제 모달 시트(`deleteAccountModalView`) 구현.
  - 경고 카드, 마스터 비밀번호 입력창, 확인 문구 입력창, `.cancellationAction`("취소"), `.confirmationAction`("계정 삭제", 유효 시만 활성화) 시스템 툴바 배치.
- `Apps/Apple/Projects/Features/Settings/Implementation/SettingsViewController.swift` & `SettingsInteractor.swift`:
  - microRIBs 프레젠터 및 인터랙터 바인딩.
  - 성공 시 모달 닫기, 성공 토스트 노출, `settingsDidRequestLogout()` 호출을 통한 루트 라우팅 트리거.
- `Apps/Apple/Projects/Features/Settings/Tests/SettingsTests.swift`: 인터랙터 및 뷰모델 계정 삭제 로직 단위 테스트 3종 추가.

## 사람의 판단

| 판단 | 내용 |
| --- | --- |
| 확인 문구 strict equality 적용 | SEC-005 정책상 "앞뒤 공백을 포함해 계정 삭제와 정확히 일치해야 함"이 명시되어 있으므로 whitespace trimming 없이 엄격 일치(`==`)로 구현. |
| 읽기 전용 볼트 계정 삭제 허용 | SEC-005 규칙 1에 따라 읽기 전용 볼트에서도 계정 삭제는 허용되어야 하므로 `isReadOnly` 가드를 우회하도록 설계. |
| 클라이언트 선행 검증 | 영지식 원칙에 따라 로컬 볼트 복호화 및 authHash 생성을 먼저 수행하여, 비밀번호 오류 시 네트워크 홉 없이 즉시 로컬 거절. |
