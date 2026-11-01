# Apple 클라이언트 아키텍처 결정 기록 (ADR)

Apple 클라이언트에만 해당하는 기술·구조 결정을 모은다. 시스템 전체에 영향을 주는 결정은 공통 ADR([`docs/adr/`](../../../../docs/adr/README.md))에 쓴다.

## 작성 규칙

1. 형식과 불변 원칙은 공통 ADR의 작성 규칙을 따른다. 채택한 ADR은 고치지 않고, 결정이 바뀌면 새 ADR을 쓴다.
2. 새 ADR 번호는 `ADR-APL-<세 자리 순번>`이다(`ADR-APL-001`부터 발급). 다른 플랫폼과 번호가 겹치지 않는다.
3. ADR-0014~0017은 공통 순번 시절에 쓴 Apple 결정이라 원래 번호를 그대로 쓴다.
4. 공통 정책·공통 ADR만 참조한다. 다른 플랫폼 문서는 참조하지 않는다.

## ADR 목록

| 번호 | 제목 | 상태 | 날짜 |
|---|---|---|---|
| [ADR-0014](ADR-0014-apple-multiplatform-microribs.md) | microRIBs 기반 Apple 멀티플랫폼(iOS·macOS) 아키텍처 및 형제 계층 구조 채택 | 채택 | 2026-10-30 |
| [ADR-0015](ADR-0015-tuist-micro-features-modularization.md) | Tuist 기반 Micro-Features 5계층 모듈화 아키텍처 채택 | 채택 | 2026-10-30 |
| [ADR-0016](ADR-0016-apple-collaboration-experiment-delegation-and-role-separation.md) | Apple 클라이언트 협업 방식 비교 실험 (일괄 위임 + 역할 분리) | 채택 | 2026-10-30 |
| [ADR-0017](ADR-0017-single-agent-vs-subagent-experiment.md) | 단일 에이전트 vs 서브에이전트 비교 실험 (Antigravity·Gemini 고정) | 채택 | 2026-10-30 |
