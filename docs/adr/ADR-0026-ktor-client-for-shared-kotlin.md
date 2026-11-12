### ADR-0026 공유 Kotlin 모듈의 HTTP 클라이언트로 Ktor Client를 쓴다
- 상태: 채택
- 날짜: 2026-11-12
- 영역: 공통

> **요약** 공유 Kotlin 도메인(`Apps/Shared`)의 서버 포트(`AuthApi`·`VaultApi`) 구현은 공통 코드 하나로 두고, HTTP는 Ktor Client와 플랫폼별 엔진(JS fetch, Android OkHttp, iOS Darwin)으로 보낸다.

#### 배경

[KMP 도메인 이전 계획](../research/kmp-migration/domain-migration-plan.md)은 플랫폼마다 뷰만 남기고 도메인·RIB을 Kotlin `commonMain` 하나로 공유한다. 0~5단계에서 도메인과 세션을 옮겼고, 세션은 서버를 `AuthApi`·`VaultApi` 포트로만 쓴다. 이제 이 포트를 실제 HTTP로 구현해야 한다.

지금 서버 통신은 플랫폼마다 따로 있다. 웹은 `fetch`로 쓴 `Apps/Web/src/adapters/http/`, Apple은 `URLSession`으로 쓴 코드다. 응답 형식 검사, 오류를 `ServerError`(코드·상태·`Retry-After`)·`NetworkError`·`UnexpectedResponseError`로 바꾸는 규칙, 30초 시간 초과, 리다이렉트 거절, 쿠키 없이 Bearer 토큰만 보내기는 플랫폼마다 같아야 한다. 같은 규칙을 플랫폼 수만큼 다시 쓰면 어긋나기 쉽다.

이 결정은 새 외부 의존성(Ktor)을 들이므로 공통 ADR로 남긴다. 실험 브랜치(`research/kmp-migration`)의 결정이며, 이 브랜치를 main에 합칠 때 함께 들어간다.

#### 결정

1. 새 모듈 `Apps/Shared/server-http`의 `commonMain`에 `AuthApi`·`VaultApi`의 HTTP 구현을 둔다. 응답 검사와 오류 변환은 이 공통 코드 한 곳에만 있다.
2. HTTP는 Ktor Client(`ktor-client-core`)로 보낸다. 엔진은 대상마다 고른다.
   - JS(브라우저·데스크톱 웹뷰): `ktor-client-js`(fetch)
   - Android: `ktor-client-okhttp`
   - iOS·macOS: `ktor-client-darwin`
   - JVM: 테스트와 로컬 통합 테스트용(`ktor-client-cio`)
3. JSON은 이미 쓰는 kotlinx-serialization의 `JsonElement`로 직접 읽고 쓴다. Ktor의 ContentNegotiation 플러그인은 쓰지 않는다. 응답 필드 검사를 원본처럼 직접 하기 위해서다.
4. 원본(웹 어댑터)의 보안 규칙을 그대로 지킨다. 리다이렉트는 따라가지 않고, 쿠키 저장 플러그인은 쓰지 않으며(JS 엔진은 `credentials: omit`), 요청마다 30초 시간 초과를 둔다. 서버 주소는 HTTPS 출처(localhost만 HTTP)만 받는다.
5. 테스트는 Ktor `MockEngine`으로 하고, 엔진 없이 공통 코드만 검사한다.

#### 고려한 대안

| 대안 | 기각 이유 |
| :--- | :--- |
| 플랫폼별 `expect`/`actual`로 HTTP를 직접 구현(fetch, OkHttp, URLSession) | 시간 초과·리다이렉트 거절·오류 본문 읽기를 플랫폼마다 다시 써야 해, 이 결정이 막으려는 어긋남이 다시 생긴다. 플랫폼마다 따로 테스트해야 한다 |
| HTTP를 앱이 주입(공통 코드에는 "요청 보내기" 포트만 두고 각 앱이 구현) | 응답 검사·오류 변환은 공통에 둘 수 있지만, 전송 계층(시간 초과, 리다이렉트, 자격 증명)이 앱마다 다시 생긴다. 앱이 늘 때마다 같은 코드를 또 쓴다 |

#### 결과

- 얻는 것: 서버 통신 규칙을 한 곳에서 한 번 테스트한다. 새 플랫폼(Android)은 엔진 하나만 고르면 된다.
- 비용: 새 외부 의존성(Ktor)과 그 버전 관리가 생긴다. 웹 번들 크기가 늘어난다. 엔진별 동작 차이(시간 초과 예외 종류, 리다이렉트 설정)는 공통 코드에서 다시 묶어야 한다.

#### 관련

[ADR-0004](ADR-0004-complete-boundary-isolation-and-dependency-inversion.md)(DIP), [ADR-0008](ADR-0008-auth-hash-and-server-session.md)(인증 해시와 서버 세션), [api-spec](../api-spec.md) 1장·2장, [KMP 도메인 이전 계획](../research/kmp-migration/domain-migration-plan.md) 6단계
