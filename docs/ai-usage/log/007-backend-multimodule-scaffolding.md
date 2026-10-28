# 2026-10-28 · 백엔드 헥사고날 4모듈 스캐폴딩 및 domain 프레임워크 격리 검사 구축

> 요약: 이슈 #5에 따라 `Back/`에 Gradle Kotlin DSL 4모듈(`domain`, `api`, `infrastructure`, `app`)을 구성하고, `domain`에 프레임워크 의존이 들어오면 `check`가 실패하는 검사 작업을 추가했다. `./gradlew check`가 통과했다.

## 사용 도구

- 도구: Claude Code (Claude Opus 5.5)
- 플러그인/스킬: `project-docs:ai-usage`

## 요청

- Antigravity에서 만든 `feat/5-backend-scaffolding` 브랜치에서 이슈 #5 구현을 요청. 조건: Kotlin 1.9, Java 21, Spring Boot 3.4, `:domain`은 순수 Kotlin/JUnit 5만 허용하는 물리적 격리, `./gradlew check` 성공 검증.

## AI 산출물

- [`Back/settings.gradle.kts`](../../../Back/settings.gradle.kts), [`Back/build.gradle.kts`](../../../Back/build.gradle.kts): 4모듈 등록, 플러그인 버전 일괄 선언(Kotlin 1.9.25, Spring Boot 3.4.13), Spring Boot BOM 버전 통일, Java 21 toolchain.
- 모듈별 `build.gradle.kts`: 의존 방향 `api`·`infrastructure` → `domain`, `app` → 전부.
- [`Back/domain/build.gradle.kts`](../../../Back/domain/build.gradle.kts): `verifyFrameworkIsolation` 작업. 메인 클래스패스에 `org.jetbrains.kotlin`·`org.jetbrains` 외 그룹이나 다른 프로젝트 모듈이 있으면 실패하며 `check`에 연결.
- `app` 진입점 `KeyManageBackApplication.kt`, `application.yml`, 컨텍스트 로드 테스트.
- `domain`·`api`·`infrastructure` 패키지(`com.keymanage.back.*`) 자리 확보(`.gitkeep`).
- Gradle 8.12.1 wrapper와 `.gitattributes`는 ADR-0002가 계승하라고 한 `MusicSearch/Back`에서 복사.

## 사람의 판단

- 모듈 구성, 버전 기준, domain 격리 요구는 사용자가 이슈 #5와 ADR-0002·ADR-0004로 사전에 확정했다.
- AI가 제안한 격리 검사 방식(Gradle 작업으로 classpath 검사)과 이 로그 초안을 사용자가 그대로 승인했다.

## 검증

- `./gradlew check` (JDK: Corretto 21.0.12.1): **BUILD SUCCESSFUL**. `app`의 `contextLoads` 테스트 통과, `domain:verifyFrameworkIsolation` 통과.
- 격리 검사 음성 검증: `domain`에 `org.springframework:spring-context`를 임시로 추가했을 때 `:domain:check`가 실패하고 위반 의존성 8개를 나열함을 확인한 뒤 원복.

## 검증하지 못한 것

1. `bootRun`으로 실제 서버를 띄우는 것은 하지 않았다(컨텍스트 로드 테스트로만 확인).
2. `api`가 `infrastructure`를 참조하지 않는다는 규칙은 빌드 설정상 지켜져 있을 뿐, `domain`처럼 자동 검사로 막지는 않는다.
3. 이 PC에는 PATH에 잡힌 Java가 없어 `JAVA_HOME`을 직접 지정해 실행했다. 다른 환경이나 CI에서는 확인하지 않았다.

## 근거 링크

- Issue #5
- 커밋: 없음(커밋 전)
