# 2026-11-01 · Apple 디자인 시스템 토큰 및 5대 핵심 컴포넌트 구현 (#82)

- 이슈·PR: #82
- 커밋: 26f6bb4

> **요약** Apple 플랫폼(iOS & macOS)에 크로스 플랫폼 디자인 시스템(docs/design-system.md) 규격에 맞춘 15대 색상 토큰(Hex/RGBA 및 동적 모드 전환), SF Symbols 8종 아이콘, SF Pro/고정폭 타이포그래피, 5대 핵심 컴포넌트(KMButton, KMCard, KMPasswordField, KMGaugeBar, KMBurnBadge)를 구현했다. Argon(Medium 수준 추론) 독립 코드 리뷰어가 적발한 비밀번호 자동 재가림 타이머 생명주기(P1), 콜백 중복 발화(P2), VoiceOver A11y 누락(P2) 결함을 완치하고 Apple 6중 기계적 검증을 통과했다.

## 사용 도구

- 도구: Antigravity (Gemini), Xcode 16.4, xcodebuild
- 플러그인·스킬: `clean-architecture-layers`, `code-review`

## 요청

사용자가 "ㅇㅇ"로 이슈 #82 착수를 승인하고 "ㅇㅇ작업전엔 항상 main 최신화하고 pr전엔 항상 main에 리베이스 하고 진행해", "리뷰 서브 에이전트는 argon으로 해 medium 수준으로"를 지시하여 워크플로우에 반영했다.

## AI 산출물

- `Apps/Apple/Projects/Core/DesignSystem/Sources/DesignToken.swift`: 15대 색상 토큰(ColorToken, Dynamic PlatformColor/Color 지원), SF Symbols 8종 아이콘 상수, SF Pro 및 Monospaced 타이포그래피 토큰.
- `Apps/Apple/Projects/Core/DesignSystem/Sources/KMButton.swift`: 4대 variant, 3대 size(32, 40, 48pt), loading, disabled, action, press 피드백 ButtonStyle, VoiceOver 레이블.
- `Apps/Apple/Projects/Core/DesignSystem/Sources/KMCard.swift`: 3대 padding(12, 16, 20pt), isInteractive hover/press 애니메이션.
- `Apps/Apple/Projects/Core/DesignSystem/Sources/KMPasswordField.swift`: 마스킹/평문 토글, 복사 버튼, 평문 노출 후 10초 무조작 시 비동기 Task 기반 자동 재가림 타이머 및 중복 콜백 방지.
- `Apps/Apple/Projects/Core/DesignSystem/Sources/KMGaugeBar.swift`: 0~100점 엔트로피 게이지, 3단계 색상(Red/Sky/Emerald) 및 레이블("취약함"/"보통"/"초강력"), A11y 값 연동.
- `Apps/Apple/Projects/Core/DesignSystem/Sources/KMBurnBadge.swift`: 30초 카운트다운 진행 바, 단일 발화 보장 onBurned 콜백, A11y 안내.
- `Apps/Apple/Projects/Core/DesignSystem/Tests/`: 단위 테스트 28개 (DesignTokenTests, KMButtonTests, KMCardTests, KMPasswordFieldTests, KMGaugeBarTests, KMBurnBadgeTests).
- `Apps/Apple/docs/platform-bindings.md`: 실제 구현된 DesignToken 및 SwiftUI/UIKit 바인딩 최신화.

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 채택 | PR 제출 전 항상 main 최신 리베이스 원칙 준수 채택 |
| 채택 | 코드 리뷰 서브에이전트에 Argon(Gemini 4 Argon, Medium Reasoning) 적용 채택 |
| 채택 | 테스트 작성(RED)과 코드 리뷰(REVIEW)만 서브에이전트 위임, 구현 및 통합 검증은 메인 에이전트 단일 수행 워크플로우 채택 |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `bash Scripts/check-docs.sh` | 통과 |
| `bash Scripts/check-apple-lint.sh` | 통과 (위반 0건) |
| `bash Scripts/check-policy-acceptance.sh` | 통과 (누락 0건) |
| `bash Scripts/verify-tdd-integrity.sh` | 통과 (TDD 불변성 100%) |
| `bash Scripts/verify-apple.sh` | 통과 (모든 빌드 및 단위 테스트 28개 All Green) |

## 검증하지 못한 것

1. 실제 물리 iPhone 디스플레이 및 macOS 외장 HDR 모니터에서의 시각적 픽셀 렌더링 (macOS 호스트 단위 테스트 및 프레임 계측으로 검증함)

## AI의 오류와 정정

| 오류 | 발견 | 조치 |
| :--- | :--- | :--- |
| `KMPasswordField`의 10초 무조작 자동 재가림 타이머가 수동 함수만 있고 런타임에 구동되지 않아 평문 암호가 방치되는 결함 (P1) | Argon 코드 리뷰어 (Medium) | SwiftUI `.task(id: engine.isSecured)` 생명주기를 연동하여 평문 노출 시 10초 카운트다운 후 `@MainActor`에서 자동 재가림 처리 |
| `KMPasswordField` 뷰와 엔진에서 `onTextChange`, `onCopy` 콜백이 각각 2중으로 중복 호출되는 결함 (P2) | Argon 코드 리뷰어 (Medium) | 콜백 호출 주체를 엔진(`engine.copyAction()`, `engine.updateText()`)으로 단일화하여 중복 발화 제거 |
| `DesignToken.View`와 `UI`가 다크 모드에 고정되어 시스템 다크/라이트 테마 변경에 동적으로 반응하지 않는 결함 (P2) | Argon 코드 리뷰어 (Medium) | `PlatformColor`에 dynamic provider(`traitCollection`/`appearance`)를 적용하고 SwiftUI `Color`로 바인딩하여 동적 테마 전환 지원 |
| `KMBurnBadgeModel.tick()`이 0초 도달 이후 매 호출마다 `onBurned` 콜백을 무한 중복 실행하는 결함 (P2) | Argon 코드 리뷰어 (Medium) | `hasBurned` 가드 플래그를 추가하여 0초 도달 시 1회만 발화하도록 보장 |
| `KMButton`, `KMPasswordField`, `KMGaugeBar`, `KMBurnBadge` 전반의 VoiceOver 접근성 레이블 누락 (P2) | Argon 코드 리뷰어 (Medium) | 각 컴포넌트에 표준 `.accessibilityLabel`, `.accessibilityValue`, `.accessibilityHidden` 추가 |
