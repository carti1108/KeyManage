# 2026-11-12 · 가입·항목 저장 폼 비밀번호 강도 게이지와 다크모드 버튼 수정 (#122)

- 이슈·PR: #121, #122
- 커밋: d7314c6, 17142fc, 1ef88fd

> **요약** Apple 회원가입 화면과 항목 저장 폼에 없던 비밀번호 강도 게이지를 붙이고, 웹 가입 화면도 같은 동작으로 맞췄다. 작업 중 빌드가 깨져 있던 AuthFeature 테스트 타깃을 복구했고, 사람이 찾은 다크모드 버튼 글자색 문제를 고쳤다.

## 사용 도구

- 도구: Claude Code (Claude Opus 5.5), iOS 시뮬레이터 제어 도구
- 플러그인·스킬: `ai-usage`

## 요청

시뮬레이터에서 비밀번호 입력창을 눌러도 적합도(강도) 창이 뜨지 않는 원인을 물었고, 이를 이슈로 올린 뒤 작업하도록 요청했다. 이후 로그인 후 계정 저장 폼에도 같은 문제가 있다고 알려 범위에 넣었고, 다크모드에서 로그인 버튼 글자가 보이지 않는 문제를 추가로 보고했다.

## AI 산출물

- 이슈 #121 작성(원인, 재사용할 부품, 작업 목록, 정할 점)
- `docs/policies/SEC/SEC-002-account-auth-and-session.md`: 규칙 8(가입 화면 강도 게이지, 안내만 하고 가입은 막지 않음), 취약 경고 문구, 검증 항목, 이력 추가
- `Scripts/check-policy-acceptance.sh`: Apple·Web 가입 게이지 59/60 경계값과 문구 규칙 추가. Apple 규칙은 Auth 테스트를 읽도록 함
- Apple: `AuthViewState`·`VaultListViewState`에 점수·표시 여부 계산 추가, `AuthView`·`VaultListView`에 `KMGaugeBar`와 경고·안내 문구 연결, 테스트 7건 추가
- Web: `AuthScreen`에 `EntropyGauge`와 경고 연결, 테스트 5건 추가
- AuthFeature 테스트 타깃 복구: `AuthTesting.swift` mock 공개·MicroRIBs 속성 추가, `AuthFeatureTests.swift` mock·생성자 갱신
- `AuthView` 제출 버튼 글자·로딩 색을 `.white`에서 `primaryForeground` 토큰으로 변경
- 커밋 3개와 PR #122 작성

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 결정 먼저, 계획 먼저 | AI가 정할 점을 묻지 않고 작업에 들어가자 중단시키고, 결정과 진행 계획을 먼저 합의하도록 했다. |
| 취약 시 처리 | 게이지는 안내만 하고 가입은 막지 않는 추천안을 받아들였다. |
| 범위 확대 | 웹 가입 화면도 같이 맞추기로 했고(추천안은 Apple만), 이후 항목 저장 폼도 같은 PR에 넣기로 했다. |
| 기존 고장 처리 | 깨져 있던 AuthFeature 테스트 타깃을 이 PR에서 별도 커밋으로 고치기로 했다. |
| 다크모드 문제 발견 | 사람이 다크모드에서 직접 써 보고 버튼 글자가 보이지 않는 문제를 찾았다. |
| 화면 확인 | 게이지 표시는 사람이 시뮬레이터에서 직접 확인했다. |

## 검증

- Apple: `AuthFeature` 테스트 9건, `VaultListFeature` 테스트 19건 통과(macOS), iOS 앱 빌드 성공
- Web: vitest 762건 통과, oxlint 통과
- 정적 검사: `check-policy-acceptance`, `check-docs`, `check-swift-architecture`, `check-apple-lint`, `check-api-contract`, `verify-tdd-integrity` 통과
- 커밋별 `pre-commit`·`commit-msg`, 푸시 전 `pre-push` 훅을 bash로 직접 실행해 통과
- 화면: 사람이 시뮬레이터에서 게이지 표시를 확인했다. 다크모드 버튼 수정 후 화면은 AI가 확인하지 못했다.
- 독립 코드 리뷰: 하지 않음

## 검증하지 못한 것

1. 독립 코드 리뷰(`/code-review`)를 하지 않았다.
2. CI 결과는 PR을 연 시점에 아직 나오지 않았다.

## AI의 오류와 정정

1. 정할 점(취약 시 처리, 범위)을 사람과 합의하기 전에 worktree를 만들고 구현에 들어갔다. 사람이 중단시킨 뒤 결정과 계획을 먼저 확인받고 다시 진행했다.
2. 시뮬레이터 스크린샷이 실제 화면보다 늦게 찍히는 것을 모르고, 회원가입 모드 전환이 안 되는 버그라고 판단해 보고했다. 디버깅 중 실제로는 전환이 되고 있었다는 것을 확인하고 정정했다. 코드는 고치지 않았다.
3. 첫 커밋 때 저장소 훅에 실행 권한이 없어 git이 훅을 건너뛰었다. 커밋을 되돌린 뒤 훅을 직접 실행해 확인하고 다시 커밋했다. 커밋 요약 50자 제한에도 한 번 걸려 줄였다.

## 발견 사항

1. `.githooks/*`에 실행 권한이 없어 git이 훅을 조용히 건너뛴다. `chmod +x .githooks/*`를 별도 커밋으로 넣어야 한다.
2. `check-policy-acceptance.sh`의 Apple 감사는 `Core/Domain/Tests`만 읽어서 Feature 단위 정책 테스트를 놓친다.
3. iOS 시뮬레이터 제어 도구는 보안 입력란에 텍스트를 붙여 넣지 못하고, 스크린샷이 몇 초씩 늦게 찍힐 수 있다.

## 근거 링크

- 이슈 #121, PR #122
- 커밋 d7314c6, 17142fc, 1ef88fd
