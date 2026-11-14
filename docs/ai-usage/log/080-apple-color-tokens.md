# 2026-11-14 · Apple 화면 색상 리터럴을 의미 토큰으로 교체하고 금지 게이트 추가

- 이슈·PR: (이 PR)
- 커밋: (이 PR의 커밋)

> **요약** Apple Feature 화면 4개(Auth, PasswordGenerator, Settings, VaultList)의 시스템 색(`.red`, `.orange`, `.green`, `.white`, `Color.black.opacity(0.85)` 등)을 `DesignToken.View` 의미 토큰으로 바꿨다. 주황은 브랜드 규칙(유채색은 Emerald·Red 두 가지)에 없어 웹과 같은 무채색 토큰으로 옮겼다. `Scripts/check-apple-lint.sh` 규칙 7이 `Features/**`의 색상 리터럴을 막는다(ADR-0025 결정 7).

## 사용 도구

- 도구: Claude Code (Claude Opus 5.5)

## 요청

간격·모서리에 이어 남은 색상 리터럴도 토큰으로 바꿔 달라고 했다.

## 색 대응

| 원래 | 토큰 | 근거 |
| :--- | :--- | :--- |
| `.red`, `.tint(.red)`, `Color.red.opacity(0.1)` | `destructive` (배경은 `destructive.opacity(0.1)`) | 같은 의미 |
| 비활성 버튼 `Color.gray.opacity(0.5)` | `primary.opacity(0.5)` | 웹 버튼의 비활성 표시(불투명도 50%)와 맞춤 |
| 강도 `warning` 주황, `secure` 초록 | `primary`, `accent` | 웹 `EntropyGauge`, `KMGaugeBar`와 같은 대응 |
| 설정 "주의 사항" 제목 주황 | `foreground` | 웹은 경고 문구를 무채색으로 표시 |
| 오프라인 배너 아이콘·배경 주황 | `mutedForeground`, `muted` | 같은 이유 |
| 토스트 `Color.black.opacity(0.85)`, `.white`, `.green` | `primary`, `primaryForeground`, `accent` | 다크 모드에서는 반전된 토스트가 된다 |

## 사람의 판단

- 색 대응은 되돌리기 쉬운 UI 결정이라 AI가 웹 구현과 브랜드 규칙에 맞춰 정하고 보고했다.

## 검증

| 명령 | 결과 |
| :--- | :--- |
| `bash Scripts/check-apple-lint.sh` | 통과 |
| 규칙 7 음성 시험: `.foregroundColor(.red)`, `Color.orange.opacity`, `Color(red:…)`, `"#FF0000"`, `.tint(.green)` 추가 | 5건 모두 적발. 토큰 사용, `.font(.body)`, `.redacted(reason: .placeholder)`는 통과 |

## 검증하지 못한 것

- Windows라 Apple 빌드와 화면(특히 다크 모드 토스트, 오프라인 배너)을 확인하지 못했다. CI `apple` 작업과 시뮬레이터에서 확인해야 한다.

## AI의 오류와 정정

- 079에서 규칙 6 설명을 스크립트 머리 주석에 넣었다고 적었으나 실제로는 들어가지 않았다. 이번에 규칙 6·7 설명을 함께 넣었다.

## 근거 링크

- [ADR-0025](../../adr/ADR-0025-design-system-shared-contract-platform-expression.md)
- [디자인 시스템](../../design-system.md)
