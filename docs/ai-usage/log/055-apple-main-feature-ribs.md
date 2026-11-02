# 2026-11-02 · Apple MainFeature microRIBs 메인 대시보드 셸 및 루트 라우팅 연동 (#90)

- 이슈·PR: #90
- 커밋: e08e410, 1826b84

> **요약** SEC-003(잠금 해제 및 자동 잠금) 및 ADR-0014(Apple microRIBs)에 따라 Apple 멀티플랫폼 잠금 해제 후 진입하는 최상위 메인 대시보드 부모 microRIBs인 MainFeature를 구현하고 RootFeature 라우팅 트리에 배선했다. 볼트·생성기·설정 3대 탭 전환 및 볼트 즉시 잠금 요청 액션을 TDD로 조립하고, RootRouter에서 routeToMain() 시 MainFeature attach 및 replaceActiveView(), routeToUnlock() 시 MainFeature detachChild() 정리를 완벽히 연동했다. Argon(Medium 수준 추론) 독립 코드 리뷰어가 승인(LGTM)하였으며, iOS TabView 양방향 바인딩 보정, Interface 전반 @MainActor 격리 명시, AppDependency 계산 프로퍼티 전환을 반영하고 Apple 6중 기계적 검증을 통과했다.

## 사용 도구

- 도구: Antigravity (Gemini), Xcode 16.4, xcodebuild, Tuist 4.x
- 플러그인·스킬: `clean-architecture-layers`, `modern-swift-testing`, `code-review`

## 요청

사용자가 "ㅇㅇ"로 이슈 #90 착수를 승인하고 잔여 구현 로드맵을 확인한 후 진행했다. "작업전엔 항상 main 최신화하고 pr전엔 항상 main에 리베이스 하고 진행해", "테스트 작성이랑 코드 리뷰만 서브 에이전트 두고 나머지 총괄을 단일로 가", "리뷰 서브 에이전트는 argon으로 해 medium 수준으로" 워크플로우를 엄격히 준수했다.

## AI 산출물

- `Apps/Apple/Projects/Features/Main/Project.swift`: Tuist makeFeature 기반 MainFeature 4개 타깃 정의 (Interface, Implementation, Testing, Tests).
- `Apps/Apple/Projects/Features/Main/Interface/`:
  - `MainBuildable.swift`: `@MainActor public protocol MainBuildable`
  - `MainRouting.swift`: `@MainActor public protocol MainRouting`
  - `MainListener.swift`: `@MainActor public protocol MainListener` (`didRequestLockVault()`)
  - `MainDependency.swift`: `@MainActor public protocol MainDependency` (`lockManager`)
  - `MainViewControllable.swift`: `@MainActor public protocol MainViewControllable`
  - `MainTab.swift`: `vault`, `generator`, `settings` 열거형 및 `Identifiable` 준수
- `Apps/Apple/Projects/Features/Main/Implementation/`:
  - `MainRouter.swift`: `MainInteractable` 프로토콜 정의 및 `ViewableRouter` 구현
  - `MainInteractor.swift`: `MainPresentable` 정의, `requestLockVault()` 및 `didSelectTab(_:)` 비즈니스 로직
  - `MainViewController.swift`: `MainPresentableListener` 정의, macOS(`NSHostingController`)/iOS(`UIHostingController`) 셸 뷰컨트롤러
  - `MainViewModel.swift`: 탭 선택 및 볼트 잠금 트리거 뷰모델
  - `MainView.swift`: macOS 2-Pane NavigationSplit 및 iOS TabView 적응형 셸 레이아웃
  - `MainBuilder.swift`: `MainComponent` 및 의존성 주입 조립
- `Apps/Apple/Projects/Features/Main/Testing/MainTesting.swift`: `MockMainListener`, `MockMainRouting`, `MockMainBuildable`, `MockMainViewControllable`, `MockVaultLockManager`
- `Apps/Apple/Projects/Features/Main/Tests/MainTests.swift`: 단위 테스트 6종 구축 (락 요청 호출, 탭 갱신, 라우터 바인딩, 빌더 DI 조립, 뷰모델 트리거 2종)
- `Apps/Apple/Projects/Features/Root/`:
  - `RootRouter.swift`: `routeToMain()` 시 Main RIB 빌드/부착 및 뷰 교체, `routeToUnlock()` 시 Main RIB 안전 분리
  - `RootInteractor.swift`: `MainListener` 채택 및 `didRequestLockVault()` 수신 시 `routeToUnlock()` 호출
  - `RootBuilder.swift`: `MainBuildable` 주입 배선
- `Apps/Apple/Projects/Features/Root/Tests/RootTests.swift`: 단위 테스트 4종 추가 (기존 8종 100% 보존, 총 12종 전수 통과)
- `Apps/Apple/Projects/App/Sources/Shared/AppDependency.swift`: `MainDependency` 채택 및 `MainBuilder` 계산 프로퍼티 제공

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
| `bash Scripts/verify-apple.sh` | 통과 (Apple 6중 검증 All Green, MainFeature 6건 + RootFeature 12건 전수 통과) |
| `xcodebuild -scheme KeyManageIOS build` | 통과 (iOS 15 시뮬레이터 빌드 성공) |

## 검증하지 못한 것

| 항목 | 사유 |
| :--- | :--- |
| 실기기(iPhone/Mac) 하드웨어 생체 인증 연동 및 노치 영역 Safe Area | macOS 호스트 시뮬레이터 및 xcodebuild 컨테이너 뷰 교체와 오토레이아웃 제약조건 무결성으로 검증함 |

## AI의 오류와 정정

| 오류 | 발견 | 조치 |
| :--- | :--- | :--- |
| `MainView.swift` iOS 레이아웃에서 `TabView(selection: $viewModel.selectedTab)` 직접 바인딩으로 인해 탭 선택 시 인터랙터로의 이벤트 전파 누락 (P3) | Argon 코드 리뷰어 (Medium) | 커스텀 `Binding<MainTab>`을 적용하여 `selectTab(_:)` 메서드가 호출되도록 수정 |
| `MainFeatureInterface` 프로토콜들의 `@MainActor` 명시 누락 (P3) | Argon 코드 리뷰어 (Medium) | `MainBuildable`, `MainRouting`, `MainViewControllable`, `MainDependency` 선언부에 `@MainActor`를 추가하여 Swift 6 Complete Concurrency 환경 완벽 격리 |
| `RootRouter.routeToUnlock()`에서 수동 attach된 MainRouting 인스턴스를 프로퍼티만 체크하여 detach 누락 위험 | RED Test 검증 | `children` 목록 전수 순회(`where child is MainRouting`)를 추가하여 어떤 경로로 부착되었든 안전하게 분리되도록 방어 로직 강화 |
