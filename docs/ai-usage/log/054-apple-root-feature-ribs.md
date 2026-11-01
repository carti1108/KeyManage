# 2026-11-01 · Apple RootFeature microRIBs 루트 라우팅 및 앱 조립 (#86)

- 이슈·PR: #86
- 커밋: ddf99e7

> **요약** SEC-003(잠금 해제 및 자동 잠금) 및 ADR-0014(Apple microRIBs)에 따라 Apple 멀티플랫폼 최상위 루트 microRIBs인 RootFeature를 구현했다. 초기 잠금 상태 판별, UnlockFeature 자식 RIB 부착, 잠금 해제(didUnlockVault) 수신 시 화면 전이, 잠금 이벤트(didLockVault) 복귀를 TDD로 조립하고 KeyManageMacApp 및 KeyManageIOSApp 진입점에 배선했다. Argon(Medium 수준 추론) 독립 코드 리뷰어가 적발한 routeToMain() 시 해제된 자식 뷰 영구 노출 결함(P1), routeToUnlock() 멱등성 결여 및 입력 데이터 소실 위험(P2), iOS UIViewController 커스텀 컨테이너 수명주기(willMove/didMove) 누락(P2), 전이 단위 테스트 누락(P2)을 완치하고 Apple 6중 기계적 검증을 통과했다.

## 사용 도구

- 도구: Antigravity (Gemini), Xcode 16.4, xcodebuild
- 플러그인·스킬: `clean-architecture-layers`, `code-review`

## 요청

사용자가 "ㅇㅇ"로 이슈 #86 착수를 승인하고 잔여 구현 로드맵을 확인한 후 진행했다. "작업전엔 항상 main 최신화하고 pr전엔 항상 main에 리베이스 하고 진행해", "테스트 작성이랑 코드 리뷰만 서브 에이전트 두고 나머지 총괄을 단일로 가", "리뷰 서브 에이전트는 argon으로 해 medium 수준으로" 워크플로우를 준수했다.

## AI 산출물

- `Apps/Apple/Projects/Features/Root/Project.swift`: Tuist makeFeature 기반 RootFeature 4개 타깃 정의 (Interface, Implementation, Testing, Tests).
- `Apps/Apple/Projects/Features/Root/Interface/RootInterface.swift`: `RootRouting`, `RootListener`, `RootBuildable`, `RootDependency` 계약 선언.
- `Apps/Apple/Projects/Features/Root/Implementation/RootImplementation.swift`:
  - `RootComponent`: lockManager, unlockBuilder 의존성 캡슐화.
  - `RootInteractor`: didBecomeActive() 초기 잠금 상태 분기, didUnlockVault(), didLockVault() 전이 핸들러.
  - `RootRouter`: unlockRouting attach/detach, 멱등성 가드(guard unlockRouting == nil), 활성 뷰 교체 및 정리.
  - `RootViewController`: macOS/iOS 커스텀 컨테이너 뷰컨트롤러, willMove(toParent: nil) 및 didMove(toParent: self) 수명주기 준수.
  - `RootBuilder`: 의존성 배선 및 조립.
- `Apps/Apple/Projects/Features/Root/Testing/RootTesting.swift`: `MockVaultLockManager`, `MockUnlockBuilder`, `MockUnlockRouting`, `RootRoutingMock`, `RootListenerMock`, `RootDependencyMock`.
- `Apps/Apple/Projects/Features/Root/Tests/RootTests.swift`: 단위 테스트 8종 구축 (초기 잠금/해제 분기, 잠금 해제 전이, 잠금 복귀, 뷰 교체, routeToMain 뷰 정리, routeToUnlock 멱등성, Builder 배선).
- `Apps/Apple/Projects/App/Sources/Shared/AppDependency.swift`: RootDependency 및 UnlockDependency 공유 컨테이너.
- `Apps/Apple/Projects/App/Sources/macOS/KeyManageMacApp.swift`, `KeyManageIOSApp.swift`: RootBuilder 구동 및 WindowGroup 배선.

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
