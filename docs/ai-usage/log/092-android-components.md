# 2026-11-18 · Android KM 컴포넌트 5종 구현

- 이슈·PR: #3
- 커밋: 없음(커밋 전)

> **요약** 디자인 시스템 6.1절 계약 표에 Android 열을 추가하고 컴포넌트 계약 게이트가 Android 구현도 대조하게 했다. 그 뒤 KMButton·KMCard·KMPasswordField·KMGaugeBar·KMBurnBadge를 Compose로 구현했다. 나머지 4종은 M3 기본 컴포넌트를 쓴다고 표에 적었다.

## 사용 도구

- 도구: Claude Code(Opus 5.5), Node, Gradle
- 플러그인·스킬: 없음

## 요청

4단계 PR을 머지하고 5단계(컴포넌트)를 진행하는 요청이었다.

## AI 산출물

- `docs/design-system.md` 6.1절 표에 Android 열(KM 5종 구현, Alert·ConfirmPrompt·ModalDialog·TextField는 M3 사용)
- `check-components.mjs`: Android `core/designsystem`의 `fun KM이름(`을 표와 양방향 대조
- Compose 컴포넌트 5종(목업 `code.js` Android 수치와 6장 계약 기준), `EntropyLevel`·`KMBurnBadgeText` 같은 순수 로직
- 단위 테스트: 강도 경계(59·60·79·80), 점수 자르기, 문구, 배지 0초 하한, 자동 가림 10초, 버튼 최소 높이

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 지시 | #133 머지 후 5단계 진행 |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `node Tools/design-tokens/check-components.mjs` | 구현 전 Android 5건 실패 확인 → 구현 후 통과 |
| `./gradlew test lint assembleDebug` | 통과. 컴포넌트 로직 테스트 6개 추가 |
| `bash Scripts/check-docs.sh` | 통과 |

## 검증하지 못한 것

1. Compose UI 테스트(자동 가림 타이머, 접근성 트리)는 넣지 않았다. 화면 단계에서 계측 테스트 도입을 검토한다.
2. 에뮬레이터에서 컴포넌트 모양을 목업과 눈으로 대조하지 않았다.

## AI의 오류와 정정

| 오류 | 발견 | 조치 |
| :--- | :--- | :--- |
| 로딩 표시 색을 `Color.Unspecified`로 둠 | 자체 점검 | 버튼 글자색(`LocalContentColor`)으로 바꿈 |
