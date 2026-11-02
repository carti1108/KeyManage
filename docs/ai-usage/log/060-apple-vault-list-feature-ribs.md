# 2026-11-02 · Apple VaultListFeature microRIBs 볼트 목록 및 소각 연동 (#97)

- 이슈·PR: #97
- 커밋: d956f95, 401cdcc

> **요약** COM-001(30초 클립보드 자동 소각) 및 VLT-001(볼트 동기화/툼스톤), ADR-0014(Apple microRIBs)에 따라 MainFeature 볼트 탭의 자식 microRIBs인 VaultListFeature를 구현했다. 복호화된 볼트 아이템 목록 렌더링, 검색 필터링, 비밀번호 마스킹/표시 토글, 클립보드 복사(30초 자동 소각 연동)를 TDD로 조립하고 SystemPasteboard 크로스 플랫폼 어댑터를 구축했다. Argon(Medium 수준 추론) 독립 코드 리뷰어가 적발한 삭제 툼스톤(isDeleted == true) 아이템 노출 결함(P2), 클립보드 복사 실패 시 에러 삼킴 및 안내 누락(P2), 초장문 비밀번호 레이아웃 방어(P4)를 완치하고 Apple 6중 기계적 검증을 통과했다.

## 사용 도구

- 도구: Antigravity (Gemini), Xcode 16.4, xcodebuild, Tuist 4.x
- 플러그인·스킬: `clean-architecture-layers`, `modern-swift-testing`, `code-review`

## 요청

사용자가 "ㅇㅇ"로 이슈 #97 착수를 승인하고 잔여 구현 로드맵을 확인한 후 진행했다. "작업전엔 항상 main 최신화하고 pr전엔 항상 main에 리베이스 하고 진행해", "테스트 작성이랑 코드 리뷰만 서브 에이전트 두고 나머지 총괄을 단일로 가", "리뷰 서브 에이전트는 argon으로 해 medium 수준으로" 워크플로우를 준수했다.

## AI 산출물

- `Apps/Apple/Projects/Features/VaultList/Project.swift`: Tuist makeFeature 기반 VaultListFeature 4개 타깃 정의 (Interface, Implementation, Testing, Tests).
- `Apps/Apple/Projects/Features/VaultList/Interface/`:
  - `VaultListBuildable.swift`: `@MainActor public protocol VaultListBuildable`
  - `VaultListRouting.swift`: `@MainActor public protocol VaultListRouting`
  - `VaultListListener.swift`: `@MainActor public protocol VaultListListener`
  - `VaultListDependency.swift`: `@MainActor public protocol VaultListDependency` (`lockManager`, `clipboardManager`)
  - `VaultListViewControllable.swift`: `@MainActor public protocol VaultListViewControllable`
- `Apps/Apple/Projects/Features/VaultList/Implementation/`:
  - `VaultListRouter.swift`: `VaultListInteractable` 프로토콜 정의 및 `ViewableRouter` 구현
  - `VaultListInteractor.swift`: `VaultListPresentable` 정의, 아이템 목록 로드(isDeleted 필터링), 검색 쿼리 필터링, 비밀번호/아이디 클립보드 복사 및 토스트 발송
  - `VaultListViewController.swift`: `VaultListPresentableListener` 정의, macOS(`NSHostingController`)/iOS(`UIHostingController`) 뷰컨트롤러, 토스트 레이스 컨디션 방어
  - `VaultListViewModel.swift`: 검색 텍스트, 아이템 목록, 토스트 메시지, 패스워드 표시 ID 세트 상태 관리
  - `VaultListView.swift`: 검색바, 아이템 카드(`KMCard`), 복사 버튼(`KMButton`), 마스킹 토글, 토스트 오버레이
  - `VaultListBuilder.swift`: `VaultListComponent` 및 DI 조립
- `Apps/Apple/Projects/Features/VaultList/Testing/VaultListTesting.swift`: `MockVaultListListener`, `MockVaultListRouting`, `MockVaultListBuildable`, `MockVaultListViewControllable`, `MockClipboardManager`
- `Apps/Apple/Projects/Features/VaultList/Tests/VaultListTests.swift`: 단위 테스트 9종 구축 (아이템 로드, 검색 필터링, 패스워드/유저명 복사, 라우터 바인딩, 빌더 DI, 툼스톤 필터링, 복사 실패 토스트, 뷰모델 마스킹 토글)
- `Apps/Apple/Projects/Features/Main/`:
  - `MainDependency.swift`: `vaultListBuilder: VaultListBuildable` 추가
  - `MainRouter.swift`: `routeToVaultList()` 자식 RIB 빌드 및 `attachChild` 구현
  - `MainInteractor.swift`: `VaultListListener` 채택 및 `didBecomeActive()` 시 볼트 목록 라우팅 실행
- `Apps/Apple/Projects/App/Sources/Shared/`:
  - `SystemPasteboard.swift`: macOS(`NSPasteboard`) 및 iOS(`UIPasteboard`) 크로스 플랫폼 어댑터
  - `AppDependency.swift`: `VaultListDependency` 채택 및 `SystemPasteboard` 주입

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
| `bash Scripts/verify-apple.sh` | 통과 (Apple 6중 검증 All Green, VaultList 9건 + Main 7건 전수 통과) |
| `xcodebuild -scheme KeyManageIOS build` | 통과 (iOS 15 시뮬레이터 빌드 성공) |

## 검증하지 못한 것

| 항목 | 사유 |
| :--- | :--- |
| 실기기(iPhone/Mac) 클립보드 범용 페이스트보드(Universal Clipboard) 동기화 환경에서의 소각 | macOS 호스트 xcodebuild 및 단위 테스트 환경에서 SystemPasteboard와 30초 타이머 스케줄러로 기계적 검증함 |

## AI의 오류와 정정

| 오류 | 발견 | 조치 |
| :--- | :--- | :--- |
| 볼트 삭제 툼스톤(`isDeleted == true`) 아이템이 필터링되지 않고 뷰에 노출되는 결함 (P2) | Argon 코드 리뷰어 (Medium) | `allItems`에서 `.filter { !$0.isDeleted }`를 적용하여 삭제된 항목 배제 |
| 클립보드 복사 실패 시 에러를 무시하여 사용자가 복사 실패를 인지하지 못하는 결함 (P2) | Argon 코드 리뷰어 (Medium) | `catch` 블록에서 `ClipboardStrings.copyFailedMessage` 토스트를 호출하도록 보강 |
| `AppDependency.swift`에서 `ClipboardManager` 기본 생성자 인자(`pasteboard`) 누락 빌드 에러 | Apple 빌드 검증 | `SystemPasteboard.swift`를 구현하고 `ClipboardManager(pasteboard: SystemPasteboard())`로 연결 |
| `VaultListView.swift`에서 초장문 비밀번호 노출 시 버튼이 밀려나는 레이아웃 결함 (P4) | Argon 코드 리뷰어 (Medium) | 비밀번호 텍스트에 `.lineLimit(1)` 및 `.truncationMode(.middle)` 적용 |
