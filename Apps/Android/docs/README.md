# Android 클라이언트 문서

> **요약** Android 클라이언트는 `Apps/Android`의 단독 Gradle 프로젝트다. 구조는 [ADR-AND-001](adr/ADR-AND-001-android-architecture.md)을 따른다. 공통 정책과 공통 ADR은 루트 [`docs/`](../../../docs/README.md)를 따른다.

## 빌드와 검사

`Apps/Android`에서 실행한다. JDK 21과 Android SDK가 필요하고, SDK 위치는 `local.properties`의 `sdk.dir`에 적는다(커밋하지 않는다).

```bash
./gradlew test lint assembleDebug
```

CI의 `android` 잡이 같은 명령을 돌린다.

## 모듈

| 모듈 | 내용 |
| :--- | :--- |
| `:app` | Activity와 앱 조립. 피처 implementation에 의존하는 유일한 모듈 |
| `:core:domain` | 순수 Kotlin. 엔티티, 유스케이스, 포트 인터페이스 |
| `:core:data` | 포트 구현(javax.crypto, Keystore, 네트워크, 저장) |
| `:core:designsystem` | 테마와 KM 컴포넌트 |
| `:core:rib` | uber/RIBs와 ViewState/ViewAction 연결(`ViewStateStore`, `ViewActionStream`) |
| `:feature:<이름>:interface` | Buildable, Dependency, Listener |
| `:feature:<이름>:implementation` | Builder, Interactor, Router, View, ViewState/ViewAction |
| `:feature:<이름>:testing` | 다른 모듈이 쓰는 테스트 더블 |
| `:feature:<이름>:tests` | 그 피처의 단위 테스트 |

빌드 설정은 `build-logic`의 convention 플러그인(`km.jvm.library`, `km.android.library`, `km.android.compose`, `km.android.application`)으로 모은다. 버전은 `gradle/libs.versions.toml`에서만 바꾼다.

## 문서

| 문서 | 내용 |
| :--- | :--- |
| [`platform-bindings.md`](platform-bindings.md) | 암호 라이브러리, 색상 토큰, 글꼴, 아이콘 이름 |
| [`adr/`](adr/README.md) | Android ADR. 새 결정은 `ADR-AND-NNN` |
