# 2026-10-27 · shadcn/ui 기반 크로스 플랫폼 디자인 시스템 통합 및 UIKit/SwiftUI 공용 토큰 수립

> 요약: `ui-ux-pro-max-skill`을 프로젝트에 설치하고, shadcn/ui의 HSL 시멘틱 토큰과 미니멀리즘 테마를 Web, iOS(UIKit/SwiftUI 공용), Android 3대 스택에 1:1 매핑하도록 `docs/design-system.md`를 갱신했다.

## 사용 도구

- 도구: Antigravity IDE (Gemini 3.8 Flash High)
- 플러그인/스킬: `ui-ux-pro-max@ui-ux-pro-max-skill`, `project-docs:ai-usage`

## 요청

- `https://ui.shadcn.com/`을 활용한 디자인 시스템 구축 가능 여부 확인 및 명세서 갱신 요구.
- `https://github.com/nextlevelbuilder/ui-ux-pro-max-skill` 설치 및 활용 요구.
- iOS UI 프레임워크가 아직 UIKit일지 SwiftUI일지 미정인 상황을 반영할 것.

## AI 산출물

- `ui-ux-pro-max-skill` 프로젝트 설치 (`claude plugin install ui-ux-pro-max@ui-ux-pro-max-skill`).
- `scripts/search.py`를 활용한 보안 도메인 최적화 shadcn 테마(Slate Navy & Emerald Green & Swiss Style) 추출.
- [`docs/design-system.md`](../design-system.md) 갱신:
  - shadcn/ui 표준 14개 CSS 시멘틱 토큰 정의.
  - iOS의 UIKit(`UIColor`) 및 SwiftUI(`Color`) 동시 지원 바인딩 구조 수립.
  - 고정폭 모노스페이스(SF Mono / JetBrains Mono) 폰트 규격 명시.
  - shadcn 프리미티브(Input, Progress, Toast, Dialog)와 아이콘 매핑표 확정.

## 사람의 판단

- **디자인 표준 결정**: shadcn/ui의 컴포넌트 소유 및 CSS 변수 토큰 철학을 채택.
- **iOS 유연성 확보**: SwiftUI로 한정 짓지 않고 UIKit과 SwiftUI 모두 대응 가능한 `DesignToken.UI` / `DesignToken.View` 듀얼 구조로 설계.

## 검증

- `bash Scripts/check-docs.sh` 실행 결과: **문서 검사 통과**.

## 근거 링크

- 없음(커밋 전)
