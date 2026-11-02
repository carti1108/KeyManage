# 2026-11-02 · Apple Feature RIBs State-Action 단방향 아키텍처 통일

- 이슈·PR: #119
- 커밋: c0034b8

> **요약** Apple 플랫폼의 6개 UI 보유 Feature RIB(`Main`, `PasswordGenerator`, `Unlock`, `VaultList`, `Settings`, `Auth`)에 `ViewState` / `ViewAction` 구조체·열거형과 `request(action:)` / `update(state:)` 단방향 흐름을 도입했다.

## 사용 도구

- 도구: Antigravity (Gemini)
- 플러그인·스킬: `ribs-architecture`, `commit-rules`, `ai-usage`

## 요청

`https://minsone.github.io/swiftui-ribs` 패턴에 맞춰 Apple Feature RIBs 전반을 `State`/`Action` 및 `request(action:)`/`update(state:)` 단방향 흐름으로 리팩터링하고 `origin/main` 최신화 후 PR을 생성한다.

## AI 산출물

- `Apps/Apple/Projects/Features/Main/Implementation/`: `MainViewStateAction.swift` 추가 및 `MainViewModel`, `MainViewController`, `MainInteractor`, `MainView` 단방향 `State`/`Action` 전환.
- `Apps/Apple/Projects/Features/PasswordGenerator/Implementation/`: `PasswordGeneratorViewStateAction.swift` 추가 및 `PasswordGeneratorViewModel`, `PasswordGeneratorViewController`, `PasswordGeneratorInteractor`, `PasswordGeneratorView` 단방향 `State`/`Action` 전환.
- `Apps/Apple/Projects/Features/Unlock/Implementation/`: `UnlockViewStateAction.swift` 추가 및 `UnlockViewModel`, `UnlockViewController`, `UnlockInteractor`, `UnlockView` 단방향 `State`/`Action` 전환.
- `Apps/Apple/Projects/Features/VaultList/Implementation/`: `VaultListViewStateAction.swift` 추가 및 `VaultListViewModel`, `VaultListViewController`, `VaultListInteractor`, `VaultListView` 단방향 `State`/`Action` 전환 (VLT-001 원격 동기화 상태/액션 포함).
- `Apps/Apple/Projects/Features/Settings/Implementation/`: `SettingsViewStateAction.swift` 추가 및 `SettingsViewModel`, `SettingsViewController`, `SettingsInteractor`, `SettingsView` 단방향 `State`/`Action` 전환 (VLT-002 미동기화 로그아웃 확인 상태/액션 포함).
- `Apps/Apple/Projects/Features/Auth/Implementation/`: `AuthViewStateAction.swift` 추가 및 `AuthViewModel`, `AuthViewController`, `AuthInteractor`, `AuthView` 단방향 `State`/`Action` 전환.

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| RIBs + SwiftUI `State`/`Action` 단방향 통일 | 개별 클로저와 산발적인 presenter 메서드를 `request(action:)`과 `update(state:)`로 일원화하되, 기존 단위 테스트 스파이(`*PresenterSpy`)와의 하위 호환을 위해 프로토콜 기본 구현을 함께 제공한다. |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `bash Scripts/check-docs.sh` | 통과 |
| `bash Scripts/check-apple-lint.sh` | 통과 |
| `bash Scripts/check-policy-acceptance.sh` | 통과 |
| `bash Scripts/check-api-contract.sh` | 통과 |
| `bash Scripts/verify-tdd-integrity.sh` | 통과 |

## 검증하지 못한 것

1. Windows 환경에서 `xcodebuild` 직접 실행 불가: 정적 아키텍처·린트·정책·계약·TDD 무결성 스크립트 검증 및 GitHub Actions CI에서 macOS 빌드로 검증한다.

## AI의 오류와 정정

해당 없음.
