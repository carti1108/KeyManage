# 2026-10-28 · 볼트 동기화 충돌 해결을 낙관적 잠금으로 전환

> 요약: 볼트 동기화 이슈 발행 전 검토에서 VLT-001의 Last-Write-Wins가 단일 암호문 볼트 구조에서 데이터 소실을 일으킨다는 점을 확인했다. ADR-0006으로 낙관적 잠금(`revision`)과 클라이언트 항목 병합을 채택하고, VLT-001·api-spec·vault-spec에 반영했다.

## 사용 도구

- 도구: Claude Code (Claude Opus 5.5)
- 플러그인/스킬: `policy-manager:policy-manager`, `adr-manager:adr-manager`, `issue-worktree:commit-rules`, `project-docs:ai-usage`

## 요청

- 볼트 동기화 모델과 버전 충돌 감지 유스케이스를 TDD 이슈로 발행하는 것에 대한 의견을 요청받았다.

## AI 산출물

- 검토 의견: VLT-001이 정한 Last-Write-Wins는 "충돌 감지"와 반대되는 방식이라는 점, 서버가 내용을 볼 수 없는 단일 암호문 볼트에서는 기기 간 변경이 통째로 사라진다는 점, 클라이언트 시계에 의존하는 문제를 지적했다.
- 낙관적 잠금과 클라이언트 병합 방식 제안. 함께 발견한 사항으로 볼트 항목에 삭제 표시가 없어 병합 시 지운 항목이 되살아나는 문제, `X-Vault-ID`만으로 볼트에 접근할 수 있는 인증 공백을 보고했다.
- [`docs/adr/ADR-0006-optimistic-locking-vault-sync.md`](../../adr/ADR-0006-optimistic-locking-vault-sync.md) 작성.
- [`VLT-001`](../../policies/VLT/VLT-001-cross-platform-vault-sync.md) 수정: revision 규칙, 용량 측정 기준(5,242,880바이트), 자동 병합 재시도 3회, 충돌 문구, 검증 항목.
- `api-spec.md` 2.3·2.4에 `revision`·`expectedRevision`, 409·413 응답 추가. `vault-spec.md`에 `deletedAt`과 revision 위치 명시.
- `open-questions.md`에 삭제 표시 항목의 영구 삭제 시점 등록, 정책 CHANGELOG 갱신.

## 사람의 판단

- Last-Write-Wins 유지와 낙관적 잠금 중 AI가 추천한 낙관적 잠금을 선택했다.
- 정책 수정의 8개 카테고리 제안(재시도 3회, 동일 시각 시 서버 우선, revision을 HTTP 헤더가 아닌 JSON 본문으로 주고받기 등)을 수정 없이 승인했다.
- 인증 공백은 이번 범위에서 제외하고 별도 이슈로 다루기로 했다.

## 검증

- `bash Scripts/check-docs.sh`: **문서 검사 통과**.
- 코드 변경이 없어 빌드·테스트는 하지 않았다.

## 근거 링크

- 커밋: 없음(커밋 전)
