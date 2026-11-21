# Android 클라이언트 문서

> **요약** Android 클라이언트는 Compose 뷰와 앱 진입점만 갖고, 도메인과 RIB은 `Apps/Shared`의 공유 Kotlin 모듈을 composite build로 가져온다. 구조는 [ADR-AND-003](adr/ADR-AND-003-shared-kotlin-ribs.md)을 따른다. 공통 정책과 공통 ADR은 루트 [`docs/`](../../../docs/README.md)를 따른다.

## 빌드와 검사

`Apps/Android`에서 실행한다. JDK 21과 Android SDK가 필요하다. `Apps/Shared`가 쓰는 carti-RIBs-KMP 소스도 필요한데, 저장소 옆 폴더(`../carti-RIBs-KMP`)에 두거나 `CARTI_RIBS_DIR` 환경 변수로 위치를 알린다. SDK 위치는 `local.properties`의 `sdk.dir`에 적는다(커밋하지 않는다).

```bash
./gradlew test lint assembleDebug
```

CI의 `android` 잡이 공유 모듈 JVM 테스트와 같은 명령을 돌린다.

## 모듈

| 모듈 | 내용 |
| :--- | :--- |
| `:app` | `MainActivity`와 프로세스 단위 조립(`KeyManageProcess`) |
| `:core:designsystem` | 테마와 KM 컴포넌트 |
| `:core:example` | 피처 Example 앱이 함께 쓰는 틀(`FeatureExample`) |
| `:feature:<이름>:ui` | 공유 ViewContract를 구현하는 Compose 뷰 |
| `:feature:<이름>:example` | 공유 Testing 시나리오로 그 피처 화면만 띄우는 앱 |

도메인, 피처 RIB(interface·implementation·testing·tests), Android 플랫폼 구현(`android-session`)은 `Apps/Shared`에 있다. 공유 모듈 테스트는 `Apps/Shared`에서 `./gradlew jvmTest`로 돌린다.

빌드 설정은 `build-logic`의 convention 플러그인(`km.android.library`, `km.android.compose`, `km.android.application`)으로 모은다. 버전은 `gradle/libs.versions.toml`에서만 바꾼다.

## 문서

| 문서 | 내용 |
| :--- | :--- |
| [`platform-bindings.md`](platform-bindings.md) | 암호 라이브러리, 색상 토큰, 글꼴, 아이콘 이름 |
| [`adr/`](adr/README.md) | Android ADR. 새 결정은 `ADR-AND-NNN` |
