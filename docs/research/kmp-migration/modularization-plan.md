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
  app/                                Root에서 트리를 조립하는 진입점(KeyManageApp)
  android-session/ web-session/       플랫폼 포트 구현과 앱 진입점
  web-example/                        웹 피처 Example용 번들. 시나리오로 RIB 하나를 웹 뷰에 붙인다

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

### C단계(시나리오와 Android): 피처 6개의 뷰와 Example 앱 (2026-11-20)

| 피처 | 시나리오 | Android `ui` | Android `example`에서 확인한 것(에뮬레이터 API 37) |
| :--- | :--- | :--- | :--- |
| Unlock | 10개 | `ComposeUnlockView` | 목록 10개, 틀린 비밀번호 문구, 세션 만료 알림 |
| Auth | 9개 | `ComposeAuthView` | 목록 9개, 세션 만료 안내와 함께 열림, 가입 모드 전환 |
| PasswordGenerator | 3개 | `ComposePasswordGeneratorView` | 목록 3개, 생성기 탭에서 복사 |
| VaultList | 7개 | `ComposeVaultListView` | 목록 → 상세 → 수정 → 폼 안의 생성기(자식 RIB) → 사용 → 저장 |
| Settings | 8개 | `ComposeSettingsView` | 내보내기 안내와 저장, 자동 잠금 선택 창, 계정 삭제 대화상자 |
| Main | 7개 | `ComposeMainView` | 세션 없이 연 시나리오에서 다시 연결 안내와 탭 3개(볼트·생성기·설정) 전환 |

- 시나리오마다 다 눌러 보지는 않았다. 피처마다 위 표에 적은 흐름만 확인했다.
- **Example 공통 틀**: `Apps/Android/core/example`의 `FeatureExample`. 시나리오 목록과 "RIB 하나 띄우기"를 맡고, 피처의 Example은 시나리오로 Dependency를 만드는 `launch` 함수만 쓴다(MusicSearch의 Example 템플릿 역할).
- **테스트 더블**: 테스트 파일 안에 있던 피처 전용 가짜(가짜 세션·Listener·뷰)를 각 피처의 `testing` 모듈로 옮겼다. 테스트는 같은 이름으로 가져다 쓰며 어설션은 그대로다. 공유 피처 테스트는 합계 306건으로 변함없다.
- **자식 RIB이 있는 피처**: VaultList(폼 안의 생성기)와 Main(탭 셋)의 Example은 자식의 구현 모듈과 `ui` 모듈도 함께 넣는다. 부모의 뷰는 자식 화면을 `ComposeRenderable`로 받아 그린다.
- **공유 쪽에 더한 것**: 생성기의 취약·이어지는 숫자 안내 문구(`PasswordGeneratorMessages`). Android 뷰가 쓰던 문구와 같다.
- **Android 뷰가 공유 상태에 맞춰 달라진 점**:
  - 생성기의 길이는 슬라이더 값을 공유 상태의 길이 글자로 보낸다.
  - 설정의 백업·비밀번호 변경·계정 삭제는 하위 화면 대신 대화상자다(공유 상태가 `modal` 하나다).
  - 설정에 생체 토글이 없고 잠금 화면의 생체 버튼은 늘 숨는다(android-migration-plan 3단계에서 공유 RIB에 넣는다).
  - 볼트 목록의 오프라인·읽기 전용 안내와 "잠금" 버튼은 Main과 Settings로 옮겨졌다.
- 기존 Android 모듈(uber/RIBs 기반 `interface`·`implementation`·`testing`·`tests`)과 앱(`:app`)은 그대로다. 앱을 공유 RIB으로 바꾸는 일은 android-migration-plan 4단계다.
- 남은 것: D(기존 모듈을 `core/` 아래로), E(웹 Example), F(iOS). D는 아래 기록대로 끝났다.

### D단계: 기존 모듈을 `core/` 아래로 (2026-11-21)

| 항목 | 내용 |
| :--- | :--- |
| 옮긴 것 | `crypto`·`domain`·`secure-store`·`device-store`·`server-http`를 `core/` 아래로 옮겼다. Gradle 경로는 `:core:<이름>`이다. 패키지 이름과 파일 내용은 그대로다 |
| 이름을 바꾼 것 | 조립 모듈 `feature`를 `app`으로 바꿨다(`:app`). 피처를 다 옮겨 Root RIB과 `KeyManageApp`만 남았기 때문이다 |
| 그대로 둔 것 | `android-session`, `web-session`. 웹이 읽는 번들 경로(`web-session/build/dist/...`)와 Android의 치환 좌표(`com.keymanage.shared:*`)가 바뀌지 않는다 |
| 맞춘 경로 | `Scripts/check-api-contract.sh`가 `core/domain`·`core/server-http`를 읽는다. 호환 픽스처 생성기(`core/domain/compat`)의 웹 소스 경로와 웹 문서·주석의 경로도 맞췄다. 정책 감사는 `Apps/Shared` 전체를 훑어 고칠 것이 없었다 |

- 이 문서와 다른 조사 문서의 앞선 기록에 나오는 `Apps/Shared/domain`, `Apps/Shared/feature` 같은 경로는 그때의 위치다. 고치지 않았다.
- `Scripts/check-api-contract.sh`의 Android 구역은 지운 옛 Android 어댑터 경로를 본다. 파일이 없으면 건너뛰므로 지금은 아무것도 검사하지 않는다. Android가 쓰는 서버 통신은 공유 `core/server-http`라 같은 스크립트의 공유 Kotlin 검사가 대신한다.
- 남은 것: E(웹 Example), F(iOS).

### E단계: 웹 Example (2026-11-21)

| 항목 | 내용 |
| :--- | :--- |
| 공유 모듈 | `Apps/Shared/web-example`(JS 전용). `exampleFeatures()`가 피처와 시나리오 이름을 주고, `launchExample(피처, 번호, onEvent)`가 그 시나리오의 가짜 세션으로 공유 RIB 하나를 만들어 웹 뷰(`WebXxxView`)에 붙인다. Android의 `feature/<이름>/example`과 같은 조립이다 |
| 번들 분리 | 가짜 세션과 시나리오가 배포 번들에 들어가지 않게 `web-session`과 따로 빌드한다. `web-example`은 `web-session`에 의존하므로 결과 폴더에 같은 `web-session` 모듈이 함께 나온다 |
| 웹 페이지 | `Apps/Web/example.html`과 `src/example/`. 피처별 시나리오 목록을 보이고, 고르면 실제 화면(`ui/rib/*RibScreen`)을 띄운다. 아래에 RIB이 부모에 알린 결과를 보인다 |
| 실행 | `Apps/Web`에서 `npm run example`. Example 번들을 빌드한 뒤 `vite.example.config.ts`로 5174 포트에 연다. 이 설정은 `keymanage-web-session`도 Example 번들 폴더의 것으로 돌려 Kotlin 런타임이 한 벌만 뜬다 |
| 타입 | Example 번들은 `npm run check` 때 없을 수 있어, 페이지가 쓰는 두 함수의 타입을 `src/example/exampleModule.d.ts`에 손으로 적었다. Kotlin 쪽을 고치면 함께 맞춘다 |

- 확인: 헤드리스 Chrome으로 목록의 시나리오 52개를 하나씩 눌러 화면이 뜨고 콘솔 오류가 없는 것을 보았다. 웹 `npm run check` 505건 통과. 시나리오 안의 동작(입력, 버튼)은 눌러 보지 않았다.
- 웹 잠금 화면은 생체 해제를 그리지 않으므로 Unlock의 생체 시나리오는 웹에서 보통 잠금 화면으로 보인다.
- 배포 빌드(`vite build`)의 입력은 `index.html`뿐이라 Example 페이지는 들어가지 않는다.
- 남은 것: F(iOS).

### F단계 설계: iOS (2026-11-21)

Mac에서 진행한다. 출발점은 `origin/research/kmp-migration`(`1c8aa3b`)이다. 11/13~14에 이 Mac에서 따로 해 둔 iOS 전환 커밋 14개(`../KeyManage-kmp`의 로컬 브랜치)는 원격이 리베이스되고 RIB API가 바뀌기 전 것이라 옮기지 않고 참고만 한다.

| 항목 | 결정 |
| :--- | :--- |
| Apple 쪽 Kotlin 모듈 | `Apps/Shared/apple-session`을 새로 둔다(`android-session`의 짝). 공유 세션에 넣을 iOS 포트 구현(클립보드, 네트워크 상태, 기기 기록 파일, 자동 잠금·생체 설정, 키체인 생체 키, 백업 파일 선택 창)과 진입점 `AppleKeyManageApp`을 둔다 |
| 묶음 프레임워크 2종 | 운영용은 `apple-session`이, 테스트용은 `apple-example`(새 모듈)이 만든다. 둘 다 프레임워크 이름(`baseName`)을 `KeyManageShared`로 같게 하고 정적 프레임워크로 낸다. 테스트용은 운영용 내용에 피처 구현·`testing` 모듈(가짜 세션, 시나리오)을 더 export한다. Swift 뷰 코드는 어느 쪽이든 `import KeyManageShared` 한 줄로 컴파일된다. 계획 4장의 `KeyManageSharedTesting`은 이름이 아니라 이 테스트용 묶음을 가리킨다 |
| Tuist 피처 프로젝트 | 피처마다 `Sources`(SwiftUI 뷰), `Tests`(뷰 테스트), `Example`(시나리오 목록 → RIB 하나)을 둔다. 뷰는 Kotlin 프레임워크를 링크한 프레임워크 타깃으로 만들지 않고, 쓰는 곳(앱, Example, 테스트)이 `Sources`를 직접 컴파일한다. 앱은 운영용, Example·테스트는 테스트용 묶음을 링크한다. 정적 Kotlin 프레임워크를 여러 Swift 프레임워크가 링크해 런타임이 중복되는 일을 피한다 |
| 공용 Swift | 디자인 시스템(`Core/DesignSystem`)은 Kotlin에 의존하지 않아 그대로 프레임워크로 쓴다. RIB 화면 자리(`RIBScreenSlot`)는 carti-RIBs-KMP의 `RIBsKMPSwiftUI` 패키지를 형제 폴더에서 가져온다 |
| 최소 iOS | 15.0 → 17.0. 뷰가 `@Observable`을 쓴다(`RIBsKMPSwiftUI`와 같다) |
| macOS 앱 | 이번 단계는 iOS만 옮긴다. macOS 앱은 iOS 뷰를 옮긴 뒤 같은 묶음 프레임워크로 따로 정한다. 그때까지 옛 Swift 도메인과 MicroRIBs를 지우지 않는다 |
| 순서 | ① `apple-session`과 묶음 2종 ② Unlock 뷰·Example(B단계의 iOS 짝) ③ 나머지 피처 6개 ④ 앱을 공유 Root로 전환 ⑤ 옛 Swift RIB·도메인·MicroRIBs 삭제 |

### F단계 ①②: 묶음 프레임워크와 Unlock 뷰·Example (2026-11-21)

| 항목 | 내용 |
| :--- | :--- |
| `apple-session` | iOS 포트 6종과 `AppleKeyManageApp`. 기기 기록은 Application Support 파일(백업 제외, 기기 잠김 동안 보호), 생체 키는 현재 등록된 생체로만 열리는 키체인 항목이다. 감싼 키가 있는지는 인증 창 없이 알 수 없어 UserDefaults 표시로 본다. 시뮬레이터 테스트 9건(설정 저장소, 기기 기록) |
| 묶음 2종 | `:apple-session:assembleKeyManageSharedDebugXCFramework`(운영용), `:apple-example:assembleKeyManageSharedDebugXCFramework`(테스트용). 둘 다 `KeyManageShared` |
| Swift 이름 | 피처마다 같은 이름의 `render`·`handle`·`build`가 Objective-C로 내보내질 때 `render(state____:)`처럼 선언 순서에 따라 바뀌는 이름이 됐다. 피처 `interface`의 매개변수에 `@ObjCName`을 붙여 `render(unlockState:)`, `handle(unlockAction:)`, `build(unlockListener:)`로 고정했다. Apple 내보내기에만 영향이 있다 |
| Tuist | `SharedFeature.targets(name:)`가 `<Name>Example`과 `<Name>ViewTests`를 만든다. `Core/FeatureExample`은 시나리오 목록 틀(Kotlin 의존 없음), `RIBsKMPSwiftUI`는 형제 폴더의 로컬 패키지다. 옛 Swift RIB 타깃은 그대로 둔다 |
| Unlock | `SwiftUIUnlockView`. 뷰 테스트 4건(상태 그리기, 입력 보내기, 시나리오 15개 띄우기). 시뮬레이터(iPhone 17, iOS 26)에서 Example의 시나리오 목록과 "틀린 비밀번호"(입력 → 잠금 해제 → 정책 문구, 입력 비움)를 확인했다 |

- 확인: 공유 `jvmTest`, JS 컴파일, 문서·Swift 아키텍처·Apple 린트·정책·API 계약 검사 통과.
- 시뮬레이터에 맥의 텍스트를 붙여 넣을 수 없어 입력은 화면 키보드로 했다. 나머지 시나리오 14개는 뷰 테스트가 띄우는 것만 확인했다.
- Mac에 기본 Java가 없다. Homebrew `openjdk@21`을 `JAVA_HOME`으로 지정해 빌드했다.
- 남은 것: ③ 나머지 피처 6개 ④ 앱 전환 ⑤ 옛 Swift 삭제.

### F단계 ③: 나머지 피처 5개의 뷰와 Example (2026-11-21)

| 피처 | Swift 뷰 | 뷰 테스트 | 시뮬레이터 확인 |
| :--- | :--- | :--- | :--- |
| PasswordGenerator | `SwiftUIPasswordGeneratorView` | 3건 | — |
| Auth | `SwiftUIAuthView` | 2건 | — |
| VaultList | `SwiftUIVaultListView`(폼 안의 생성기는 `RIBScreenSlot`) | 2건 | — |
| Settings | `SwiftUISettingsView`(복원·비밀번호 변경·계정 삭제는 시트, 내보내기는 알림) | 3건 | Main Example 안에서 |
| Main | `SwiftUIMainView`(탭 화면은 `RIBScreenSlot`) | 2건 | "정상 (탭 전환)": 볼트 목록 → 설정 탭 |

- **Example 조립은 Kotlin**: `apple-example`의 `AppleExamples`가 시나리오 번호로 가짜 의존성과 Listener를 만들어 Swift 뷰에 RIB을 붙인다(`web-example`과 같은 조립). Swift Example은 뷰를 만들고 받은 `RibMount`를 내리기만 한다. 웹과 조립 코드가 겹치지만 웹 쪽은 JS 전용 소스라 이번에는 합치지 않았다.
- **공유 쪽에 더한 것**: Settings `interface`에 `AutoLockChoice`·`autoLockLabel`과 상태의 `autoLockChoices`·`autoLockTimeoutLabel`. `Duration`은 Swift에서 해석할 수 없는 `Int64`로 나오므로, Swift는 글자를 이 값으로 그리고 시간 값은 그대로 돌려보낸다. JVM 테스트 2건.
- **공용 Swift 조각**: `Core/SharedUI`(오류 문구, 공유 상태가 여닫는 대화상자 바인딩). Kotlin 타입을 써서 피처 뷰와 함께 직접 컴파일한다.
- 뷰 테스트 합계 16건 통과. 각 테스트는 그 피처의 시나리오 전부를 띄운다.
- Main Example은 가짜 클립보드라 복사 뒤 남은 초 배지가 뜨지 않는다(웹·Android Example과 같다). 배지는 앱 전환(④) 뒤 실제 클립보드로 확인한다.
- 남은 것: ④ 앱을 공유 Root로 전환(Root 뷰는 앱에 둔다) ⑤ 옛 Swift 삭제.

### F단계 ④: iOS 앱을 공유 Root로 전환 (2026-11-21)

| 항목 | 내용 |
| :--- | :--- |
| 앱 타깃 | `KeyManageIOS`는 운영용 묶음, 디자인 시스템, `RIBsKMPSwiftUI`만 링크하고 피처 뷰(`Features/*/Sources`)와 `Core/SharedUI`를 함께 컴파일한다. 진입점(`Sources/iOSShared`)은 `AppleKeyManageApp`을 만들어 루트 뷰를 창에 붙이고, 화면 상태(활성·백그라운드)와 터치를 알린다. 최소 iOS는 17.0이다 |
| 서버 주소 | 빌드 설정 `KEYMANAGE_SERVER_URL`(기본 `http://localhost:8080`, Android와 같다)을 Info.plist로 넘긴다. 로컬 주소만 HTTP를 허용한다(`NSAllowsLocalNetworking`) |
| 사용자 조작 | 처음에는 루트에 SwiftUI 탭 제스처를 걸었는데, 설정 화면(List)의 버튼 탭을 가로채 "잠그기"가 눌리지 않았다. 창에 터치를 보기만 하고 바로 실패하는 인식기를 달아 고쳤다 |
| 빌드 폴더 | 앱(운영용)과 Example·뷰 테스트(테스트용)를 한 번에 빌드하면 같은 이름의 `KeyManageShared.framework`가 같은 결과 폴더에 두 번 만들어졌다. 테스트용 묶음을 쓰는 타깃만 `SharedTesting/` 결과 폴더를 쓰게 했다 |
| 스킴 | `KeyManageIOS` 스킴은 앱과 앱 UI 테스트만 빌드한다. 옛 Swift 피처 타깃은 macOS 앱이 옮겨 갈 때까지 남는다 |
| UI 테스트 | `SharedSessionFlowUITests`: 가입 → 항목 추가(저장하면 상세로 감) → 설정에서 잠그기 → 잠금 해제 → 목록 → 복사 안내. 로컬 백엔드가 없으면 건너뛴다. 앞선 실행의 기기 기록이 있으면 잠금 화면에서 로그아웃하고 시작한다 |

- 확인: 로컬 백엔드(`Back`의 `bootRun`, 저장 폴더는 임시 폴더)로 UI 테스트 통과. 뷰 테스트 16건을 앱과 같은 DerivedData에서 다시 통과.
- 확인하지 못한 것: 생체 해제(시뮬레이터에 등록된 생체가 없어 가입 직후 안내도 뜨지 않음), 백업 내보내기·복원의 파일 선택 창, 자동 잠금 시간 경과, 오프라인, 실기기.
- 남은 것: ⑤ 옛 Swift 삭제는 macOS 앱을 옮긴 뒤에 한다(F단계 설계의 macOS 줄).

### F단계 ④ macOS: macOS 앱을 공유 Root로 전환 (2026-11-21)

| 항목 | 내용 |
| :--- | :--- |
| `apple-session` | iOS·macOS 공용 코드(`appleMain`: 진입점, 설정 저장소, 네트워크 상태, 기기 기록 파일, 키체인 생체 키)와 플랫폼 코드로 나눴다. 플랫폼이 다른 포트 넷(클립보드, 백업 파일, 생체 키, 기기 기록)은 `ApplePlatformPorts`로 묶어 `iosPlatformPorts(presenter)`·`macosPlatformPorts()`가 만든다. 운영용 묶음에 `macos-arm64`가 더해졌다 |
| macOS 포트 | 클립보드는 `NSPasteboard`(마지막으로 쓴 값일 때만 비움), 백업은 저장·열기 패널, 기기 기록은 Application Support 아래 `com.keymanage.macos` 폴더(샌드박스가 없어 다른 앱과 폴더를 함께 쓰므로) |
| macOS 생체 해제 | 아직 열지 않는다. 생체 전용 키체인 항목(데이터 보호 키체인)은 서명한 앱의 키체인 권한이 있어야 만들 수 있다. 설정 스위치와 잠금 화면 버튼이 보이지 않는다. 서명·권한을 정한 뒤 `KeychainBiometricKeyStore`로 바꾼다 |
| Swift 뷰 | iOS 전용 입력 수정자를 `Core/SharedUI`의 `plainTextInput`·`emailInput`·`inlineNavigationTitle`로 감싸 같은 피처 뷰를 macOS에서도 컴파일한다. 뷰 팩토리와 루트 뷰는 `App/Sources/AppleShared`에 둔다 |
| 앱 타깃 | `KeyManageMac`도 옛 Swift RIB 대신 공유 트리로 돈다. 사용자 조작은 키 입력·클릭 이벤트를 보기만 하는 감시로 알린다. 앱 UI 테스트 파일(`UITests/Shared`)을 iOS·macOS가 함께 쓴다 |
| 테스트 | `apple-session` 테스트 9건을 iOS·macOS에서 각각 통과. 설정 저장소 테스트는 테스트마다 다른 UserDefaults 묶음을 쓰게 고쳤다(같은 묶음을 쓰면 순서에 따라 앞 테스트 값이 남았다) |

- 확인: macOS 앱 빌드, 실행 뒤 프로세스 유지와 시스템 로그 오류 없음. iOS 앱 UI 테스트(로컬 백엔드)와 뷰 테스트 16건 재통과.
- 확인하지 못한 것: macOS 화면 흐름. UI 테스트 러너가 "자동화 모드"를 켜지 못했다(이 Mac에서 사용자가 한 번 허용해야 한다). 창 내용 읽기도 보조 접근 권한이 없어 하지 못했다.
- 남은 것: ⑤ 옛 Swift RIB·도메인·MicroRIBs 삭제.

### F단계 ⑤: 옛 Swift RIB·도메인 삭제 (2026-11-21)

| 항목 | 내용 |
| :--- | :--- |
| 지운 것 | `Core/Domain`·`Core/Data`·`Core/UIAdapter`, `Features/Root`, 피처의 `Interface`·`Implementation`·`Testing`·`Tests`, 옛 앱 소스, MicroRIBs 패키지와 그 의존성 설정, `makeFeature` 템플릿 |
| 정책 감사 | `check-policy-acceptance.sh`의 Apple 부분이 지운 Swift 도메인 테스트를 읽었다. Android와 같은 함수로 바꿔 공유 Kotlin 테스트·소스와 Apple Swift 뷰를 본다. 규칙 목록은 바꾸지 않았다 |
| 검증 스크립트 | `verify-apple.sh`: 공유 Kotlin 묶음 빌드·macOS 포트 테스트 → Tuist 생성 → macOS·iOS 앱 빌드 → 디자인 시스템 테스트·피처 뷰 테스트 6개 |
| 훅·CI | pre-commit의 정책 감사 조건을 공유 Kotlin·Apple 뷰 소스로 바꿨다. CI Apple 작업은 JDK 21과 carti-RIBs-KMP(고정 커밋)를 받고, `Apps/Shared` 변경에도 돈다 |
| 문서 | [ADR-APL-002](../../../Apps/Apple/docs/adr/ADR-APL-002-shared-kotlin-ribs.md) 채택, ADR-0014·0015는 대체됨. `Apps/Apple/README.md`를 새 구조로 고쳤다 |

- 확인: 정적 검사 4종·문서 검사, 공유 묶음 빌드·macOS 포트 테스트, macOS·iOS 앱 빌드, 디자인 시스템·피처 뷰 테스트 통과.
- TDD 무결성 검사는 옛 Swift 테스트 13개 파일이 코드와 함께 지워져 걸린다. 사용자 확인 뒤 `ALLOW_TEST_EDIT=1`로 돌린다.
- CI 변경은 아직 돌지 않았다(CARTI_RIBS_TOKEN 필요, 기존과 같다).
- F단계는 여기서 끝난다. 남은 것: macOS 생체 해제(서명·키체인 권한), macOS 화면 흐름 확인(UI 자동화 권한), 공통 ADR(main에 올릴 때).

### Mac에서 한 전체 확인 (2026-11-21)

| 대상 | 확인한 것 | 결과 |
| :--- | :--- | :--- |
| 자동 테스트 | 백엔드 `check`, 공유 `jvmTest`·`jsTest`, Android `test lint assembleDebug`, 웹 `npm run check`(테스트 505건) | 통과. 웹의 `designLiterals.ts`는 이 Mac의 Node 22.17이 `.ts`를 바로 못 돌려 `--experimental-strip-types`로 돌렸다(CI는 최신 22.x라 해당 없음) |
| iOS(시뮬레이터, 로컬 백엔드) | 가입·항목 추가·잠금·해제·복사 안내, 검색·수정·삭제, 자동 잠금 "즉시"(백그라운드 뒤 잠김), 가입 직후 생체 안내 → Face ID 해제, 백업 내보내기 → 항목 삭제 → 복원, 서버 없이 기기 볼트로 해제 | `SharedSessionFlowUITests`·`SharedFeatureFlowUITests`(새 UI 테스트) 통과 |
| Android(에뮬레이터 API 37, 로컬 백엔드) | 가입, 검색·수정·삭제(취소 포함), 최근 앱 화면 가림(FLAG_SECURE, 캡처가 검게 나옴), 화면 회전(상태 유지), 자동 잠금 "즉시", 오프라인 안내, 재연결 즉시 재전송, 30초 주기 재전송, 지문 켜기·해제·취소, 지문 새 등록 시 생체 해제 꺼짐 | 통과(아래 수정 뒤) |

확인하며 고친 것:

- **재연결·30초 재전송 누락(VLT-002 규칙 6)**: 웹은 앱 쪽(`pendingSyncWiring.ts`)에서 재전송을 걸지만 Android·Apple 진입점에는 없었다. 앱이 앞으로 올 때만 보냈다. 공유 도메인에 `PendingSyncRetry`(JVM 테스트 6건)를 두고 `AndroidKeyManageApp`·`AppleKeyManageApp`에 연결했다. 에뮬레이터에서 재연결 8초 안, 서버 재시작 뒤 다음 주기에 보내는 것을 확인했다.
- **iOS 백업 저장 창이 뜨지 않고 멈춤**: 확인 알림이 닫히는 중에 그 위로 띄우려 해서 아무 일도 일어나지 않았고, 결과를 기다리는 공유 코드가 멈췄다. 닫히는 중인 화면은 건너뛰고, 창이 뜨지 못하면 실패로 끝내게 했다.
- **iOS 설정의 완료·오류 안내가 화면 밖**: 목록 맨 아래에 있어 스크롤해야 보였다. 맨 위로 옮겼다.

남은 것:

- ~~서버만 꺼진 채 저장하면 Main 오프라인 안내가 뜨지 않음~~ → 아래에서 고쳤다.
- 확인하지 못한 것: iOS Face ID 재등록 시 무효화(Android에서는 확인), macOS 화면 흐름(자동화 권한), 실기기 전부.

### 서버만 닿지 않을 때의 오프라인 안내 (2026-11-21)

- 원인: 저장이 서버에 닿지 못해 동기화 대기가 돼도 세션이 알림을 보내지 않아, Main이 대기 여부를 다시 읽지 않았다. 네트워크 끊김은 환경 알림으로 다시 읽어 안내가 떴다.
- 고친 것: `VaultSession.onPendingSyncChange`(저장이 대기가 되거나 재전송으로 풀릴 때만 알림)와 `SessionPort.onPendingSyncChange`(기본 구현은 아무것도 하지 않음, `SessionPortAdapter`가 세션에 연결). Main이 구독해 상태를 다시 읽는다. 웹·Android·Apple 모두 같은 공유 Main을 쓴다.
- 테스트: 도메인 5건(대기가 됨·바로 올라감·이미 대기·재전송으로 풀림·해제), Main 3건(안내 보임·걷힘·내려갈 때 구독 해제).
- 확인: 공유 JVM·JS, Android `test lint assembleDebug`, 웹 테스트 505건, iOS Main 뷰 테스트·앱 흐름 UI 테스트 통과. 에뮬레이터에서 네트워크는 연결된 채 서버만 내리고 저장 → 안내가 뜸 → 서버를 다시 띄우면 다음 주기(16초)에 보내고 안내가 걷힘.
