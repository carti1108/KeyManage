# 2026-11-14 · Apple 화면 간격·모서리 리터럴을 토큰으로 교체하고 금지 게이트 추가

- 이슈·PR: (이 PR)
- 커밋: (이 PR의 커밋)

> **요약** Apple Feature 화면 6개와 DesignSystem 컴포넌트 4개의 `spacing`·`padding`·`cornerRadius` 숫자를 `DesignToken.Spacing`·`Radius`로 바꿨다. 척도에 없는 값은 가까운 토큰으로 맞췄다. `Scripts/check-apple-lint.sh`에 규칙 6을 더해 `Features/**`에 숫자가 다시 들어오면 막는다(ADR-0025 결정 7).

## 사용 도구

- 도구: Claude Code (Claude Opus 5.5)

## 요청

토큰 생성 구조 다음 단계로, Apple Feature 코드의 리터럴 교체와 리터럴 금지 게이트를 함께 진행해 달라고 했다.

## AI 산출물

- Features(Auth, Main, PasswordGenerator, Settings, Unlock, VaultList): 숫자를 토큰으로 교체
- DesignSystem(KMBurnBadge, KMButton, KMCard, KMPasswordField): 척도와 정확히 같은 값만 교체. 컴포넌트 내부의 척도 밖 값(6, 3)은 그대로 둠
- `Scripts/check-apple-lint.sh` 규칙 6, Windows 경로 변환(`cygpath -m`)
- `docs/design-system.md` 1장 리터럴 금지 행에 게이트 위치 추가

## 사람의 판단

- 척도 밖 값을 맞추는 방식(가까운 값, 같으면 큰 쪽)은 되돌리기 쉬운 UI 결정이라 AI가 기본값으로 정하고 보고했다.

## 척도 맞춤 규칙 (Features만)

| 원래 값 | 토큰 |
| :--- | :--- |
| 간격 6, 10, 14, 18 | space2(8), space3(12), space4(16), space5(20) |
| 간격 32 (Unlock 바깥 여백) | space6(24) |
| 모서리 3, 4 | Radius.sm(6) |
| 모서리 20 (토스트) | `Capsule()` |

## 검증

| 명령 | 결과 |
| :--- | :--- |
| `bash Scripts/check-apple-lint.sh` | 통과 |
| 규칙 6 음성 시험: `spacing: 7`, `.cornerRadius(5)`, `cornerRadius: 2`, `.padding(9)` 추가 | 4건 모두 적발. `spacing: 0`, `lineSpacing: 3`은 통과 |

## 검증하지 못한 것

- Windows라 Apple 빌드·테스트와 화면 확인을 못 했다. CI `apple` 작업과 시뮬레이터에서 확인해야 한다.
- 색상 리터럴(`Color.red.opacity(0.1)`, `Color.black.opacity(0.85)` 등 7곳)은 의미 토큰이 없어 이번 범위에서 뺐다.

## AI의 오류와 정정

- 게이트를 처음 넣었을 때 위반을 하나도 잡지 못했다. 원인은 둘이었다. 정규식의 `\b`가 셸 heredoc을 거치며 백스페이스 문자로 바뀌었고, Windows Git Bash가 넘긴 `/c/...` 경로를 Python이 열지 못해 파일을 0개 읽었다. 경로 문제는 기존 규칙 4·5도 Windows에서 아무것도 검사하지 않았다는 뜻이라 `cygpath -m`으로 함께 고쳤다.

## 근거 링크

- [ADR-0025](../../adr/ADR-0025-design-system-shared-contract-platform-expression.md)
- [ADR-0026](../../adr/ADR-0026-design-token-generation.md)
