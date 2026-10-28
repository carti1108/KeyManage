# 2026-10-28 · 계정 인증 도메인·저장소 TDD 구현 (Argon2id, 서버 비밀키)

> 요약: 이슈 #18에 따라 계정·세션·로그인 시도 제한을 `:domain`에 순수 Kotlin으로, 계정·세션 파일 저장소와 Argon2id 해시, 서버 비밀키 로더를 `:infrastructure`에 TDD로 구현하고 `:app`에 조립했다. 전체 124개 테스트가 통과했다.

## 사용 도구

- 도구: Claude Code (Claude Opus 5.5)
- 플러그인/스킬: `superpowers:test-driven-development`, `issue-worktree:commit-rules`, `project-docs:ai-usage`

## 요청

- 인증 설계 문서(PR #17) 머지 후 이슈 #18 진행을 요청받았다. 이번 작업부터 위험도가 낮은 항목(커밋, 로그, 테스트 방식 등)은 확인 없이 진행하고, 정책·ADR·보안 결정·새 외부 의존성만 확인받기로 합의했다.

## AI 산출물

- `:domain` `auth` 패키지: `Email`(정규화·254자), `AuthHash`(32바이트, `toString` 마스킹), `KdfParams`, `ServerSecret`(HMAC), `SessionTokens`, 포트 3개(`AccountRepository`, `SessionRepository`, `PasswordHasher`), `SessionIssuer`, `LoginAttemptLimiter`, 유스케이스 5개(prelogin·가입·로그인·로그아웃·토큰 인증), 도메인 오류 6종.
- `:infrastructure`: `FileSystemAccountRepository`(파일 이름은 이메일 SHA-256), `FileSystemSessionRepository`(16진수 64자 키만 허용), `Argon2AuthHashHasher`, `ServerSecretLoader`(환경 변수 우선, 없으면 생성·보관), 볼트 저장소에서 분리한 공용 `AtomicFiles`.
- `:app` `AuthConfiguration`, `keymanage.auth.storage-dir`·`registration-enabled` 설정, 테스트가 `build/` 아래만 쓰도록 한 테스트 설정.
- Argon2id 구현 선택지 3개(spring-security-crypto + Bouncy Castle, Bouncy Castle 단독, argon2-jvm) 비교와 추천.
- 테스트: 도메인 32개, 인프라 27개(동시 가입 10회 반복 포함), 조립 1개.

## 사람의 판단

- 확인 범위를 줄이는 운영 방식에 동의했다.
- Argon2id 구현으로 spring-security-crypto + Bouncy Castle 조합을 승인했다. Bouncy Castle은 Spring Boot BOM이 관리하지 않아 Maven Central의 최신 안정 버전 1.86으로 고정했다.

## 검증

- RED: 본문을 `TODO()`로 둔 뼈대에서 도메인 인증 테스트 32개, 인프라 27개, 조립 1개 실패 확인.
- GREEN: `./gradlew check` 124개 통과, `verifyFrameworkIsolation` 통과(도메인은 JDK `javax.crypto`만 사용), 경고 없음.
- 계정 저장소의 이메일별 잠금을 임시로 제거하면 동시 가입 테스트 10회가 모두 실패함을 확인한 뒤 원복.
- 테스트 실행 후 `app/data`가 생기지 않고 `app/build/test-data`에만 기록됨을 확인.

## 검증하지 못한 것

1. Argon2id(19MiB) 해시 1회의 실제 소요 시간과 동시 로그인 시 메모리 사용량은 측정하지 않았다.
2. 로그인 시도 제한 상태는 메모리에만 있어 서버를 재시작하면 초기화된다(단일 인스턴스 전제, ADR-0007).
3. 인증 API와 볼트 API 토큰 전환은 후속 이슈 #19에서 한다. 현재 볼트 API는 여전히 `X-Vault-ID`로 접근한다.

## 근거 링크

- Issue #18
- 커밋: 없음(커밋 전)
