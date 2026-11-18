### ADR-AND-001 Android 클라이언트는 단독 Gradle 프로젝트로 만들고, Clean Architecture·uber/RIBs·마이크로 피처 구조에 ViewState/ViewAction 패턴을 쓴다
- 상태: 채택
- 날짜: 2026-11-18
- 영역: Android

> **요약** Android 클라이언트는 `Apps/Android`의 단독 Gradle 프로젝트로 만든다. 계층은 순수 Kotlin Domain과 플랫폼 구현을 담는 Data로 나누고, 화면 흐름은 uber/RIBs로, 피처는 Interface·Implementation·Testing·Tests 모듈로 나눈다. View와 RIB 사이는 불변 ViewState와 ViewAction으로만 주고받는다.

#### 배경

[ADR-0010](../../../../docs/adr/ADR-0010-client-composition-ios-and-shared-web.md)은 Android 네이티브를 마지막 순서로 두었다. 이슈 #3에서 Android를 시작하면서 세 가지를 정해야 했다.

- 백엔드 Kotlin 코드나 다른 클라이언트와 코드를 공유할지 여부. KMP 실험은 `research/kmp-migration` 브랜치에만 있고 main에는 공유 모듈이 없다.
- 계층 경계. Apple 클라이언트는 Domain 모듈이 CryptoKit·URLSession 구현을 직접 갖고 있어 Data 계층과 경계가 흐려졌다(이슈 #128). Android는 처음부터 경계를 지켜야 한다.
- 화면 흐름 구조. Apple은 MicroRIBs와 Tuist 마이크로 피처를 쓰고, 피처마다 `ViewState`·`ViewAction` 타입을 둔다. 두 앱의 구조가 같으면 정책 구현과 테스트를 1:1로 대조할 수 있다.

#### 결정

1. **단독 프로젝트**: `Apps/Android`는 독립 Gradle(Kotlin DSL) 프로젝트이며 `Back/`이나 KMP 모듈에 의존하지 않는다. 계약은 [vault-spec](../../../../docs/vault-spec.md), [api-spec](../../../../docs/api-spec.md), `docs/policies/`, `docs/crypto-test-vectors.json`이다. 버전은 `gradle/libs.versions.toml` 하나에서 관리한다.
2. **Clean Architecture 계층**:
   - `:core:domain`: 순수 Kotlin(JVM) 모듈. 엔티티, 유스케이스, 포트 인터페이스(암호, 저장, 네트워크, 클립보드, 시계)만 둔다. `android.*`, `javax.crypto`, 네트워크·저장 라이브러리를 import하지 않는다.
   - `:core:data`: 포트 구현. javax.crypto, Android Keystore, HTTP 클라이언트, 파일 저장을 둔다.
   - `:core:designsystem`: 생성된 토큰과 KM 컴포넌트.
   - 의존 방향은 `feature → domain ← data`이고, 조립은 `:app`에서만 한다.
3. **uber/RIBs**: 화면 흐름과 수명주기는 uber/RIBs(`rib-android`, `rib-android-compose`, `rib-coroutines`)로 만든다. 피처마다 Builder·Interactor·Router를 두고, 트리는 Apple과 같게 Root → Auth/Unlock, LoggedIn → Main(VaultList·PasswordGenerator·Settings)로 한다. 자동 잠금(SEC-003) 시 LoggedIn 트리를 detach하고 메모리의 키를 0으로 덮어쓴다.
4. **마이크로 피처 모듈**: 피처마다 `:feature:<name>:interface`(Buildable·Dependency·Listener), `:implementation`, `:testing`(테스트 더블), `:tests`로 나눈다. 다른 피처는 `interface`에만 의존한다.
5. **ViewState / ViewAction**:
   - `XxxViewState`는 불변 `data class`이고, 화면에 필요한 파생 값은 계산 속성으로 둔다.
   - `XxxViewAction`은 `sealed interface`로 사용자 동작을 정의한다.
   - Compose View는 `ViewState`를 받아 그리고 `ViewAction`만 보낸다. 로직을 갖지 않는다.
   - Interactor는 `ViewAction`을 처리해 `StateFlow<ViewState>`를 갱신하고, 상태 변경은 `copy()`로만 한다.

#### 고려한 대안

| 대안 | 기각 이유 |
| :--- | :--- |
| KMP 공유 모듈(백엔드·Android 도메인 공유) | 실험이 브랜치에만 있고, main에 올리면 백엔드 빌드와 Android 빌드가 묶인다. 공유 대상인 백엔드 도메인에는 암호 코드가 없어 재사용 이득이 작다. |
| MicroRIBs를 Kotlin으로 포팅 | Apple과 이름이 1:1로 맞지만 코루틴 기반 RIB 엔진을 직접 만들고 검증해야 한다. uber/RIBs는 Compose·코루틴 모듈과 테스트 도구를 이미 제공한다(v0.16.6). |
| Jetpack Navigation + ViewModel(MVVM) | Android 관례지만 Apple의 RIB 트리·Listener 구조와 달라 두 앱의 흐름과 테스트를 대조하기 어렵다. 잠금 시 하위 트리를 한 번에 해제하는 동작도 직접 만들어야 한다. |
| 단일 `:app` 모듈 | 처음엔 단순하지만 계층 침범을 빌드가 막지 못한다. Apple에서 생긴 Domain-Data 경계 문제(이슈 #128)를 되풀이할 수 있다. |

#### 결과

- 얻는 것:
  - Domain이 순수 Kotlin이라 JVM 단위 테스트만으로 정책과 암호 로직을 검증할 수 있다.
  - 모듈 의존이 계층 침범을 빌드 단계에서 막는다.
  - Apple과 피처·RIB·ViewState 이름이 같아 정책 구현을 플랫폼 간에 대조할 수 있다.
- 비용:
  - 모듈 수가 많아 초기 Gradle 설정과 빌드 시간이 늘어난다.
  - uber/RIBs는 학습 비용이 있고, 업데이트 주기가 느려 Compose·Kotlin 버전을 따라가지 못할 수 있다.
  - 백엔드·웹과 계약 코드를 공유하지 않으므로, 계약은 문서와 테스트 벡터·계약 검사 스크립트로 맞춘다.

#### 관련

ADR-0001, ADR-0004, ADR-0010, ADR-0025, ADR-0026, SEC-003, VLT-001, 이슈 #3, 이슈 #128
