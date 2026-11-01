# 2026-11-01 · Apple UnlockFeature microRIBs 잠금 해제 인터랙터 및 뷰 조립 (#84)

- 이슈·PR: #84
- 커밋: 83ef6fd

> **요약** SEC-003(잠금 해제 및 자동 잠금) 및 ADR-0014(Apple microRIBs)에 따라 UnlockFeature 모듈에 마스터 비밀번호 해제, 생체 인증 해제, 비밀번호 메모리 즉시 소거(Zeroization), SEC-003 표준 에러 안내, KMCard/KMButton/KMPasswordField 기반 크로스 플랫폼(macOS & iOS) UnlockView 조립을 TDD 방식으로 완결했다. Argon(Medium 수준 추론) 독립 코드 리뷰어가 적발한 생체 인증 더미 복호화 키 주입(P1), 비밀번호 메모리 영구 잔존 위험(P2), 생체 인증 14일 만료·정보 변경 엣지 케이스 단위 테스트 누락(P2), VoiceOver A11y 누락(P3), 플랫폼 뷰 컨트롤러 중복(P4)을 완치하고 Apple 6중 기계적 검증을 통과했다.

## 사용 도구

- 도구: Antigravity (Gemini), Xcode 16.4, xcodebuild
- 플러그인·스킬: `clean-architecture-layers`, `code-review`

## 요청

사용자가 "머지했음", "ㅇㅇ"로 이슈 #84 착수를 지시하며 "테스트 작성이랑 코드 리뷰만 서브 에이전트 두고 나머지 총괄을 단일로 가", "작업전엔 항상 main 최신화하고 pr전엔 항상 main에 리베이스 하고 진행해", "리뷰 서브 에이전트는 argon으로 해 medium 수준으로" 워크플로우를 강제했다.

## AI 산출물

- `Apps/Apple/Projects/Features/Unlock/Interface/UnlockInterface.swift`: `BiometricKeyStoreProtocol` 선언 및 `UnlockDependency`에 `biometricKeyStore` 주입 계약 확장.
- `Apps/Apple/Projects/Features/Unlock/Implementation/UnlockImplementation.swift`:
  - `UnlockInteractor`: 마스터 비밀번호 검증(`unlock(withMasterPassword:)`), 생체 인증 키 복원 및 해제 연동(`unlockWithBiometrics()`), 비밀번호 `defer` 메모리 즉시 소거, SEC-003 표준 에러 바인딩.
  - `UnlockViewModel`: `@ObservedObject` 기반 반응형 뷰모델, 비밀번호 소거 메서드 제공.
  - `UnlockView`: `KMCard`, `KMButton`, `KMPasswordField`, `DesignToken` 기반 고품질 반응형 뷰, VoiceOver 헤더 및 오류 안내 레이블 연동.
  - `UnlockViewController`: `PlatformHostingController` 타입별칭을 통해 macOS(`NSHostingController`)와 iOS(`UIHostingController`) 37줄 중복 코드를 단일화.
  - `UnlockBuilder`: `UnlockComponent` 의존성 바인딩 및 Router/Interactor/ViewController 5계층 microRIBs 조립.
- `Apps/Apple/Projects/Features/Unlock/Testing/UnlockTesting.swift`: `MockVaultLockManager` 및 `MockBiometricKeyStore` 구현.
- `Apps/Apple/Projects/Features/Unlock/Tests/UnlockTests.swift`: 단위 테스트 9개 (마스터 비밀번호 일치/불일치, 비밀번호 제로화, 생체 인증 성공, 비활성화 가드, 14일 만료 재요구, 생체 정보 변경 무효화, BiometricKeyStore 키 복원 연동, Builder 의존성 배선 검증).

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 채택 | PR 제출 전 항상 main 최신 리베이스 원칙 준수 채택 |
| 채택 | 리뷰 서브에이전트에 Gemini 4 Argon Medium Reasoning 적용 채택 |
| 채택 | 테스트 작성(RED)과 코드 리뷰(REVIEW)만 서브에이전트 위임, 구현(GREEN) 및 검증/PR은 메인 에이전트 단일 수행 워크플로우 채택 |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `bash Scripts/check-docs.sh` | 통과 |
| `bash Scripts/check-apple-lint.sh` | 통과 (위반 0건) |
| `bash Scripts/check-policy-acceptance.sh` | 통과 (누락 0건) |
| `bash Scripts/verify-tdd-integrity.sh` | 통과 (기존 어설션 삭제·변조 0건) |
| `bash Scripts/verify-apple.sh` | 통과 (Apple 6중 검증 All Green, UnlockFeature 테스트 9건 포함 전수 통과) |

## 검증하지 못한 것

| 항목 | 사유 |
| :--- | :--- |
| 실제 물리 Secure Enclave 칩셋 하드웨어 연동 | Secure Enclave 및 Touch ID/Face ID 센서 하드웨어는 macOS 호스트 단위 테스트 환경에서 `BiometricKeyStoreProtocol` 및 `MockBiometricKeyStore` 모의 객체로 격리 검증함 |

## AI의 오류와 정정

| 오류 | 발견 | 조치 |
| :--- | :--- | :--- |
| 생체 인증 해제 시 메모리에 키가 소거된 상태에서 `Data(repeating: 0x01, count: 32)` 가짜 더미 바이트를 복호화 키로 주입하여 런타임에 무조건 복호화 실패하는 결함 (P1) | Argon 코드 리뷰어 (Medium) | `BiometricKeyStoreProtocol` 추상 인터페이스를 선언하고 주입받은 키 저장소로부터 평가된 해시 및 복호화 키를 안전하게 전달받도록 아키텍처 정정 |
| `UnlockInteractor`가 비밀번호를 인스턴스 프로퍼티로 보유하고 해제 완료 후에도 힙 메모리에 영구 잔존할 위험 (P2) | Argon 코드 리뷰어 (Medium) | `UnlockInteractor`의 비밀번호 인스턴스 상태를 제거하고 `defer { self.password = "" }` 및 `viewModel.clearPassword()`로 즉각 메모리 소거 처리 |
| 생체 인증 비활성화, 14일 만료(`biometricsExpiredRequirePassword`), 생체 정보 변경(`biometricsInvalidatedRequirePassword`) 엣지 케이스 단위 테스트 누락 (P2) | Argon 코드 리뷰어 (Medium) | SEC-003 정책 기반 엣지 케이스 테스트 4종을 `UnlockTests.swift`에 추가하여 검증 커버리지 확대 |
| `UnlockView`의 타이틀 헤더 및 에러 메시지에 스크린 리더(VoiceOver) 접근성 레이블 누락 (P3) | Argon 코드 리뷰어 (Medium) | `.accessibilityAddTraits(.isHeader)` 및 `.accessibilityLabel("오류 안내: \(errorMessage)")` 추가 |
| macOS(`NSHostingController`)와 iOS(`UIHostingController`)용 `UnlockViewController`의 완전 중복 구현 37줄 발생 (P4) | Argon 코드 리뷰어 (Medium) | `typealias PlatformHostingController`로 크로스 플랫폼 베이스를 추상화하여 중복 코드 100% 제거 |
