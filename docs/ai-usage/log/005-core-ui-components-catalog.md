# 2026-10-27 · 5대 핵심 UI 컴포넌트 카탈로그 및 파라미터 규격 수립

> 요약: 외부 라이브러리 종속 없이 각 플랫폼(Web, iOS, Android)에서 직접 소유할 5대 핵심 컴포넌트(`Button`, `Card`, `PasswordField`, `EntropyGauge`, `BurnBadge`)의 파라미터 및 인터페이스 명세를 `docs/design-system.md`에 정의했다.

## 사용 도구

- 도구: Antigravity IDE (Gemini 3.8 Flash High)
- 플러그인/스킬: `project-docs:ai-usage`

## 요청

- 크로스 플랫폼 환경에서 UI 컴포넌트를 어떻게 구축하고 관리할지에 대한 가이드 및 컴포넌트 카탈로그 명세화 요구.

## AI 산출물

- [`docs/design-system.md`](../design-system.md)에 5대 컴포넌트 카탈로그 추가:
  - `KMButton`: `variant`(Primary/Outline/Destructive/Ghost), `size`, 로딩/비활성화 상태.
  - `KMCard`: 패딩 및 인터랙션 옵션.
  - `KMPasswordField`: 마스킹 토글, 모노스페이스 폰트, 복사 액션 및 10초 자동 마스킹 복귀 로직.
  - `KMGaugeBar`: 0~100점 3단계 색상 전이 게이지.
  - `KMBurnBadge`: 30초 카운트다운 타이머 배지.

## 사람의 판단

- **컴포넌트 구축 전략 확정**: 무거운 외부 번들 설치 대신, 5대 필수 컴포넌트를 각 플랫폼 프로젝트 내에 직접 구현하여 소유하는 shadcn/ui 방식을 승인함.

## 검증

- `bash Scripts/check-docs.sh` 실행 결과: **문서 검사 통과**.

## 근거 링크

- 없음(커밋 전)
