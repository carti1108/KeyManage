# KeyManage 아키텍처 결정 기록 (ADR)

이 디렉터리는 `KeyManage` 프로젝트의 유의미한 아키텍처 및 기술 결정의 배경, 대안, 트레이드오프를 보존하는 지식베이스입니다.

---

## 작성 규칙

1. **내용 불변 (Append-only)**: 한 번 채택된 ADR의 내용은 수정하지 않습니다. 결정이 바뀌면 새 ADR을 작성하고 기존 ADR의 상태를 `대체됨 (→ ADR-xxxx)`으로 변경합니다.
2. **대안 검토 필수**: "고려했던 대안과 기각 이유"가 없는 ADR은 작성할 수 없습니다.
3. **파일명 규칙**: `ADR-<번호>-<영문-슬러그>.md` (네 자리 순번, 영역 구분 없는 프로젝트 전체 순번).
4. **1결정 1파일**: 파일 하나에 하나의 결정만 작성합니다.

---

## ADR 목록

| 번호 | 제목 | 상태 | 날짜 |
|---|---|---|---|
| [ADR-0001](ADR-0001-zero-knowledge-security-architecture.md) | 영지식(Zero-Knowledge) 클라이언트 암호화 아키텍처 채택 | 채택 | 2026-10-27 |
| [ADR-0002](ADR-0002-hexagonal-multi-module-backend.md) | Kotlin/Spring Boot 기반 포트-어댑터 멀티 모듈 백엔드 채택 | 채택 | 2026-10-27 |
| [ADR-0003](ADR-0003-file-based-vault-storage-adapter.md) | 파일 기반 단일 암호화 볼트 저장소 어댑터 채택 | 채택 | 2026-10-27 |
| [ADR-0004](ADR-0004-complete-boundary-isolation-and-dependency-inversion.md) | 영역 간 완전 격리 및 백엔드 교체형 DIP 아키텍처 채택 | 채택 | 2026-10-28 |
| [ADR-0005](ADR-0005-client-only-password-generation-and-audit.md) | 비밀번호 생성·진단의 클라이언트 전용 수행 | 채택 | 2026-10-28 |
| [ADR-0006](ADR-0006-optimistic-locking-vault-sync.md) | 볼트 동기화 충돌 해결을 낙관적 잠금과 클라이언트 병합으로 전환 | 채택 | 2026-10-28 |
| [ADR-0007](ADR-0007-file-vault-atomic-write-single-instance.md) | 파일 볼트 저장소의 원자적 쓰기와 단일 인스턴스 제약 | 채택 | 2026-10-28 |
| [ADR-0008](ADR-0008-auth-hash-and-server-session.md) | 마스터 키 분리 기반 인증 해시와 서버 저장형 세션 채택 | 채택 | 2026-10-28 |
| [ADR-0009](ADR-0009-verification-delegation-pipeline.md) | AI 작업의 검증 위임 체계 (CI·뮤테이션 테스트·독립 리뷰) | 채택 | 2026-10-28 |
| [ADR-0010](ADR-0010-client-composition-ios-and-shared-web.md) | 클라이언트 구성: iOS 네이티브와 웹 코드 공유(브라우저·데스크톱) 우선 | 채택 | 2026-10-28 |

---

## ADR 틀

새로운 ADR은 아래 서식을 그대로 따릅니다:

```markdown
### ADR-<번호> 제목
- 상태: 채택
- 날짜: YYYY-MM-DD
- 배경: 왜 이 결정이 필요했는지 설명
- 결정: 무엇을 하기로 했는지 기술
- 고려했던 대안: (1) 대안 A - 기각 사유, (2) 대안 B - 기각 사유
- 결과: 이 결정으로 얻는 이점과 감수해야 할 비용/트레이드오프
- 관련: 관련 정책(SEC-xxx 등) 또는 이슈 번호
```
