# 2026-10-28 · 파일 기반 볼트 저장소 어댑터 TDD 구현 및 원자성 검증

> 요약: 이슈 #13에 따라 ADR-0007(볼트별 잠금, 임시 파일 ATOMIC_MOVE, 단일 인스턴스)을 먼저 기록한 뒤, `VaultId` UUID 검증과 `FileSystemVaultRepository`를 TDD로 구현하고 `:app`에 조립했다. 잠금을 제거하면 동시성 테스트가 실패하는 것까지 확인했다.

## 사용 도구

- 도구: Claude Code (Claude Opus 5.5)
- 플러그인/스킬: `adr-manager:adr-manager`, `superpowers:test-driven-development`, `issue-worktree:commit-rules`, `project-docs:ai-usage`

## 요청

- #9 머지 후 추천 순서에 따라 파일 저장소 어댑터 작업 진행을 요청받았다.

## AI 산출물

- [`docs/adr/ADR-0007-file-vault-atomic-write-single-instance.md`](../../adr/ADR-0007-file-vault-atomic-write-single-instance.md): 원자적 쓰기 방식과 단일 인스턴스 제약, 기각한 대안(OS 파일 잠금, 직접 덮어쓰기, RDBMS 전환).
- 이슈 #13 본문 작성.
- `:domain` `VaultId.of()`: UUID 자릿수 검사, 소문자 정규화, `InvalidVaultIdException`.
- `:infrastructure` `FileSystemVaultRepository`: 볼트별 잠금, 임시 파일 기록 후 `force`와 `ATOMIC_MOVE` 교체, NIO 스트림 읽기.
- `:app` `VaultConfiguration`, `keymanage.vault.storage-dir` 설정, `.gitignore`에 `data/` 추가.
- 테스트: `VaultIdTests` 6개, `FileSystemVaultRepositoryTests`(동시 저장 20회 반복 포함), `VaultWiringTests`.

## 사람의 판단

- 저장 위치, 파일 형식, 원자성 방식, `VaultId` 검증을 도메인에 두는 안을 승인했다.
- 원자성 방식을 ADR로 남기는 것과 이슈 #13 초안을 승인했다.
- 구현 중 AI가 정한 대문자 UUID 소문자 정규화와 `force(true)` 적용을 검토 후 승인했다.
- 커밋을 도메인 변경, 어댑터 구현, 기록으로 나누는 분할 방식을 선택했다.

## 검증

- RED: 뼈대(`TODO()`) 상태에서 도메인·어댑터·조립 테스트 실패 확인.
- GREEN: `./gradlew check` 47개 통과, `verifyFrameworkIsolation` 통과, 경고 없음.
- 동시 저장 테스트(8개 스레드) 20회 반복 모두 통과. 볼트별 잠금을 임시로 제거하면 20회 모두 실패함을 확인한 뒤 원복.
- 테스트 실행 후 저장 폴더가 저장소 밖에 남지 않음 확인.

## 검증하지 못한 것

1. 실제 정전·프로세스 강제 종료 상황에서의 파일 무결성은 재현해 보지 않았다(`force`와 `ATOMIC_MOVE` 적용으로 대응).
2. Linux 등 다른 OS에서의 실행은 확인하지 않았다(Windows에서만 실행).

## 근거 링크

- Issue #13
- 커밋: `f5e6818`(ADR-0007), 나머지는 커밋 전
