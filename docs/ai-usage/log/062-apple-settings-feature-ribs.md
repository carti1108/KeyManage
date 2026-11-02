# 2026-11-02 · Apple SettingsFeature microRIBs 환경설정 및 백업/자동잠금 연동 (#103)

- 이슈·PR: #103
- 커밋: f0cf32f

> **요약** VLT-004(암호화 백업 내보내기와 복원) 및 SEC-003(자동 잠금 유휴 시간 설정), VLT-002(로그아웃 대기 변경 확인), ADR-0014(Apple microRIBs)에 따라 Apple 플랫폼의 SettingsFeature microRIBs를 구현했다. 자동 잠금 타임아웃 5종(즉시, 1분, 5분, 15분, 1시간) 설정 및 세션 연동, VLT-004 표준 문구 기반 암호화 백업 파일 내보내기/복원 모달, 로그아웃 확인 팝업 및 루트 라우팅 연동을 TDD로 조립하고 MainFeature 및 App 타깃에 DIP(의존성 역전)로 연결했다. Argon(Medium 수준 추론) 독립 코드 리뷰어가 적발한 iPadOS `UIActivityViewController` 팝오버 크래시 위험(P2) 및 `MainRouter` ↔ `MainViewControllable` 자식 뷰컨트롤러 마운트 연동 누락(P2), macOS SavePanel 약한 참조/원자적 쓰기(P3/P4), 임시 파일 정리 및 토스트 타이머 경합(P3)을 완치하고 Apple 6중 기계적 검증을 통과했다.

## 사용 도구

- 도구: Antigravity (Gemini), Xcode 16.4, xcodebuild, Tuist 4.x
- 플러그인·스킬: `clean-architecture-layers`, `modern-swift-testing`, `code-review`

## 요청

사용자가 "1번부터 해"로 이슈 #103(SettingsFeature microRIBs) 착수를 지시한 후 진행했다. "작업전엔 항상 main 최신화하고 pr전엔 항상 main에 리베이스 하고 진행해", "테스트 작성이랑 코드 리뷰만 서브 에이전트 두고 나머지 총괄을 단일로 가", "리뷰 서브 에이전트는 argon으로 해 medium 수준으로" 워크플로우를 엄격히 준수했다.

## AI 산출물

- `Apps/Apple/Projects/Features/Settings/Project.swift`: Tuist makeFeature 기반 SettingsFeature 4개 타깃 정의 (Interface, Implementation, Testing, Tests).
- `Apps/Apple/Projects/Features/Settings/Interface/`:
  - `SettingsBuildable.swift`: `@MainActor public protocol SettingsBuildable`
  - `SettingsRouting.swift`: `@MainActor public protocol SettingsRouting`
  - `SettingsListener.swift`: `@MainActor public protocol SettingsListener` (`settingsDidRequestLogout()`)
  - `SettingsDependency.swift`: `@MainActor public protocol SettingsDependency` (`lockManager`)
  - `SettingsViewControllable.swift`: `@MainActor public protocol SettingsViewControllable`
- `Apps/Apple/Projects/Features/Settings/Implementation/`:
  - `SettingsRouter.swift`: `SettingsInteractable` 프로토콜 정의 및 `ViewableRouter` 구현
  - `SettingsInteractor.swift`: `SettingsPresentable` 정의, 자동 잠금 타임아웃 갱신(SEC-003), 백업 내보내기/복원(VLT-004), 로그아웃 호출
  - `SettingsViewController.swift`: `SettingsPresentableListener` 정의, macOS `NSSavePanel`(원자적 쓰기) / iOS `UIActivityViewController`(iPad 팝오버 크래시 방어 및 임시 파일 자동 소거) 파일 내보내기 연동
  - `SettingsViewModel.swift`: 타임아웃 선택, 토스트 Task 취소 기반 자동 소각, 백업 복원/내보내기 모달 상태 관리
  - `SettingsView.swift`: 보안 설정(타임아웃 Picker), 백업 및 복원(KeyManageBackupStrings 바인딩), 계정 로그아웃 확인 알림창, Security-Scoped Bookmark 파일 읽기
  - `SettingsBuilder.swift`: `SettingsComponent` 및 DI 조립
- `Apps/Apple/Projects/Features/Settings/Testing/SettingsTesting.swift`: `MockSettingsListener`, `MockSettingsRouting`, `MockSettingsBuildable`, `MockSettingsViewControllable`
- `Apps/Apple/Projects/Features/Settings/Tests/SettingsTests.swift`: 단위 테스트 12종 전수 통과 (타임아웃 동기화, 백업 내보내기/복원 성공 및 에러 토스트, 로그아웃 호출, 라우터/빌더 바인딩, 뷰모델 상태 갱신)
- `Apps/Apple/Projects/Features/Main/`:
  - `MainDependency.swift`: `settingsBuilder: SettingsBuildable` 추가
  - `MainRouting.swift`: `routeToSettings()` 추가
  - `MainRouter.swift`: `routeToSettings()` 자식 RIB attach 및 `viewController.setSettingsViewControllable` 마운트 연동
  - `MainViewControllable.swift`: `setVaultListViewControllable`, `setSettingsViewControllable` 인터페이스 선언
  - `MainViewController.swift` & `MainViewModel.swift`: 자식 뷰컨트롤러 바인딩
  - `MainView.swift`: `ViewControllableRepresentable` 기반 자식 뷰컨트롤러 SwiftUI 임베드
  - `MainTests.swift`: Settings 라우팅 및 뷰컨트롤러 마운트 어설션 보강 (9개 테스트 통과)
- `Apps/Apple/Projects/App/`:
  - `Project.swift`: `SettingsFeatureInterface`, `SettingsFeatureImplementation` 의존성 추가
  - `AppDependency.swift`: `SettingsDependency` 채택 및 `settingsBuilder` 주입

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 채택 | PR 제출 전 항상 main 최신 리베이스 원칙 준수 채택 |
| 채택 | 리뷰 서브에이전트에 Gemini 4 Argon Medium Reasoning 적용 채택 |
| 채택 | 테스트 작성(RED)과 코드 리뷰(REVIEW)만 서브에이전트 위임, 구현 및 검증/PR은 메인 에이전트 단일 수행 워크플로우 채택 |
| 채택 | Argon 지적 사항(iPad 팝오버 크래시 방어, MainRouter 자식 뷰컨트롤러 마운트 연동) 전수 반영 채택 |

## 다음 작업

- [Apple] PasswordGeneratorFeature microRIBs 비밀번호 생성기 구현 (GEN-001)
