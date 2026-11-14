# 디자인 토큰 생성기

시멘틱 디자인 토큰(색, 간격, 모서리)의 값을 [`tokens.json`](tokens.json) 한 곳에 두고, 웹과 Apple 코드를 여기서 만듭니다([ADR-0026](../../docs/adr/ADR-0026-design-token-generation.md)). 토큰 이름과 의미는 [디자인 시스템](../../docs/design-system.md) 2·3장을 따릅니다.

## 구성

| 파일 | 역할 |
| :--- | :--- |
| `tokens.json` | 토큰 원본. W3C 디자인 토큰 형식(`$type`·`$value`)이고, 색은 `light`·`dark` 값을 함께 적는다 |
| `generate.mjs` | Node 표준 라이브러리만 쓰는 생성기. 원본 형식을 검사한 뒤 아래 두 파일을 쓴다 |

| 생성 파일 | 내용 |
| :--- | :--- |
| [`Apps/Web/src/styles/tokens.css`](../../Apps/Web/src/styles/tokens.css) | `:root`·`.dark` CSS 변수와 Tailwind `@theme inline` |
| [`Apps/Apple/.../Generated/DesignToken+Generated.swift`](../../Apps/Apple/Projects/Core/DesignSystem/Sources/Generated/DesignToken+Generated.swift) | `DesignToken.Colors`·`Spacing`·`Radius` 값 |

생성 파일은 직접 고치지 않습니다. 색 파싱, `View`·`UI` 접근자, 글꼴처럼 값이 아닌 코드는 각 플랫폼 소스에 손으로 씁니다.

## 사용법 (저장소 루트에서)

```bash
node Tools/design-tokens/generate.mjs            # tokens.json을 고친 뒤 생성 파일 갱신
node Tools/design-tokens/generate.mjs --check    # 생성 파일이 원본과 같은지만 검사
```

`--check`는 CI의 `design-tokens` 작업과 `.githooks/pre-commit`에서 돕니다. 원본이나 생성 파일을 고친 커밋에서 둘이 어긋나면 실패합니다.
