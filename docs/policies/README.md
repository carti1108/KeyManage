# KeyManage 서비스 정책 SSOT (Single Source of Truth)

이 문서는 `KeyManage` 서비스의 동작 규칙, 값과 경계, 사용자 문구, 예외 처리의 유일한 진실 공급원(SSOT)입니다.

---

## 영역과 접두어

| 접두어 | 영역 | 폴더 |
|---|---|---|
| SEC | 보안 및 마스터키 | [SEC/](SEC/README.md) |
| GEN | 비밀번호 생성 및 진단 | [GEN/](GEN/README.md) |
| VLT | 볼트 저장 및 동기화 | [VLT/](VLT/README.md) |
| COM | 공통 예외 및 클립보드 | [COM/](COM/README.md) |

---

## 정책 목록

| 번호 | 제목 | 상태 | 영역 |
|---|---|---|---|
| [SEC-001](SEC/SEC-001-client-side-zero-knowledge-encryption.md) | 클라이언트 영지식 암복호화 규격 | 확정 | 보안 및 마스터키 |
| [SEC-002](SEC/SEC-002-account-auth-and-session.md) | 계정 인증 및 세션 정책 | 확정 | 보안 및 마스터키 |
| [SEC-003](SEC/SEC-003-unlock-and-auto-lock.md) | 잠금 해제 및 자동 잠금 정책 | 확정 | 보안 및 마스터키 |
| [GEN-001](GEN/GEN-001-password-generation-and-entropy-scoring.md) | 무작위 암호 생성 및 엔트로피 점수 산출 규칙 | 확정 | 비밀번호 생성 및 진단 |
| [VLT-001](VLT/VLT-001-cross-platform-vault-sync.md) | 크로스 플랫폼 볼트 동기화 및 충돌 해결 정책 | 확정 | 볼트 저장 및 동기화 |
| [VLT-002](VLT/VLT-002-offline-pending-sync.md) | 오프라인 동기화 대기 및 재전송 정책 | 확정 | 볼트 저장 및 동기화 |
| [VLT-003](VLT/VLT-003-device-vault-persistence.md) | 기기 볼트 보존 정책 | 확정 | 볼트 저장 및 동기화 |
| [COM-001](COM/COM-001-clipboard-auto-burn-and-graceful-fallback.md) | 클립보드 30초 자동 소각 및 오류 방어 정책 | 확정 | 공통 예외 및 클립보드 |

---

## 정책 틀

새로운 정책은 아래 서식을 그대로 따릅니다:

```markdown
# <접두어>-<번호> 제목
- 상태: 확정
- 규칙: 무엇을, 언제, 어떻게 제한/보장하는지 한두 문장
- 값: 구체적인 경계값 (해당 없으면 "해당 없음")
- 적용 범위: 어떤 화면/모듈/상황
- 예외: 없음
- 사용자 문구: 제목 "..." / 본문 "..."
- 근거: 파일경로:줄번호 또는 설계 문서
- 검증 방법: 테스트 파일명 또는 QA 체크리스트
- 비고: 관련 ADR 번호 또는 참고 사항
- 이력: YYYY-MM-DD 초기 등록
```
