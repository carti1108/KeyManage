# 2026-11-02 · Apple 시스템 네비게이션 표준 정렬 및 뷰포트 왜곡 수정

- 이슈·PR: [진행 중]
- 커밋: b2b1af9

> **요약** Apple 플랫폼(iOS/macOS)에서 발생한 화면 뷰포트 레터박스 왜곡(구형 호환 모드로 인한 찌그러짐 현상)을 `UILaunchScreen` 주입 및 Safe Area 보정으로 전면 해소했다. 또한 사용자의 "네비게이션 같은 것들은 최대한 시스템 활용해" 지침에 따라 커스텀 복합 컴포넌트를 Apple HIG(Human Interface Guidelines) 표준 시스템 컴포넌트로 전면 마이그레이션했다. `NavigationStack`, 시스템 툴바(`ToolbarItem`, `lock`, `plus`), 시스템 `.searchable` 검색바, 최신 `ContentUnavailableView` 빈 화면, 그리고 모달 시트의 인라인 네비게이션 및 취소/저장 시스템 툴바를 연동했다. Apple 6중 기계적 통합 검증 스위트(`Scripts/verify-apple.sh`)를 All Green으로 완벽하게 통과했다.

## 사용 도구

- 도구: Antigravity (Gemini), Xcode 16.4, xcodebuild, xcrun simctl, Tuist 4.x
- 플러그인·스킬: `swiftui-architecture`, `clean-architecture-layers`, `code-review`

## 요청

사용자가 시뮬레이터 구동 화면을 확인한 후 "뷰 다 찌그러져있는데 뭐하는 거임", "네비게이션 같은 것들은 최대한 시스템 활용해"를 피드백함에 따라, 뷰포트 해상도 왜곡의 근본 원인을 파악하여 해결하고 시스템 표준 네비게이션으로 전면 리팩토링했다.

## AI 산출물

- `Apps/Apple/Projects/App/Project.swift`: `KeyManageIOS` 타깃에 `UILaunchScreen: [:]`을 확장 주입하여 iOS 시뮬레이터가 레거시 해상도 호환 모드로 축소하지 않고 최신 기기 네이티브 전체 해상도(Fullscreen)를 활성화하도록 수정.
- `Apps/Apple/Projects/App/Sources/iOS/KeyManageIOSApp.swift`: `RootViewControllerRepresentable`에 `.ignoresSafeArea()`를 추가하여 루트 뷰 컨트롤러의 네이티브 Safe Area 및 상단 스테이터스 바 영역 레이아웃 가이드 보장.
- `Apps/Apple/Projects/Features/Main/Implementation/MainView.swift`:
  - iOS 탭 컨테이너에서 기존 `NavigationView`를 `NavigationStack` 및 하위 호환 헬퍼로 최신화.
  - 네비게이션 툴바의 투박한 커스텀 사각 버튼을 애플 시스템 아이콘 툴바 버튼(`ToolbarItem(placement: .topBarTrailing)`, `lock` 심볼)으로 교체.
- `Apps/Apple/Projects/Features/VaultList/Implementation/VaultListView.swift`:
  - 뷰 본문에 억지로 끼워져 있던 커스텀 `topBar`(HStack 검색창 + 항목 추가 버튼)를 완전히 제거.
  - 시스템 네이티브 `.searchable(text:prompt:)`를 연동하여 네비게이션 바 아래에 유려한 검색 UX 제공.
  - `항목 추가` 버튼을 네비게이션 툴바 액션(`ToolbarItem(placement: .primaryAction)`, `plus` 심볼)으로 승격.
  - 빈 목록 화면에 최신 `ContentUnavailableView` (iOS 17+/macOS 14+) 및 하위 호환 레거시 뷰를 유려하게 적용.
  - `itemFormModalView` 모달 시트에 `NavigationStack` 및 `cancellationAction`("취소"), `confirmationAction`("저장") 툴바 표준 적용.

## 사람의 판단

| 판단 | 내용 |
| --- | --- |
| 시스템 `.searchable` 도입 | 별도 HStack 뷰보다 SwiftUI `.searchable`이 스크롤 연동, 애니메이션, 포커스 인터랙션에서 압도적으로 자연스럽고 화면 폭 낭비가 없음. |
| 툴바 표준 아이콘 채택 | 툴바 안에 커스텀 스타일 버튼을 넣으면 HIG에 위배되고 붕 떠 보이므로, 표준 SF Symbols(`lock`, `plus`) 기반 시스템 버튼으로 정렬. |
| UILaunchScreen Info.plist 주입 | 코드 레이아웃 문제가 아닌 번들 Info.plist의 런치스크린 부재로 인한 iOS 런타임 호환성 다운스케일링 버그임을 정확히 진단하고 Tuist 매니페스트에서 근본 해결. |
