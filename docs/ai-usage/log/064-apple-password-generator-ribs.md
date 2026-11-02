# 2026-11-02 · Apple PasswordGeneratorFeature microRIBs 비밀번호 생성기 구현 (#105)

- 이슈·PR: #105
- 커밋: 1e5dec6

> **요약** GEN-001(비밀번호 생성기 및 엔트로피 강도 측정 규칙), COM-001(30초 클립보드 자동 소각), ADR-0014(Apple microRIBs)에 따라 Apple 플랫폼의 PasswordGeneratorFeature microRIBs 모듈을 구현하고 MainFeature에 연동했다. 길이 조절(8~64자), 4종 문자셋 토글(대문자, 소문자, 숫자, 특수문자), 거부 샘플링 CSPRNG 생성기, 실시간 엔트로피 점수/등급/연속숫자 피드백, 클립보드 복사 및 30초 자동 소각 연동을 TDD로 조립하고 MainFeature 및 App 타깃에 DIP(의존성 역전)로 연결했다. Argon(Medium 수준 추론) 독립 코드 리뷰어가 적발한 에러 시 상태 초기화(P3) 및 `init(coder:)` 컨벤션(P5)을 완치하고 Apple 6중 기계적 검증을 통과했다.

## 사용 도구

- 도구: Antigravity (Gemini), Xcode 16.4, xcodebuild, Tuist 4.x
- 플러그인·스킬: `clean-architecture-layers`, `modern-swift-testing`, `code-review`

## 요청

사용자가 이전 SettingsFeature(#103) 완료 후 다음 작업으로 승인("ㅇㅇ")한 이슈 #105(PasswordGeneratorFeature microRIBs)를 진행했다. "작업전엔 항상 main 최신화하고 pr전엔 항상 main에 리베이스 하고 진행해", "테스트 작성이랑 코드 리뷰만 서브 에이전트 두고 나머지 총괄을 단일로 가", "리뷰 서브 에이전트는 argon으로 해 medium 수준으로" 워크플로우를 엄격히 준수했다.

## AI 산출물

- `Apps/Apple/Projects/Features/PasswordGenerator/Project.swift`: Tuist makeFeature 기반 PasswordGeneratorFeature 4개 타깃 정의 (Interface, Implementation, Testing, Tests).
- `Apps/Apple/Projects/Features/PasswordGenerator/Interface/`:
  - `PasswordGeneratorBuildable.swift`: `@MainActor public protocol PasswordGeneratorBuildable`
  - `PasswordGeneratorRouting.swift`: `@MainActor public protocol PasswordGeneratorRouting`
  - `PasswordGeneratorListener.swift`: `@MainActor public protocol PasswordGeneratorListener`
  - `PasswordGeneratorDependency.swift`: `@MainActor public protocol PasswordGeneratorDependency` (`clipboardManager`)
  - `PasswordGeneratorViewControllable.swift`: `@MainActor public protocol PasswordGeneratorViewControllable`
- `Apps/Apple/Projects/Features/PasswordGenerator/Implementation/`:
  - `PasswordGeneratorRouter.swift`: `PasswordGeneratorInteractable` 프로토콜 정의 및 `ViewableRouter` 구현
  - `PasswordGeneratorInteractor.swift`: `PasswordGeneratorPresentable` 정의, GEN-001 거부 샘플링 생성, 엔트로피 점수 및 보안 등급 산출, 연속 숫자 피드백 안내, COM-001 클립보드 복사 및 30초 자동 소각 토스트 연동
  - `PasswordGeneratorViewController.swift`: `PasswordGeneratorPresentableListener` 정의, macOS `NSViewController` / iOS `UIViewController` 호환 브릿지
  - `PasswordGeneratorViewModel.swift`: 길이, 문자셋 4종 토글, 생성 비밀번호, 실시간 강도 점수/등급, 토스트 Task 수명주기 관리
  - `PasswordGeneratorView.swift`: 비밀번호 표시 카드, 재생성/복사 버튼, 강도 게이지 바, 길이 슬라이더, 문자셋 토글 스위치, 토스트 오버레이
  - `PasswordGeneratorBuilder.swift`: `PasswordGeneratorComponent` 및 DI 조립
- `Apps/Apple/Projects/Features/PasswordGenerator/Testing/PasswordGeneratorTesting.swift`: `MockPasswordGeneratorListener`, `MockPasswordGeneratorRouting`, `MockPasswordGeneratorBuildable`, `MockPasswordGeneratorViewControllable`
- `Apps/Apple/Projects/Features/PasswordGenerator/Tests/PasswordGeneratorTests.swift`: 단위 테스트 17종 전수 통과 (기본 생성, 옵션 변경, 재생성, 빈 문자셋/최소길이 에러 처리 및 상태 초기화, 클립보드 복사 및 자동 소각, 라우터/빌더 바인딩, 뷰모델 상태 갱신)
- `Apps/Apple/Projects/Features/Main/`:
  - `MainDependency.swift`: `passwordGeneratorBuilder: PasswordGeneratorBuildable` 추가
  - `MainRouting.swift`: `routeToPasswordGenerator()` 추가
  - `MainRouter.swift`: `routeToPasswordGenerator()` 자식 RIB attach 및 `viewController.setPasswordGeneratorViewControllable` 마운트 연동
  - `MainViewControllable.swift`: `setPasswordGeneratorViewControllable` 인터페이스 선언
  - `MainViewController.swift` & `MainViewModel.swift`: 자식 뷰컨트롤러 바인딩
  - `MainView.swift`: '생성기' 탭에 `ViewControllableRepresentable` 기반 자식 뷰컨트롤러 SwiftUI 임베드
  - `MainTests.swift`: PasswordGenerator 라우팅 및 뷰컨트롤러 마운트 어설션 보강 (10개 테스트 통과)
- `Apps/Apple/Projects/App/`:
  - `Project.swift`: `PasswordGeneratorFeatureInterface`, `PasswordGeneratorFeatureImplementation` 의존성 추가
  - `AppDependency.swift`: `PasswordGeneratorDependency` 채택 및 `passwordGeneratorBuilder` 주입
- `Scripts/verify-apple.sh`: 4개 신규 피처(MainFeature, VaultListFeature, SettingsFeature, PasswordGeneratorFeature) 단위 테스트 파이프라인 추가.

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 채택 | PR 제출 전 항상 main 최신 리베이스 원칙 준수 채택 |
| 채택 | 리뷰 서브에이전트에 Gemini 4 Argon Medium Reasoning 적용 채택 |
| 채택 | 테스트 작성(RED)과 코드 리뷰(REVIEW)만 서브에이전트 위임, 구현 및 검증/PR은 메인 에이전트 단일 수행 워크플로우 채택 |
| 채택 | Argon 지적 사항(옵션 에러 시 빈 비밀번호/0점 초기화로 stale state 방지, init(coder:) @available(*, unavailable) 컨벤션 일관화) 전수 반영 채택 |

## 다음 작업

- [Apple] VaultItemDetailFeature microRIBs 볼트 아이템 추가/수정/삭제 모달 구현
