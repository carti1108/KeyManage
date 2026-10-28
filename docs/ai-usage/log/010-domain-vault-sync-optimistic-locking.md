# 2026-10-28 · 볼트 동기화 도메인 모델 및 낙관적 잠금 저장 유스케이스 TDD 구현

> 요약: 이슈 #9에 따라 `:domain`에 볼트 모델, 저장소 포트, 조회·저장 유스케이스를 순수 Kotlin으로 TDD 구현했다. revision이 다르면 충돌, 5MB를 넘으면 거절하며, 테스트 13개와 프레임워크 격리 검사가 통과했다.

## 사용 도구

- 도구: Claude Code (Claude Opus 5.5)
- 플러그인/스킬: `superpowers:test-driven-development`, `issue-worktree:commit-rules`, `project-docs:ai-usage`

## 요청

- 문서 PR(#11) 머지 후 이슈 #9의 TDD 구현 진행을 요청받았다.

## AI 산출물

- `Back/domain/src/main/kotlin/com/keymanage/back/domain/vault/`: `VaultId`, `Revision`, `EncryptedVault`, `StoredVault`, `VaultRepository`, `GetVaultUseCase`, `SaveVaultUseCase`, `VaultConflictException`, `VaultTooLargeException`.
- `Back/domain/src/test/kotlin/com/keymanage/back/domain/vault/`: 테스트 13개(첫 저장, 충돌, revision 증가, 동시 저장, 용량 경계값, 조회)와 테스트용 `InMemoryVaultRepository`.
- 설계: 저장소 포트에 무조건 덮어쓰기 메서드를 두지 않고 revision 비교 후 교체만 제공해, 유스케이스에서 조회 후 저장 경쟁 조건이 생기지 않게 했다. 패키지 구조와 예외·유스케이스 형식은 ADR-0002가 계승하도록 한 `MusicSearch/Back` 관례를 따랐다.

## 사람의 판단

- 이슈 #9의 범위(모델, 포트, 유스케이스, 테스트 목록)를 사전에 승인했고, 구현 진행을 지시했다.
- 커밋을 구현과 기록으로 나누는 분할 방식을 선택했다.

## 검증

- RED: 본문을 `TODO()`로 둔 뼈대에서 13개 중 12개 실패 확인(통과한 1개는 상수 확인 테스트).
- GREEN: `./gradlew :domain:check` 13개 통과, `verifyFrameworkIsolation` 통과, 컴파일 경고 없음.
- 동시 저장 테스트 5회 반복 실행 모두 통과.
- `./gradlew check`(전체 모듈): BUILD SUCCESSFUL.

## 검증하지 못한 것

1. 동시 저장 테스트는 유스케이스가 원자적 포트를 쓰는지를 검증한다. 실제 파일 저장소 어댑터의 원자성은 어댑터 구현 이슈에서 따로 검증해야 한다.

## 근거 링크

- Issue #9
- 커밋: 없음(커밋 전)
