# 백엔드 아키텍처 결정 기록 (ADR)

백엔드에만 해당하는 기술·구조 결정을 모은다. 시스템 전체에 영향을 주는 결정은 공통 ADR([`docs/adr/`](../../../docs/adr/README.md))에 쓴다.

## 작성 규칙

1. 형식과 불변 원칙은 공통 ADR의 작성 규칙을 따른다. 채택한 ADR은 고치지 않고, 결정이 바뀌면 새 ADR을 쓴다.
2. 새 ADR 번호는 `ADR-BE-<세 자리 순번>`이다(`ADR-BE-001`부터 발급). 다른 플랫폼과 번호가 겹치지 않는다.
3. ADR-0002·0003·0007은 공통 순번 시절에 쓴 백엔드 결정이라 원래 번호를 그대로 쓴다.
4. 공통 정책·공통 ADR만 참조한다. 다른 플랫폼 문서는 참조하지 않는다.

## ADR 목록

| 번호 | 제목 | 상태 | 날짜 |
|---|---|---|---|
| [ADR-0002](ADR-0002-hexagonal-multi-module-backend.md) | Kotlin/Spring Boot 기반 포트-어댑터 멀티 모듈 백엔드 채택 | 채택 | 2026-10-27 |
| [ADR-0003](ADR-0003-file-based-vault-storage-adapter.md) | 파일 기반 단일 암호화 볼트 저장소 어댑터 채택 | 채택 | 2026-10-27 |
| [ADR-0007](ADR-0007-file-vault-atomic-write-single-instance.md) | 파일 볼트 저장소의 원자적 쓰기와 단일 인스턴스 제약 | 채택 | 2026-10-28 |
| [ADR-BE-001](ADR-BE-001-password-change-vault-id-swap.md) | 마스터 비밀번호 변경은 새 볼트 ID에 볼트를 먼저 쓰고, 계정 파일 교체 한 번으로 확정한다 | 채택 | 2026-11-02 |
