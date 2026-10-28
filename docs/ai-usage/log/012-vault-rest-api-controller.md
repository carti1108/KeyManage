# 2026-10-28 · 볼트 조회·저장 REST API 구현 및 오류 응답 매핑

> 요약: 이슈 #15에 따라 api-spec을 먼저 보완(404·400·500 본문, 2.4 응답의 `updatedAt` 제거, 요청 본문 6MB 상한)한 뒤, 볼트 조회·저장 컨트롤러와 오류 변환, 본문 크기 제한 필터를 TDD로 구현했다. 실제 서버를 띄워 7개 시나리오를 확인했다.

## 사용 도구

- 도구: Claude Code (Claude Opus 5.5)
- 플러그인/스킬: `superpowers:test-driven-development`, `issue-worktree:commit-rules`, `project-docs:ai-usage`

## 요청

- 인증보다 컨트롤러를 먼저 진행할지에 대한 의견을 요청받았고, 컨트롤러 이슈 진행을 지시받았다.

## AI 산출물

- 순서 판단 의견: 다른 AI가 제시한 "도메인 먼저" 논거는 도메인과 저장소가 이미 완료된 현재 상황과 맞지 않고, 충돌 규칙을 "같거나 작으면 409"로 잘못 기술했다고 지적했다. 클라이언트 개발을 실제 서버로 진행할 수 있고 인증 도입 시 수정 범위가 작다는 이유로 컨트롤러 우선을 권했다.
- api-spec 보완안 4건(응답 `updatedAt` 제거, 오류 본문 정의, 6MB 본문 상한, 페이로드 처리 방식)과 이슈 #15 본문.
- `Back/api/.../vault/VaultController.kt`, `Back/api/.../error/`(`ApiError`, `ApiException`, `ErrorBody`, `ApiExceptionHandler`, `RequestBodyLimitFilter`).
- 테스트: `VaultApiTests` 16개, `:app`의 `VaultApiIntegrationTests`.

## 사람의 판단

- 인증보다 컨트롤러를 먼저 하는 순서를 선택했다.
- api-spec 보완안 4건과 이슈 초안을 승인했다.
- 앞으로 커밋은 매번 묻지 않고 성격별 분할 방식으로 진행하도록 지시했다.

## 검증

- RED: 뼈대 상태에서 API 테스트 16개, 통합 테스트 1개 실패 확인.
- GREEN: `./gradlew check` 64개 통과, 경고 없음.
- 실제 서버 기동 후 curl 확인: 없는 볼트 404, 첫 저장 200, 조회 200(페이로드 동일), 오래된 revision 409(`currentRevision` 포함), 잘못된 ID 400, 7MB 유효 JSON은 chunked와 Content-Length 모두 413이며 저장되지 않음. 서버 로그에 오류 없음.

## 검증하지 못한 것

1. 인증이 없어 `X-Vault-ID`만 알면 접근할 수 있다. 로컬 개발 전용이며 #10 전까지 배포하지 않는다.
2. iOS·Android·Web 실제 클라이언트로 호출해 보지 않았다.

## AI의 오류와 정정

1. 저장 요청 DTO의 `expectedRevision`을 null 불가 `Long`으로 선언해, 필드가 빠지면 0으로 채워져 "첫 저장"으로 처리되는 결함이 있었다. 먼저 작성한 "필수 필드 누락 시 400" 테스트가 이를 잡았고, null 허용으로 받아 누락을 거절하도록 고쳤다.

## 근거 링크

- Issue #15
- 커밋: `f718e26`(api-spec 보완), 나머지는 커밋 전
