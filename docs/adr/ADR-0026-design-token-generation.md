### ADR-0026 디자인 토큰은 JSON 원본 하나에서 플랫폼 코드를 생성한다
- 상태: 채택
- 날짜: 2026-11-14
- 영역: 공통

> **요약** 시멘틱 토큰의 이름과 기준값은 `Tools/design-tokens/tokens.json` 하나에만 적는다. 외부 의존성 없는 Node 스크립트가 웹 CSS와 Apple Swift 파일을 만들고, CI와 pre-commit이 `--check`로 원본과 생성 파일이 어긋나지 않았는지 검사한다.

#### 배경

[ADR-0025](ADR-0025-design-system-shared-contract-platform-expression.md)는 공통 계약을 정적 게이트로 지키기로 하고, 형식은 후속 결정으로 남겼다. 지금은 색 값이 [디자인 시스템](../design-system.md), 웹 `index.css`, Apple `DesignToken.swift` 세 곳에 따로 적혀 있다. 간격 토큰은 문서에만 있어서 Apple Feature 코드에 숫자 리터럴이 45곳 들어가 있다. 값을 바꾸려면 세 곳을 손으로 고쳐야 하고, 어긋나도 알아챌 장치가 없다.

#### 결정

1. 토큰 원본은 `Tools/design-tokens/tokens.json` 하나에 둔다. 형식은 W3C 디자인 토큰 형식(DTCG, `$type`·`$value`)을 따르고, 라이트·다크 값은 한 토큰 안에 함께 적는다. 대상은 색, 간격, 모서리다.
2. 생성기는 `Tools/design-tokens/generate.mjs`다. Node 표준 라이브러리만 쓴다([`Tools/crypto-vectors`](../../Tools/crypto-vectors/README.md)와 같은 방식). 출력은 다음과 같다.
   - 웹: `Apps/Web/src/styles/tokens.css`
   - Apple: `Apps/Apple/Projects/Core/DesignSystem/Sources/Generated/DesignToken+Generated.swift`
   - Android는 앱을 도입할 때 출력을 추가한다.
3. 생성 파일은 맨 위에 직접 수정 금지 주석을 두고 커밋한다. 그래서 Apple 빌드에는 Node가 필요 없다.
4. `generate.mjs --check`를 CI와 `.githooks/pre-commit`에서 실행한다. 생성 결과가 커밋된 파일과 다르면 실패한다.
5. 생성기는 값만 맡는다. 색 파싱, `View`·`UI` 접근자, 글꼴 같은 표현층 코드는 계속 손으로 쓴다.
6. [디자인 시스템](../design-system.md) 2·3장에는 토큰 이름, 의미, 플랫폼 조정 규칙만 남기고 값은 `tokens.json`을 가리킨다.
7. Feature 코드의 리터럴 금지 게이트와 Figma 플러그인 연동은 이 결정의 범위가 아니다. 후속 작업으로 한다.

#### 고려한 대안

| 대안 | 기각 이유 |
| :--- | :--- |
| Style Dictionary(npm) | 업계 표준이지만 KeyManage의 Swift 형태(`ColorToken(lightHex:darkHex:)`)와 라이트·다크 동시 출력에는 어차피 커스텀 포맷터가 필요하다. 토큰 30개 규모에 비해 의존성과 설정이 크다. 원본을 DTCG 형식으로 두므로 나중에 옮기기는 쉽다. |
| 지금처럼 손으로 쓰고 일치 검사만 추가 | 어긋남은 잡지만 값 하나를 바꿀 때 세 곳을 고쳐야 하는 문제는 그대로다. |
| Swift나 CSS를 원본으로 두고 다른 쪽을 생성 | 한 플랫폼 코드가 다른 플랫폼의 기준이 되어 ADR-0025의 플랫폼 중립 원칙과 맞지 않는다. |
| Figma Variables를 원본으로 사용 | ADR-0025에서 기각했다. 디자이너가 없는 구조에서 두 곳을 손으로 맞춰야 한다. |

#### 결과

- 얻는 것:
  - 값을 바꿀 때 한 곳만 고친다. 웹과 Apple 값이 어긋날 수 없다.
  - Apple에 간격·모서리 토큰이 생겨 Feature 리터럴을 교체할 수 있다.
- 비용:
  - 생성기 스크립트를 직접 관리한다.
  - 토큰을 고친 뒤 `node Tools/design-tokens/generate.mjs`를 실행해야 한다. 잊으면 pre-commit과 CI가 막는다.

#### 관련

[ADR-0025](ADR-0025-design-system-shared-contract-platform-expression.md), [ADR-0010](ADR-0010-client-composition-ios-and-shared-web.md), [디자인 시스템](../design-system.md)
