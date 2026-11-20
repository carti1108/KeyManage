# 공유 코드와 플랫폼 뷰의 모듈화 계획

- 상태: 조사 문서(실험 브랜치 `research/kmp-migration` 전용. main 머지 여부는 전환이 끝난 뒤 정한다)
- 날짜: 2026-11-20
- 관련: [target-architecture.md](target-architecture.md), [android-migration-plan.md](android-migration-plan.md)

> **요약** MusicSearch 저장소의 모듈화 방식(Tuist, 마이크로 피처, Clean Architecture, RIBs, 피처별 Example 앱)을 KMP 구조에 옮긴다. 피처마다 공유 Kotlin 모듈 4개(`interface`·`implementation`·`testing`·`tests`)를 두고, 플랫폼에는 뷰 모듈과 그 피처만 띄우는 Example 앱을 둔다. Example의 시나리오와 테스트 더블은 공유 `testing` 모듈에 한 번만 쓴다.

---

## 1. 기준이 되는 구조 (MusicSearch)

| 요소 | MusicSearch | 역할 |
| :--- | :--- | :--- |
| 피처 타깃 | `Feature<Name>Interface`, `Feature<Name>`, `Feature<Name>Testing`, `Feature<Name>Tests`, `Feature<Name>Example` | 다른 피처는 `Interface`에만 의존한다. `Example`은 `Testing`의 더블로 그 피처만 띄운다 |
| 피처 전용 계층 | `Shared/Domain`, `Shared/Data` | 그 피처만 쓰는 유스케이스와 구현 |
| Core | `Domain`, `Infrastructure`, `DesignSystem`(Example 포함), `Util`, `Testing` | 여러 피처가 함께 쓰는 것 |
| 템플릿 | `Tuist/ProjectDescriptionHelpers/Project+Templates.swift`의 `microFeatureTargets` | 타깃 5개를 같은 규칙으로 만든다 |
| Example 앱 | 시나리오 목록(성공·빈 화면·에러·지연) → 고른 시나리오의 Dependency로 RIB 하나를 띄움 | 앱 전체를 거치지 않고 그 피처의 뷰만 시뮬레이터에 띄워, `Testing`의 목 데이터로 정해 둔 시나리오를 눈으로 확인한다 |

## 2. KMP에서의 대응

RIB 로직(Builder·Interactor·Router)이 Kotlin 공유 코드가 되므로, Tuist의 타깃 5개가 "공유 모듈 4개 + 플랫폼별 뷰·Example"로 나뉜다.

```text
Apps/Shared/                          Gradle, Kotlin 공유
  build-logic/                        모듈 종류별 convention plugin (Project+Templates의 짝)
  core/
    domain/ crypto/ secure-store/ device-store/ server-http/
    feature-base/                     피처가 함께 쓰는 포트(SessionPort 등)와 사용자 문구
    testing/                          공용 테스트 더블(가짜 세션·환경·시계)
  features/<name>/
    interface/                        Dependency·Listener·Buildable·Routing·ViewContract·ViewState/Action
    implementation/                   Builder·Interactor·Router·피처 문구
    testing/                          가짜 뷰·가짜 Listener·시나리오
    tests/                            Interactor·Router·Builder 테스트
  feature/                            Root에서 트리를 조립하는 진입점(KeyManageApp). 피처를 다 옮기면 app 조립 모듈로 이름을 바꾼다
  android-session/ web-session/       플랫폼 포트 구현과 앱 진입점

Apps/Android/feature/<name>/
  ui/                                 Compose 뷰. 공유 interface와 designsystem에만 의존한다
  example/                            설치되는 앱. 시나리오 목록 → 공유 RIB + testing 더블 + 실제 뷰

Apps/Apple/Projects/Features/<Name>/  Tuist (Mac에서)
  Sources/ Tests/ Example/            SwiftUI 뷰, 뷰 테스트, Feature<Name>Example 스킴

Apps/Web/src/features/<name>/         React 화면과 examples/<name>.html
```

| MusicSearch 타깃 | 공유(Kotlin) | Android | iOS | 웹 |
| :--- | :--- | :--- | :--- | :--- |
| `Interface` | `features/<name>/interface` | — | 묶음 프레임워크로 받음 | Kotlin/JS로 받음 |
| `Feature`(RIB 부분) | `features/<name>/implementation` | — | — | — |
| `Feature`(뷰 부분) | — | `feature/<name>/ui` | `Features/<Name>/Sources` | `features/<name>/` |
| `Testing` | `features/<name>/testing` | — | 테스트용 묶음 프레임워크로 받음 | Kotlin/JS로 받음 |
| `Tests` | `features/<name>/tests` | 뷰 테스트는 `ui`의 테스트 | `Features/<Name>/Tests` | 화면 테스트 |
| `Example` | 시나리오는 `testing`에 | `feature/<name>/example` | `Features/<Name>/Example` | `examples/<name>.html` |

## 3. 규칙

1. **의존 방향**: 다른 피처는 `interface`에만 의존한다. 부모 RIB은 자식의 `XxxBuildable`·`XxxRouting`만 알고, 구현 Builder는 조립하는 곳(`feature` 진입점, Example)이 넣는다.
2. **`interface`에 두는 것**: `XxxDependency`, `XxxListener`, `XxxBuildable`, `XxxRouting`, `XxxViewContract`(Presentable·ViewControllable·ViewFactory), `XxxViewState`·`XxxViewAction`. 플랫폼 뷰는 이 모듈만 보고 만든다.
3. **`testing`에 두는 것**: 가짜 뷰, 가짜 Listener, 그리고 **시나리오**. 시나리오는 이름(화면에 보일 제목)과 그 상황을 만드는 Dependency다. 테스트와 세 플랫폼의 Example이 같은 시나리오를 쓴다.
4. **`tests`**: 별도 모듈의 `commonTest`에 둔다(Tuist의 Tests 타깃과 같은 단위). `implementation`의 `internal` 선언은 보이지 않으므로 테스트가 필요한 것은 공개하거나 `interface`로 옮긴다.
5. **Example 앱**: 시나리오 목록 화면 하나와, 고른 시나리오로 그 피처의 RIB을 띄우는 화면으로만 이뤄진다. 실제 서버·세션을 쓰지 않는다. 목적은 사람이 기기에서 화면을 빠르게 확인하는 것이다. 자동화한 UI 테스트(Compose UI 테스트 등)는 Example에 넣지 않는다.
6. **피처 전용 Domain·Data**: 지금은 만들지 않는다. 세션이 도메인 대부분을 쓰고 있어 나누면 순환이 생긴다([domain-migration-plan.md](domain-migration-plan.md) 2장). 피처만 쓰는 유스케이스가 생기면 `features/<name>/domain`·`data`를 추가한다.
7. **convention plugin**: 대상(jvm·js·android·iOS·macOS), 툴체인, 컴파일 옵션은 `build-logic`의 플러그인이 정한다. 모듈의 `build.gradle.kts`에는 의존성만 적는다.

## 4. KMP라서 달라지는 점

| 항목 | 내용 |
| :--- | :--- |
| iOS 프레임워크 | Kotlin 프레임워크를 피처마다 따로 만들면 런타임이 중복되고 타입이 서로 맞지 않는다. 운영용 묶음 하나(`KeyManageShared`)와 테스트 더블을 포함한 묶음 하나(`KeyManageSharedTesting`, Example·테스트 전용)를 만든다. Swift 쪽 타깃 분리는 Tuist로 그대로 한다 |
| 웹 | Kotlin/JS 결과물도 하나로 낸다. Example용 진입점은 `testing`을 포함한 별도 결과물로 낸다 |
| 모듈 수 | 피처 7개 × 4 = 공유 모듈 28개가 늘어난다. 모듈마다 대상이 여러 개라 Gradle 설정·빌드 시간이 늘어난다 |

## 5. 순서

| 단계 | 내용 | 확인 기준 |
| :--- | :--- | :--- |
| A | convention plugin, `core/feature-base`, `core/testing`, Unlock 피처의 공유 4모듈 | Unlock 테스트가 새 `tests` 모듈에서 그대로 통과. 나머지 피처 테스트와 웹 검사 그대로 통과 |
| B | Android `feature/unlock/ui`와 `feature/unlock/example` | 에뮬레이터에서 Example 앱의 시나리오마다 화면이 뜬다 |
| C | 나머지 피처 6개를 같은 모양으로 옮김. 부모(Root·Main)가 자식의 `interface`만 보게 바꿈 | 옮긴 테스트 전부 통과. `feature` 모듈에는 조립 코드만 남는다 |
| D | 기존 모듈(`domain`·`crypto` 등)을 `core/` 아래로 옮기고 감사 스크립트·CI 경로를 맞춤 | 감사 스크립트 3종, 웹 검사 통과 |
| E | 웹 Example 진입점 | 브라우저에서 시나리오마다 화면이 뜬다 |
| F | iOS(Mac): 묶음 프레임워크 2종, Tuist 피처 프로젝트와 Example 스킴 | Mac에서 확인 |

A~C는 [android-migration-plan.md](android-migration-plan.md)의 3단계(공유 RIB 보강)보다 먼저 한다. 3단계가 Unlock·Settings·Root를 고치므로 나눈 뒤에 고친다.

## 6. 진행 기록

### A·B단계: Unlock 피처 (2026-11-20)

| 항목 | 결과 |
| :--- | :--- |
| convention plugin | `Apps/Shared/build-logic`의 `km.kmp.library`. 대상·툴체인·컴파일 옵션을 정한다. 기존 모듈은 플러그인을 `alias` 대신 `id`로 적용하게 바꿨다(플러그인을 build-logic이 클래스패스에 올리기 때문) |
| `core/feature-base` | 피처 공용 포트(`FeaturePorts.kt`)와 문구(`messages/`)를 옮겼다. 문구 테스트 15건 통과 |
| `features/unlock/interface` | `UnlockDependency`·`UnlockListener`·화면 계약·화면 상태를 옮기고 `UnlockBuildable`·`UnlockRouting`을 추가했다 |
| `features/unlock/implementation` | Builder·Interactor·Router. Root는 `UnlockBuildable`·`UnlockRouting`만 본다 |
| `features/unlock/testing` | `FakeUnlockView`·`FakeUnlockListener`·`FakeUnlockSession`과 시나리오 10개(`UnlockScenario`) |
| `features/unlock/tests` | 옮긴 테스트 29건 통과. 어설션 80개는 그대로다 |
| 나머지 | `feature` 모듈 테스트 262건, 웹 `npm run check` 505건, 감사 스크립트 3종 통과. 정책 감사가 중첩된 공유 모듈의 테스트도 보게 경로를 바꿨다 |
| Android `feature/unlock/ui` | `ComposeUnlockView`. 공유 `interface`와 디자인 시스템에만 의존한다 |
| Android `feature/unlock/example` | 설치되는 앱(`com.keymanage.android.feature.unlock.example`). 에뮬레이터(API 37)에서 시나리오 목록 10개가 뜨고, "틀린 비밀번호"에서 표준 문구가, "세션 만료"에서 부모 알림(`didRequireSignIn`)이 뜨는 것을 확인했다 |

- 실행: `Apps/Android`에서 `gradlew :feature:unlock:example:installDebug` 뒤 기기에서 "Unlock Example"을 연다.
- `core/testing`(공용 테스트 더블)은 아직 만들지 않았다. Unlock은 자기 가짜 세션만 쓰기 때문이다. 여러 피처가 함께 쓰는 `ShellFakes.kt`는 C단계에서 옮긴다.
- `ribs-compose` 치환은 `Apps/Shared/settings.gradle.kts`에 둔다. 같은 RIB 라이브러리 빌드를 `Apps/Android`에서 한 번 더 `includeBuild`하면 치환이 적용되지 않았다.
- 기존 Android 모듈(`feature/unlock/{interface,implementation,testing,tests}`, uber/RIBs)은 앱이 아직 쓰므로 그대로 뒀다. 앱 전환(android-migration-plan 4단계) 때 지운다.

### C단계(공유 쪽): 나머지 피처 5개 (2026-11-20)

| 항목 | 결과 |
| :--- | :--- |
| 나눈 피처 | `auth`, `generator`(PasswordGenerator), `vaultlist`, `settings`, `main`. 각각 `interface`·`implementation`·`testing`·`tests` |
| `core/testing` | 공용 테스트 더블(`FakeSession`, `FakeEnvironment`, `FakeClipboard`, `FakeBackupFiles`, `FakeRandomBytes`, 테스트 디스패처)을 옮겼다 |
| 피처의 `testing` | 가짜 뷰(`FakeAuthView` 등)를 옮겼다. `main/testing`에는 자식 화면까지 만드는 `FakeMainViews`가 있다 |
| 조립 모듈(`feature`) | Root RIB과 `KeyManageApp`, 그 테스트만 남았다. MusicSearch에서 Root가 App 타깃에 있는 것과 같다 |
| 테스트 | 모듈별 JVM 테스트 합계 306건으로 나누기 전과 같다(조립 47, 문구 15, unlock 29, auth 29, generator 19, vaultlist 50, settings 63, main 54). 옮긴 테스트의 어설션 수도 그대로다 |
| 나머지 | 웹 `npm run check` 505건, 감사 스크립트 3종, `web-session` JS 빌드, Android 앱 컴파일과 Unlock Example 빌드 통과 |

- **부모가 자식의 구현을 얻는 방법**: 자식의 Builder는 부모의 Dependency가 준다(MusicSearch의 `musicDiggingBuilder`와 같다). 다만 Main은 세션을 감싼 Dependency를 자기가 만들어 자식에 넘기므로, 속성이 아니라 함수다: `MainDependency.vaultListBuilder(dependency)`, `passwordGeneratorBuilder(dependency)`, `settingsBuilder(dependency)`, `VaultListDependency.passwordGeneratorBuilder(dependency)`. 구현은 조립 모듈의 `RootComponent`가 넣는다.
- **Routing에 올린 것**: 부모가 자식 Router에서 쓰던 기능만 계약으로 올렸다. `AuthRouting.showNotice`, `MainRouting.isFinishingSession`, `VaultListRouting.refreshFromSession`.
- 아직 안 한 것: 각 피처의 시나리오(`XxxScenario`)와 Android `ui`·`example`. 테스트 파일 안에 남아 있는 피처 전용 가짜(가짜 세션·Listener)는 시나리오를 만들 때 `testing`으로 옮긴다.
