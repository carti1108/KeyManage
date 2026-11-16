# 2026-11-16 · iOS 화면을 iPhone 목업에 맞춤

- 이슈·PR: (이 PR)
- 커밋: (이 PR의 커밋)

> **요약** iPhone 목업(`Tools/figma-builder/code.js`)을 시안 기준으로 두고 iOS SwiftUI 화면 7개를 고쳤다. 목업에만 있던 항목 상세 화면을 새로 만들고, 잠금 화면에 계정 표시와 로그아웃을 넣었다. 목업과 정책 문구가 다르면 정책을 따랐다.

## 사용 도구

- 도구: Claude Code (Claude Opus 5.5)
- 서브에이전트: SwiftUI 뷰와 목업 비교(읽기 전용)

## 요청

iOS 화면을 목업대로 고치는 계획을 세우고 구현한다. 항목 상세는 편집 시트를 유지하지 않고 새 화면으로 만든다.

## AI 산출물

- 공용 컴포넌트: iOS 캡슐 버튼, `secondary` 변형, `isFullWidth`, 회색 채움 입력창(`kmFieldContainer()`), 계약 문서 6.2절
- 잠금 해제: 자물쇠 아이콘, 이메일, 로그아웃(`UnlockListener.unlockDidRequestLogout` → Root가 인증 화면으로), 제목을 SEC-003 문구로
- 로그인·가입: 목업 제목과 문구, 가입 화면 뒤로가기와 안내 문구
- 볼트: 이니셜 아바타 목록 행, 새 항목 상세 화면(정보 그룹, 비밀번호, 강도 게이지, 클립보드 소각 배지, 아이디 복사·항목 삭제)
- 생성기: 결과 카드 안 게이지, 옵션 그룹, 같은 너비 버튼, 화면 제목 "비밀번호 생성기"
- 설정: 계정·보안·백업·세션 제어 그룹, 이메일·클립보드 30초·AES-256-GCM 행, 계정 삭제 시트의 세로 버튼

## 사람의 판단

- 상세 화면을 새로 만들기로 했다(목록에서 바로 복사하던 흐름은 상세 안으로 옮겨짐).
- 기존 문구 테스트 두 곳(잠금 제목, 로그인·가입 문구)을 바꾸는 커밋을 `KM_ALLOW_TEST_EDIT=1`로 허용했다.

## 검증

| 명령 | 결과 |
| :--- | :--- |
| 스킴별 `xcodebuild test` (Domain, Data, UIAdapter, DesignSystem, Unlock, Main, VaultList, Settings, PasswordGenerator, Auth) | 통과 |
| `KeyManageMac` 빌드, `KeyManageIOS` 시뮬레이터 빌드 | 통과 |
| `check-swift-architecture.sh`, `check-apple-lint.sh`, `check-policy-acceptance.sh`, 컴포넌트 계약 검사 | 통과 |

## 검증하지 못한 것

- 시뮬레이터 화면 확인은 사용자가 직접 했다.
- `RootFeature` 스킴 테스트는 `AuthFeatureTesting` 모듈을 찾지 못해 기존부터 돌지 않는다. Root 변경은 macOS 앱 빌드로만 확인했다.
- 목업의 모서리 14·20·26은 토큰이 없어 `Radius.lg`(12)로 근사했다.
- 강도 게이지 라벨("취약함/보통/초강력")과 GEN-001("취약/보통/안전")의 차이는 고치지 않았다.

## 근거 링크

- [디자인 시스템](../../design-system.md)
- [SEC-003](../../policies/SEC/SEC-003-unlock-and-auto-lock.md)
