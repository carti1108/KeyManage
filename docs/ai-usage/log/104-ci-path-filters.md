# 2026-11-19 · CI 잡을 바뀐 경로로 고르기

- 이슈·PR: 없음
- 커밋: 없음(커밋 전)

> **요약** PR마다 10개 잡을 모두 돌리던 CI에 `changes` 잡을 두어, 바뀐 경로에 해당하는 플랫폼 잡만 돌게 했다.

## 사용 도구

- 도구: Claude Code(Opus 5.5)
- 플러그인·스킬: 없음

## 요청

Android만 바꾼 PR이 macOS 러너 배정 지연으로 막혀, 관련 없는 플랫폼 잡을 건너뛰게 해 달라는 요청이었다.

## AI 산출물

- `.github/workflows/ci.yml`: `git diff`로 바뀐 경로를 보는 `changes` 잡과 backend·android·web·integration·desktop·apple 잡의 `if:` 조건

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 지시 | 경로 조건 추가 승인 |
| AI 기본값 | 외부 액션 없이 `git diff`로 판단. docs·crypto-vectors·design-tokens는 가벼워서 항상 실행. main 푸시와 워크플로 변경은 전체 실행. |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| YAML 파싱 | 통과 |
| 경로 정규식 로컬 시뮬레이션 | Android 변경 시 android만 참 |

## 검증하지 못한 것

1. 이 PR은 워크플로를 바꾸므로 전체 잡이 돈다. 건너뛰기 동작은 다음 플랫폼 단독 PR에서 확인한다.
