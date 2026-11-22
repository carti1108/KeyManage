### ADR-0029 클라이언트는 뷰만 플랫폼별로 두고 나머지는 Kotlin 공유 모듈을 쓴다
- 상태: 채택
- 날짜: 2026-11-22
- 영역: 공통

> **요약** 웹·데스크톱, Android, iOS, macOS 클라이언트는 뷰와 진입점만 플랫폼 코드로 두고, 도메인·화면 로직(RIB)·암호·서버 통신·기기 저장·플랫폼 기능 구현은 `Apps/Shared`의 Kotlin Multiplatform 모듈 하나를 함께 쓴다. 백엔드와 클라이언트는 계속 REST 계약으로만 만난다. ADR-0004를 대체한다.

#### 배경

[ADR-0004](ADR-0004-complete-boundary-isolation-and-dependency-inversion.md)는 모든 클라이언트와 백엔드를 서로 분리된 독립 프로젝트로 두었다. 그 결과 같은 정책(SEC-003, COM-001, VLT-001~004)과 같은 암호 규격([ADR-0011](ADR-0011-password-preprocessing-and-header-aad.md))을 TypeScript, Swift, Kotlin으로 세 번 구현하고 세 번 검증했다.

실제로 구현이 어긋났다. 재연결 시 재전송(VLT-002 규칙 6)은 웹에만 있었고, 잠글 때 키를 0으로 덮는 동작은 Android에만 있었다. 정책 하나를 바꾸면 세 구현과 세 벌의 테스트, 정책 감사 규칙을 함께 고쳐야 했다.

ADR-0004가 지키려던 것은 두 가지다. 백엔드 변경이 클라이언트로 번지지 않을 것, 화면이 네트워크 구현을 모를 것. 클라이언트끼리 코드를 나누는 것은 이 둘을 해치지 않는다.

실험 브랜치(`research/kmp-migration`)에서 네 플랫폼을 모두 옮겨 확인했다. 과정과 결과는 [조사 문서](../research/kmp-migration/)에 있다.

#### 결정

1. **백엔드 격리 유지**: 백엔드(`Back/`)와 클라이언트는 코드를 나누지 않는다. 접점은 REST JSON 계약(`docs/api-spec.md`)뿐이다.
2. **클라이언트 공유 범위**: 뷰와 진입점을 뺀 전부를 `Apps/Shared`에 둔다. 도메인, 세션, 피처 RIB(Builder·Interactor·Router·Presenter), ViewState·ViewAction, 사용자 문구, 테스트와 테스트 더블이 여기에 든다.
3. **플랫폼에 남는 것**: 뷰(React, Compose, SwiftUI), 디자인 시스템, 앱 진입점, 피처 Example 앱. 뷰는 공유 모듈의 뷰 계약을 구현하고 사용자 조작만 알린다.
4. **플랫폼 기능 위치**: 키체인·Keystore, 생체 인증, 클립보드, 기기 저장소, 파일 선택은 앱이 구현해 주입하지 않는다. 공유 모듈의 플랫폼별 소스(`androidMain`·`appleMain`·`jsMain`)에서 Kotlin으로 구현한다. 앱이 넘기는 것은 화면 정보(Activity, 창)와 생명주기뿐이다.
5. **RIB 엔진**: carti-RIBs-KMP를 쓴다. 저장소 밖 소스를 Gradle composite build로 가져온다.
6. **암호**: cryptography-kotlin으로 각 플랫폼의 OS·브라우저 구현(WebCrypto, JCA, CryptoKit·CommonCrypto)을 부른다. 공유 모듈에 얇은 암호 인터페이스를 두어 라이브러리를 바꿀 수 있게 한다. 규격은 ADR-0001·ADR-0011 그대로다.
7. **DIP 유지**: RIB과 도메인은 서버·저장소·플랫폼 기능을 포트로만 쓴다. 가짜 구현을 꽂아 서버 없이 테스트하고 Example 앱을 띄운다.
8. **플랫폼별 연결 방식**은 플랫폼 ADR이 정한다(ADR-AND-003, ADR-APL-002). 웹은 Kotlin/JS 라이브러리 묶음을 Vite가 가져온다.

#### 고려한 대안

| 대안 | 기각 이유 |
| :--- | :--- |
| ADR-0004 유지(플랫폼마다 독립 구현) | 정책 구현이 세 벌이라 이미 어긋났고, 정책 변경 비용이 플랫폼 수에 비례한다 |
| 도메인만 공유하고 화면 로직은 플랫폼별 | 잠금 시 화면 트리 해제, 가입 직후 생체 안내 같은 화면 흐름 정책이 Interactor에 있다. 이 부분과 그 테스트가 플랫폼마다 남는다 |
| 뷰까지 공유(Compose Multiplatform) | 플랫폼 디자인 지침을 따르는 화면을 플랫폼마다 만든다는 방침(ADR-0025)과 맞지 않는다. 웹은 React 화면과 디자인 토큰을 버려야 한다 |
| 플랫폼 기능을 앱이 구현해 주입 | 키체인·생체 인증 코드와 그 보안 규칙(SEC-003)이 플랫폼 언어로 다시 흩어진다 |
| 백엔드와도 모델 공유 | ADR-0004의 기각 이유가 그대로다. 백엔드 빌드와 클라이언트 빌드가 묶인다 |
| 암호를 플랫폼별 `expect`/`actual`로 직접 구현 | 규격 구현이 플랫폼 수만큼 남는다. 조사 결과는 [crypto-options](../research/kmp-migration/crypto-options.md) |

#### 결과

- 얻는 것:
  - 정책 구현, 테스트, 정책 감사 대상이 한 벌이다.
  - 새 화면이나 정책 변경을 한 번 구현하면 네 플랫폼에 적용된다. 플랫폼은 뷰만 고친다.
  - 플랫폼마다 피처 Example 앱으로 화면만 띄워 확인할 수 있다.
- 비용:
  - 모든 클라이언트 빌드가 `Apps/Shared`와 carti-RIBs-KMP에 묶인다. Apple 빌드에 JDK·Gradle이 필요하고, CI는 비공개 저장소를 받는 토큰이 필요하다.
  - 공유 코드의 결함은 네 플랫폼에 함께 나타난다.
  - 외부 라이브러리(cryptography-kotlin, Ktor)가 보안 경계 안에 들어온다.
  - 웹 번들이 커진다. 메인 JS가 358KB에서 1.40MB(gzip 390KB)가 됐다.
  - 공통 코드에는 JVM·Apple 전용 API를 쓸 수 없다. Swift로 내보내는 타입에는 이름·값 표현 제약이 있다.
  - Kotlin/Native로 C API(키체인)를 부르는 코드는 Swift보다 길고 메모리를 직접 다룬다.
  - carti-RIBs-KMP는 버전이 아니라 커밋으로 고정한다. 라이브러리 변경은 그 저장소의 절차를 따로 거친다.

#### 관련

ADR-0004(대체), [ADR-0001](ADR-0001-zero-knowledge-security-architecture.md), [ADR-0010](ADR-0010-client-composition-ios-and-shared-web.md), [ADR-0011](ADR-0011-password-preprocessing-and-header-aad.md), [ADR-0025](ADR-0025-design-system-shared-contract-platform-expression.md), [ADR-0028](ADR-0028-ktor-client-for-shared-kotlin.md), ADR-AND-003, ADR-APL-002, SEC-003, COM-001, VLT-001~004, [KMP 조사 문서](../research/kmp-migration/)
