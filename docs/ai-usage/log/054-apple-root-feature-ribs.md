# 2026-11-01 · Apple RootFeature microRIBs 루트 라우팅 및 앱 조립 (#86)

- 이슈·PR: #86
- 커밋: ddf99e7, 0c7a977

> **요약** SEC-003(잠금 해제 및 자동 잠금) 및 ADR-0014(Apple microRIBs)에 따라 Apple 멀티플랫폼 최상위 루트 microRIBs인 RootFeature를 구현했다. 초기 잠금 상태 판별, UnlockFeature 자식 RIB 부착, 잠금 해제(didUnlockVault) 수신 시 화면 전이, 잠금 이벤트(didLockVault) 복귀를 TDD로 조립하고 KeyManageMacApp 및 KeyManageIOSApp 진입점에 배선했다. Argon(Medium 수준 추론) 독립 코드 리뷰어가 적발한 결함을 완치한 뒤, Uber RIBs 공식 템플릿 및 DIP 원칙에 맞춰 RootFeature 및 UnlockFeature를 개별 파일로 정격 분리(`Interactable`을 `Router.swift`에 배치)하고 `Project+Feature.swift`에서 `MicroRIBs` 의존성을 자동 주입하도록 일원화하여 Apple 6중 기계적 검증을 통과했다.

## 사용 도구

- 도구: Antigravity (Gemini), Xcode 16.4, xcodebuild
- 플러그인·스킬: `clean-architecture-layers`, `code-review`

## 요청

사용자가 "ㅇㅇ"로 이슈 #86 착수를 승인하고 잔여 구현 로드맵을 확인한 후 진행했다. "작업전엔 항상 main 최신화하고 pr전엔 항상 main에 리베이스 하고 진행해", "테스트 작성이랑 코드 리뷰만 서브 에이전트 두고 나머지 총괄을 단일로 가", "리뷰 서브 에이전트는 argon으로 해 medium 수준으로" 워크플로우를 준수했다. 이후 사용자의 피드백에 따라 "MicroRIBs 의존성 위치 일원화" 및 "Uber RIBs 공식 템플릿에 맞춘 파일 분리(Interactable의 Router.swift 배치, 외부 프로토콜의 Interface 배치)"를 완수했다.

## AI 산출물

- `Apps/Apple/Tuist/ProjectDescriptionHelpers/Project+Feature.swift`: `MicroRIBs` 외부 의존성을 Feature 템플릿(Interface, Implementation, Testing)의 기본 의존성으로 자동 주입.
- `Apps/Apple/Projects/Features/Root/Project.swift`, `Unlock/Project.swift`: 중복 의존성 선언 제거.
- `Apps/Apple/Projects/Features/Root/`: Uber RIBs 정격 구조 파일 분리
  - `Interface/`: `RootBuildable.swift`, `RootRouting.swift`, `RootListener.swift`, `RootDependency.swift`, `RootViewControllable.swift`
  - `Implementation/`: `RootRouter.swift` (`RootInteractable`), `RootInteractor.swift`, `RootViewController.swift`, `RootView.swift`, `RootBuilder.swift` (`RootComponent`)
- `Apps/Apple/Projects/Features/Unlock/`: Uber RIBs 정격 구조 파일 분리
  - `Interface/`: `UnlockBuildable.swift`, `UnlockRouting.swift`, `UnlockListener.swift`, `UnlockDependency.swift`, `BiometricKeyStoreProtocol.swift`, `VaultLockError.swift`
  - `Implementation/`: `UnlockRouter.swift` (`UnlockInteractable`), `UnlockInteractor.swift` (`UnlockPresentable`), `UnlockViewController.swift` (`UnlockPresentableListener`), `UnlockViewModel.swift`, `UnlockView.swift`, `UnlockBuilder.swift` (`UnlockComponent`)
- `Apps/Apple/Projects/Features/Root/Testing/RootTesting.swift`: `MockVaultLockManager`, `MockUnlockBuilder`, `MockUnlockRouting`, `RootRoutingMock`, `RootListenerMock`, `RootDependencyMock`.
- `Apps/Apple/Projects/Features/Root/Tests/RootTests.swift`: 단위 테스트 8종 구축 (초기 잠금/해제 분기, 잠금 해제 전이, 잠금 복귀, 뷰 교체, routeToMain 뷰 정리, routeToUnlock 멱등성, Builder 배선).
- `Apps/Apple/Projects/App/Sources/Shared/AppDependency.swift`: RootDependency 및 UnlockDependency 공유 컨테이너.
- `Apps/Apple/Projects/App/Sources/macOS/KeyManageMacApp.swift`, `KeyManageIOSApp.swift`: RootBuilder 구동 및 WindowGroup 배선.
- `Apps/Apple/Projects/Core/DesignSystem/Sources/KMPasswordField.swift`: iOS 15 호환 1인자 onChange 클로저 분기 복원.

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 채택 | PR 제출 전 항상 main 최신 리베이스 원칙 준수 채택 |
| 채택 | 리뷰 서브에이전트에 Gemini 4 Argon Medium Reasoning 적용 채택 |
| 채택 | 테스트 작성(RED)과 코드 리뷰(REVIEW)만 서브에이전트 위임, 구현 및 검증/PR은 메인 에이전트 단일 수행 워크플로우 채택 |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `bash Scripts/check-docs.sh` | 통과 |
| `bash Scripts/check-apple-lint.sh` | 통과 (위반 0건) |
| `bash Scripts/check-policy-acceptance.sh` | 통과 (누락 0건) |
| `bash Scripts/verify-tdd-integrity.sh` | 통과 (기존 어설션 삭제·변조 0건) |
| `bash Scripts/verify-apple.sh` | 통과 (Apple 6중 검증 All Green, RootFeature 테스트 8건 포함 전수 통과) |

## 검증하지 못한 것

| 항목 | 사유 |
| :--- | :--- |
| 다중 가상 모니터 및 외장 디스플레이 핫플러깅 환경에서의 윈도우 크기 변경 | macOS 호스트 xcodebuild 테스트 환경에서 RootViewController 컨테이너 뷰 교체 및 오토레이아웃 제약조건 무결성으로 검증함 |

## AI의 오류와 정정

| 오류 | 발견 | 조치 |
| :--- | :--- | :--- |
| `RootRouter.routeToMain()` 호출 시 자식 RIB만 detach하고 뷰컨트롤러 상의 활성 뷰를 정리하지 않아 화면에 해제 화면이 갇히는 좀비 뷰(Zombie View) 결함 (P1) | Argon 코드 리뷰어 (Medium) | `RootViewControllable`에 `clearActiveView()` 규격을 추가하고 `routeToMain()` 시 활성 뷰컨트롤러를 뷰 계층에서 안전하게 제거하도록 보강 |
| `RootRouter.routeToUnlock()` 호출 시 기존 인스턴스 존재 여부를 확인하지 않고 중복 생성하여 입력 중이던 비밀번호가 소실되는 결함 (P2) | Argon 코드 리뷰어 (Medium) | `guard unlockRouting == nil else { return }` 가드를 추가하여 멱등성(Idempotency) 보장 |
| iOS `RootViewController`의 커스텀 컨테이너 수명주기 메서드(`willMove(toParent: nil)`, `didMove(toParent: self)`) 누락 (P2) | Argon 코드 리뷰어 (Medium) | UIKit 표준 컨테이너 수명주기 메서드를 정확히 배치하여 Safe Area 및 이벤트 왜곡 차단 |
| `RootRouter.routeToMain()` 및 멱등성 관련 단위 테스트 누락 (P2) | Argon 코드 리뷰어 (Medium) | `rootRouter_routeToMain_detachesUnlockAndClearsActiveView()` 및 `rootRouter_routeToUnlock_whenAlreadyAttached_isIdempotent()` 2개 테스트 추가 |
| RIBs 구성요소 한 파일 집중 안티패턴 및 Interactable 배치 오류 | 사용자 피드백 | Uber RIBs 공식 템플릿 표준에 따라 `Interactable`을 `Router.swift`에 배치하고 각 컴포넌트별로 파일 분리 및 `Project+Feature.swift` 의존성 일원화 |
| `KMPasswordField` iOS 17 전용 2인자 `.onChange`로 인한 iOS 15 빌드 실패 | 사용자 빌드 검증 | `#if os(macOS)` 컴파일러 분기 적용하여 iOS 15 호환 1인자 클로저 복원 |
