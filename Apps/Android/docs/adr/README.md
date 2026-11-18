# Android 클라이언트 아키텍처 결정 기록 (ADR)

Android 클라이언트에만 해당하는 기술·구조 결정을 모은다. 시스템 전체에 영향을 주는 결정은 공통 ADR([`docs/adr/`](../../../../docs/adr/README.md))에 쓴다.

## 작성 규칙

1. 형식과 불변 원칙은 공통 ADR의 작성 규칙을 따른다. 채택한 ADR은 고치지 않고, 결정이 바뀌면 새 ADR을 쓴다. 새 ADR은 [`docs/templates/adr.md`](../../../../docs/templates/adr.md)를 복사해 쓴다.
2. 새 ADR 번호는 `ADR-AND-<세 자리 순번>`이다(`ADR-AND-001`부터 발급). 다른 플랫폼과 번호가 겹치지 않는다.
3. 공통 정책·공통 ADR만 참조한다. 다른 플랫폼 문서는 참조하지 않는다.

## ADR 목록

| 번호 | 제목 | 상태 | 날짜 |
|---|---|---|---|
| [ADR-AND-001](ADR-AND-001-android-architecture.md) | Android 클라이언트는 단독 Gradle 프로젝트로 만들고, Clean Architecture·uber/RIBs·마이크로 피처 구조에 ViewState/ViewAction 패턴을 쓴다 | 채택 | 2026-11-18 |
