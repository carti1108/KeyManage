# 2026-11-12 · 기기 암호를 끌 때 생체 해제 무효화 정책 추가

- 이슈·PR: (이 PR)
- 커밋: (이 PR의 커밋)

> **요약** SEC-003에 기기 암호(화면 잠금)를 끄면 생체 해제용 키가 지워지고 마스터 비밀번호로 푼 뒤 생체 인증을 다시 켜야 한다는 내용을 넣었다. 정책 합격 감사 스크립트에는 이 키를 쓰는 Apple 구현이 들어오면 관련 테스트를 요구하는 규칙을 더했다.

## 사용 도구

- 도구: Claude Code (Claude Opus 5.5)
- 플러그인·스킬: `policy-manager`, `ai-usage`

## 요청

Kotlin Multiplatform 실험 브랜치에서 생체 보호 키의 접근 수준을 정하면서 생긴 결정(기기 암호가 있을 때만 쓰는 키)을 정책 SSOT에 옮겨 달라고 요청했다. 기존 규칙 8(생체 정보 변경 시 무효화)과 같은 흐름으로 쓰도록 했다.

## AI 산출물

- `docs/policies/SEC/SEC-003-unlock-and-auto-lock.md`: 규칙 8 하위 항목, '생체 해제용 키' 값 비고(iOS `kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly`, Android `setUserAuthenticationRequired(true)`), 검증 항목, 이력
- `docs/policies/CHANGELOG.md`: 한 줄 추가
- `Scripts/check-policy-acceptance.sh`: Apple 소스에 `WhenPasscodeSet`이 나타나면 테스트에 기기 암호 관련 검증을 요구하는 규칙

## 사람의 판단

- 접근 수준을 기기 암호 필수(`WhenPasscodeSetThisDeviceOnly`)로 정한 것은 사람이 실험 브랜치에서 이미 확정했다. 그래서 정책 추가 절차의 8개 카테고리 질문은 생략했다.

## 검증

| 명령 | 결과 |
| :--- | :--- |
| `bash Scripts/check-policy-acceptance.sh` | 통과(새 규칙은 아직 구현이 없어 대기 상태) |
| `bash Scripts/check-docs.sh` | 통과 |

## 검증하지 못한 것

- 실제 기기에서 기기 암호를 끌 때 키가 지워지는 동작은 이 PR 범위 밖이다(main에 해당 구현 없음).

## AI의 오류와 정정

- 처음에는 새 규칙을 9번으로 넣고 뒤 번호를 밀었다. 그러면 SEC-004·SEC-005와 고칠 수 없는 ADR-0023이 가리키는 '규칙 11'이 어긋나므로 규칙 8의 하위 항목으로 옮겼다.
- 감사 스크립트에 넣은 `"\n"`이 셸 히어닥 편집 중 실제 줄바꿈으로 바뀌어 문법 오류가 났고, 바로 고쳤다.

## 근거 링크

- [SEC-003](../../policies/SEC/SEC-003-unlock-and-auto-lock.md)
