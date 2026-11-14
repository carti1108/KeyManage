### ADR-0027 타이포그래피는 역할 토큰으로 공유하고 크기는 플랫폼 단위로 매핑한다
- 상태: 채택
- 날짜: 2026-11-14
- 영역: 공통

> **요약** 글자 스타일은 `display`·`title`·`heading`·`body`·`label`·`caption` 여섯 역할의 이름과 굵기만 공유한다. 크기는 웹은 rem, Apple은 Dynamic Type 스타일로 `tokens.json`에 함께 적고 생성기가 만든다. 화면 코드는 플랫폼 기본 크기 대신 역할 토큰을 쓰고, 게이트가 이를 검사한다.

#### 배경

[ADR-0026](ADR-0026-design-token-generation.md)으로 색·간격·모서리는 `tokens.json` 하나에서 생성하게 됐지만 글자는 빠져 있다. 웹은 Tailwind 기본 클래스(`text-sm` 38곳, `text-xs` 14곳 등)를, Apple은 Dynamic Type 스타일 7종과 고정 크기 `.system(size: 13)`을 각자 쓴다. 같은 용도의 글자가 플랫폼마다 어떤 크기인지 대응표가 없다. [디자인 시스템](../design-system.md) 4장은 글자 크기가 시스템의 동적 크기를 따라야 한다고 정해 두었으므로, 색처럼 px 값 하나를 공유할 수는 없다.

#### 결정

1. 공유하는 것은 역할 이름과 굵기다. 역할은 다음 여섯 가지다.

   | 역할 | 굵기 | 웹 크기/줄 높이 | Apple 스타일 |
   | :--- | :--- | :--- | :--- |
   | `display` | 700 | 1.5rem / 2rem | `largeTitle` |
   | `title` | 600 | 1.25rem / 1.75rem | `title2` |
   | `heading` | 600 | 1.125rem / 1.75rem | `headline` |
   | `body` | 400 | 1rem / 1.5rem | `body` |
   | `label` | 400 | 0.875rem / 1.25rem | `subheadline` |
   | `caption` | 400 | 0.75rem / 1rem | `footnote` |

2. `tokens.json`의 `typography` 그룹에 DTCG `typography` 형식으로 적는다. 웹 값은 `$value`(`fontSize`·`lineHeight`는 rem, `fontWeight`)에, Apple Dynamic Type 스타일은 `$extensions["com.keymanage.apple"].textStyle`에 둔다.
3. 생성기 출력:
   - 웹: Tailwind `@theme`의 `--text-<역할>`(줄 높이·굵기 포함). 화면은 `text-label` 같은 클래스를 쓴다.
   - Apple: `DesignToken.Typography.<역할>`과 고정폭 변형 `<역할>Mono`(`Font`). iOS 15를 지원하므로 `Font.system(_:design:)`에 `.weight(_:)`를 붙인다.
4. 강조할 때 굵기만 바꾸는 것은 허용한다(웹 `font-semibold`, Apple `.weight(_:)`·`.bold()`). 비밀번호 고정폭 규칙은 그대로이며 웹은 `font-mono`, Apple은 `Mono` 변형을 쓴다.
5. 리터럴 금지 게이트를 넓힌다. 웹은 기본 크기 클래스(`text-xs`~`text-9xl`)를, Apple `Features/**`은 Dynamic Type 스타일 직접 사용(`.font(.footnote)`)과 `.system(size:)`를 금지한다.
6. 아이콘 크기(`.system(size: 48)` 등 SF Symbol 크기)는 글자가 아니므로 이 결정의 범위가 아니다. [디자인 시스템](../design-system.md) 7장에서 다룬다.

#### 고려한 대안

| 대안 | 기각 이유 |
| :--- | :--- |
| px 크기를 공유 | 4장의 동적 글자 크기 규칙과 충돌한다. Apple에서 Dynamic Type을 잃는다. |
| Apple 스타일 7종을 그대로 역할로 사용 | 웹에 1:1 크기가 없어 결국 대응표가 필요하고, `caption`과 `footnote`처럼 1pt 차이 역할이 웹에서 구분되지 않는다. |
| 플랫폼 클래스를 그대로 두고 문서로만 대응 | 지금 상태와 같다. 어긋남을 막을 장치가 없다. |

#### 결과

- 얻는 것:
  - 같은 역할의 글자가 웹과 Apple에서 같은 이름으로 쓰이고, 대응이 원본 한 곳에 있다.
  - Apple 컴포넌트의 고정 크기 글자가 Dynamic Type을 따른다.
- 비용:
  - Apple `.caption`(12pt)을 쓰던 곳은 `caption`(`footnote`, 13pt)으로 1pt 커진다.
  - 역할이 여섯 개로 줄어 세밀한 크기 차이는 굵기로만 표현한다.

#### 관련

[ADR-0025](ADR-0025-design-system-shared-contract-platform-expression.md), [ADR-0026](ADR-0026-design-token-generation.md), [디자인 시스템](../design-system.md)
