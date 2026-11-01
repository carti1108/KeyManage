# 공통 아키텍처 결정 기록 (ADR)

> **요약** 시스템 전체에 영향을 주는 결정의 배경, 대안, 결과를 남긴다. 한 플랫폼에만 해당하는 결정은 그 플랫폼의 `docs/adr/`에 쓴다.

---

## 작성 규칙

| 규칙 | 내용 |
| :--- | :--- |
| 고치지 않는다 | 채택한 ADR의 내용은 고치지 않는다. 결정이 바뀌면 새 ADR을 쓰고, 옛 ADR의 상태만 `대체됨 (→ ADR-xxxx)`으로 바꾼다. |
| 대안을 쓴다 | 고려한 대안과 기각 이유가 없는 ADR은 쓰지 않는다. |
| 한 결정에 한 파일 | 파일 하나에는 결정 하나만 쓴다. |
| 파일 이름 | `ADR-<네 자리 순번>-<영문-슬러그>.md`. 이 순번은 공통 ADR만 쓴다. |
| 플랫폼 ADR | 한 플랫폼에만 해당하는 결정은 그 플랫폼의 `docs/adr/`에 `ADR-WEB-NNN`·`ADR-APL-NNN`·`ADR-BE-NNN` 번호로 쓴다. 위치는 [문서 지도](../README.md)에 있다. |
| 절차 | 기록·조회·대체는 `adr-manager` 스킬 절차를 따른다. 새 ADR은 [`templates/adr.md`](../templates/adr.md)를 복사해 쓴다. |

---

## ADR 목록

플랫폼 전용 결정이던 ADR-0002·0003·0007(백엔드), ADR-0012·0013·0018~0020(웹), ADR-0014~0017(Apple)은 각 플랫폼의 `docs/adr/`로 옮겼다. 번호는 그대로이므로 이 표에는 빈 번호가 있다.

| 번호 | 제목 | 상태 | 날짜 |
|---|---|---|---|
| [ADR-0001](ADR-0001-zero-knowledge-security-architecture.md) | 영지식(Zero-Knowledge) 클라이언트 암호화 아키텍처 채택 | 채택 | 2026-10-27 |
| [ADR-0004](ADR-0004-complete-boundary-isolation-and-dependency-inversion.md) | 영역 간 완전 격리 및 백엔드 교체형 DIP 아키텍처 채택 | 채택 | 2026-10-28 |
| [ADR-0005](ADR-0005-client-only-password-generation-and-audit.md) | 비밀번호 생성·진단의 클라이언트 전용 수행 | 채택 | 2026-10-28 |
| [ADR-0006](ADR-0006-optimistic-locking-vault-sync.md) | 볼트 동기화 충돌 해결을 낙관적 잠금과 클라이언트 병합으로 전환 | 채택 | 2026-10-28 |
| [ADR-0008](ADR-0008-auth-hash-and-server-session.md) | 마스터 키 분리 기반 인증 해시와 서버 저장형 세션 채택 | 채택 | 2026-10-28 |
| [ADR-0009](ADR-0009-verification-delegation-pipeline.md) | AI 작업의 검증 위임 체계 (CI·뮤테이션 테스트·독립 리뷰) | 채택 | 2026-10-28 |
| [ADR-0010](ADR-0010-client-composition-ios-and-shared-web.md) | 클라이언트 구성: iOS 네이티브와 웹 코드 공유(브라우저·데스크톱) 우선 | 채택 | 2026-10-28 |
| [ADR-0011](ADR-0011-password-preprocessing-and-header-aad.md) | 마스터 비밀번호 전처리와 볼트 암호문 헤더 인증 | 채택 | 2026-10-29 |

| [ADR-0021](ADR-0021-local-docker-compose-demo.md) | 데모는 받은 사람의 로컬 Docker Compose로 실행하고, 데스크톱 릴리스는 `https://localhost:8443`을 서버 주소로 빌드한다 | 채택 | 2026-11-01 |
