# 2026-10-27 · VLT-001 크로스 플랫폼 볼트 동기화 정책 수립

> 요약: `policy-manager`의 8대 카테고리 정석 절차를 거쳐 모든 플랫폼 간 볼트 상호 운용성, 용량 한도(5MB), 오프라인 예외 처리, 충돌 해결(Last-Write-Wins)을 명문화한 `VLT-001` 정책을 제정했다.

## 사용 도구

- 도구: Antigravity IDE (Gemini 3.8 Flash High)
- 플러그인/스킬: `policy-manager:policy-manager`, `project-docs:ai-usage`

## 요청

- 특정 플랫폼(iOS)에 편향되지 않은 모든 플랫폼 간 상호 운용성을 지적하며 정책 선행 작성 요구.
- `policy-manager` 형식에 부합하지 않던 기술 구현 혼합 접근을 비판하고 정석적인 비즈니스 정책 수립 요구.

## AI 산출물

- [`docs/policies/VLT/VLT-001-cross-platform-vault-sync.md`](../policies/VLT/VLT-001-cross-platform-vault-sync.md): 크로스 플랫폼 볼트 동기화 및 충돌 해결 정책 공식 문서.
- [`docs/policies/README.md`](../policies/README.md) 및 [`docs/policies/CHANGELOG.md`](../policies/CHANGELOG.md) 업데이트.

## 사람의 판단

- **상호 운용성 원칙**: 특정 플랫폼 전용이 아닌 모든 플랫폼(iOS, Android, Web)의 100% 대칭적 볼트 생성·수정·조회 확정.
- **용량 및 타임아웃**: 업계 표준(5MB 이하, 네트워크 타임아웃 5초) 채택.
- **오프라인 동작**: 로컬 볼트 우선 사용 및 미동기화 경고 배너 노출 결정.
- **충돌 해결**: 다중 기기 동시 수정 시 `updatedAt` 최신 기준 자동 덮어쓰기(Last-Write-Wins) 채택.
- **동기화 시점**: 주기적 폴링 대신 이벤트 기반(앱 실행 시 + 볼트 변경 즉시) 채택.

## 검증

- `bash Scripts/check-docs.sh` 실행 결과: **문서 검사 통과**.

## AI의 오류와 정정

1. **정책 층위 혼선**: 기술적 구현 세부사항(스레드 풀, Web Worker 등)을 비즈니스 정책과 혼합하여 제시했다가 사용자의 지적을 받고 순수 서비스 정책과 아키텍처 스펙을 분리함.
2. **절차 위반 교정**: AI가 임의로 카테고리 답변을 작성해 들이밀던 행태를 멈추고, `policy-manager`의 8대 카테고리 순서에 따라 질문을 통해 사용자 의사결정을 도출함.

## 근거 링크

- 없음(커밋 전)
