# KeyManage 아키텍처 결정 기록 (ADR)

이 디렉터리는 `KeyManage` 프로젝트의 유의미한 아키텍처 및 기술 결정의 배경, 대안, 트레이드오프를 보존하는 지식베이스입니다.

---

## 작성 규칙

1. **내용 불변 (Append-only)**: 한 번 채택된 ADR의 내용은 수정하지 않습니다. 결정이 바뀌면 새 ADR을 작성하고 기존 ADR의 상태를 `대체됨 (→ ADR-xxxx)`으로 변경합니다.
2. **대안 검토 필수**: "고려했던 대안과 기각 이유"가 없는 ADR은 작성할 수 없습니다.
3. **파일명 규칙**: `ADR-<번호>-<영문-슬러그>.md` (네 자리 순번). 공통 ADR만 이 순번을 쓴다.
4. **1결정 1파일**: 파일 하나에 하나의 결정만 작성합니다.
5. **공통과 플랫폼 구분**: 이 폴더에는 시스템 전체에 영향을 주는 결정만 둔다. 한 플랫폼에만 해당하는 결정은 그 플랫폼의 `docs/adr/`에 `ADR-WEB-NNN`, `ADR-APL-NNN`, `ADR-BE-NNN`처럼 플랫폼 접두어 번호로 쓴다. 위치는 [문서 지도](../README.md)에 있다.

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

---

## ADR 틀

새 ADR은 [`templates/adr.md`](../templates/adr.md)를 복사해 쓴다. 채택한 ADR은 템플릿에 맞춰 고치지 않는다.
