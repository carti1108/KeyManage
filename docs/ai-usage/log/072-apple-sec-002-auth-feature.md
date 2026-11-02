# 2026-11-02 · Apple SEC-002 원격 계정 로그인 및 회원가입 UI 연동 (#117)

- 이슈·PR: #117
- 커밋: f6d9aed

> **요약** Apple 플랫폼(iOS/macOS)에 SEC-002 정책에 따른 원격 계정 회원가입/로그인 도메인 파이프라인 및 AuthFeature microRIBs 모듈을 구현했다. 클라이언트 단에서 영지식 원칙에 따라 16바이트 랜덤 salt 생성, PBKDF2(600,000회) 및 HKDF 기반 키 분리(`authHash`, `encryptionKey`)를 수행하여 서버에 마스터 비밀번호가 노출되지 않도록 전송하며, 회원가입 시 빈 암호화 볼트를 즉시 로컬 세션에 활성화한다. 로그인 시 prelogin을 통한 salt/반복횟수 획득, 반복횟수 범위(60만~1천만) 검증, 원격 볼트 복호화 및 세션 수립을 완비했다. RootFeature에 세션 유무에 따른 `routeToAuth()` 초기 분기 및 인증 성공 시 `routeToMain()`, 로그아웃/회원탈퇴 시 `routeToAuth()` 복귀 라이프사이클을 완성했다. Apple 6중 기계적 통합 검증 스위트(`Scripts/verify-apple.sh`)를 All Green으로 통과했다.

## 사용 도구

- 도구: Antigravity (Gemini), Xcode 16.4, xcodebuild, Tuist 4.x
- 플러그인·스킬: `clean-architecture-layers`, `solid-testable-design`, `modern-swift-testing`, `code-review`

## 요청

남은 앱 완성 구현 과제 1순위인 `[Apple] SEC-002 원격 계정 로그인 / 회원가입 RIBs (AuthFeature) 및 초기 진입 라우팅` 구현 요청을 수신하여 작업에 착수했다.

## AI 산출물

- `Apps/Apple/Projects/Core/Domain/Sources/Auth/AuthStrings.swift`: SEC-002 정책 표준 사용자 문구(제목, 입력 라벨, 버튼 라벨, 에러 메시지 10종) 정의.
- `Apps/Apple/Projects/Core/Domain/Sources/Auth/AuthError.swift`: LocalizedError 준수 인증 에러 11종(`invalidEmail`, `passwordTooShort`, `confirmationMismatch`, `emailAlreadyRegistered`, `registrationDisabled`, `loginFailed`, `offline`, `tooManyAttempts`, `sessionExpired`, `networkError`, `unknown`) 정의.
- `Apps/Apple/Projects/Core/Domain/Sources/Auth/AuthNetworkClientProtocol.swift`: `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `GET /api/v1/vault` DTO 페이로드 및 프로토콜 메서드 선언.
- `Apps/Apple/Projects/Core/Domain/Sources/Auth/URLSessionAuthNetworkClient.swift`: 네트워크 클라이언트 구현 및 HTTP 201/200/404/401/409/403/429, 네트워크/오프라인 에러 매핑.
- `Apps/Apple/Projects/Core/Domain/Sources/Lock/VaultLockManager.swift`:
  - `hasActiveSession: Bool`: 세션 토큰 및 salt 존재 여부 기반 판별 프로퍼티 추가.
  - `register(email:password:confirmation:networkClient:now:) async throws`: 이메일 정규화/정규식/254자 검증, 12자 이상/확인 일치 검증, 16바이트 salt 생성, PBKDF2/HKDF 키 분리, 서버 등록, 빈 볼트 암호화 및 세션 셋업, 민감 키 소각.
  - `login(email:password:networkClient:now:) async throws`: 이메일 검증, prelogin 호출, salt/KDF 범위(60만~1천만) 검증, 키 유도, 서버 로그인, 원격 볼트 복호화 또는 빈 볼트 생성, 세션 셋업, 민감 키 소각.
- `Apps/Apple/Projects/Core/Domain/Tests/AuthTests.swift`: 단위 테스트 18종(회원가입 정상/실패 6종, 로그인 정상/실패 6종, 정책 문구 2종, HTTP 상태 매핑 4종) 구현.
- `Apps/Apple/Projects/Features/Auth/`: Tuist microRIBs 신규 피처 모듈 스캐폴딩 및 구현:
  - `Interface/`: `AuthListener`, `AuthBuildable`, `AuthRouting`, `AuthDependency`.
  - `Implementation/AuthViewModel.swift`: 로그인/회원가입 모드 전환, 실시간 이메일/비밀번호/확인 유효성 검증(`canSubmit`), 제출 로직.
  - `Implementation/AuthView.swift`: Apple HIG 표준 뷰, `DesignToken` 적용, 이메일/비밀번호/확인 필드, 로딩 인디케이터, 모드 전환 버튼.
  - `Implementation/AuthViewController.swift`, `AuthInteractor.swift`, `AuthRouter.swift`, `AuthBuilder.swift`.
  - `Testing/AuthTesting.swift` & `Tests/AuthFeatureTests.swift`: 단위 테스트 3종 구현.
- `Apps/Apple/Projects/Features/Root/`: 세션 유무에 따른 `routeToAuth()` 초기 분기 및 `authDidComplete()` 라우팅 연동, 테스트 갱신.
- `Apps/Apple/Projects/App/`: `AuthFeatureInterface`, `AuthFeatureImplementation` Tuist 의존성 연결 및 `AppDependency` 주입.

## 사람의 판단

| 판단 | 내용 |
| --- | --- |
| 초기 진입 라이프사이클 완성 | 기존 구조는 앱 실행 시 세션 유무와 무관하게 바로 잠금 해제(UnlockFeature) 화면으로 진입하여 신규 사용자 진입이 불가능했음. 세션 유무(`hasActiveSession`)를 최초 확인하여 세션이 없으면 `AuthFeature`로 분기하도록 개선. |
| 가입 시 빈 볼트 로컬 세션 즉시 생성 | 신규 가입 시 서버에는 볼트가 없으므로 유도된 `encryptionKey`로 빈 볼트(`VaultData(items: [])`)를 즉시 암호화하여 로컬 세션을 생성하고 잠금 해제 상태로 메인 화면에 진입. |
| KDF 반복 횟수 클라이언트 가드 | SEC-002 정책에 명시된 600,000회 이상 10,000,000회 이하의 KDF 반복 횟수 범위를 클라이언트에서 엄격히 검증하여 악의적 서버 조작으로 인한 DoS 공격 차단. |
