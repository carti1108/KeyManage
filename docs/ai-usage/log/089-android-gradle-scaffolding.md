# 2026-11-18 · Android Gradle 스캐폴딩과 CI 잡 추가

- 이슈·PR: #3
- 커밋: 없음(커밋 전)

> **요약** ADR-AND-001대로 `Apps/Android`에 Gradle 프로젝트, convention 플러그인, core 모듈 4개, Root 피처 4개 모듈, 앱 모듈을 만들고 CI에 `android` 잡을 추가했다. Root RIB으로 uber/RIBs와 Compose, ViewState/ViewAction 연결이 빌드되고 테스트되는 것을 확인했다.

## 사용 도구

- 도구: Claude Code(Opus 5.5), Gradle 9.7.1(로컬 캐시), Android SDK 37
- 플러그인·스킬: 없음

## 요청

이슈 #3 계획의 2단계(스캐폴딩과 CI)를 진행하는 요청이었다.

## AI 산출물

- `gradle/libs.versions.toml`: AGP 9.5.1, Kotlin 2.4.21, Compose BOM 2026.09.00, uber/RIBs 0.17.0(Maven Central 메타데이터로 확인한 최신 안정 버전)
- `build-logic` convention 플러그인 4개: `km.jvm.library`, `km.android.library`, `km.android.compose`, `km.android.application`
- `:core:domain`(순수 Kotlin, 암호 포트와 `EncryptedVaultPayload`), `:core:data`, `:core:designsystem`(임시 테마), `:core:rib`(`ViewStateStore`, `ViewActionStream`)
- `:feature:root:{interface,implementation,testing,tests}`와 `MainActivity`(RibActivity)
- `data_extraction_rules.xml`: 앱 데이터를 클라우드 백업과 기기 이전에서 제외
- CI `android` 잡(`./gradlew test lint assembleDebug`)

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 지시 | 2단계 시작 |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `./gradlew test assembleDebug` | 통과, 단위 테스트 4개 |
| `./gradlew lint` | 오류 0, 경고 3(Gradle 새 버전, 앱 아이콘 없음 등) |
| `bash Scripts/check-docs.sh` | 통과 |

## 검증하지 못한 것

1. 에뮬레이터·기기에서 앱을 실행해 보지 않았다. Root 화면은 자리표시 텍스트만 있다.
2. CI 러너에서의 첫 빌드(SDK 37 플랫폼 내려받기 포함)는 PR CI에서 확인한다.

## AI의 오류와 정정

| 오류 | 발견 | 조치 |
| :--- | :--- | :--- |
| `ViewActionStream`을 구독자 없는 `SharedFlow`로 만들어 구독 전 동작이 버려지는 구조였음 | 테스트 작성 중 AI가 발견 | 버퍼가 있는 `Channel`로 바꿈 |
| `kotlin-test`만 넣어 `@Test`를 찾지 못함 | 첫 테스트 컴파일 | `kotlin-test-junit`으로 교체 |
| `android.os.Bundle`로 `didBecomeActive`를 재정의함 | 컴파일 | uber/RIBs의 `com.uber.rib.core.Bundle`로 교체 |
| `local.properties`의 Windows 경로 이스케이프 오류 | Gradle·lint | `C\:/...` 형식으로 수정 |
