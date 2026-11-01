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
| [COM-001](COM/COM-001-clipboard-auto-burn.md) | 클립보드 자동 비우기 | 확정 | 공통 예외 및 클립보드 |
| [COM-002](COM/COM-002-error-state-guidance.md) | 오류 상태 안내 정책 | 확정 | 공통 예외 및 클립보드 |

---

## 정책 틀

새 정책은 [`templates/policy.md`](../templates/policy.md)를 복사해 쓴다. 문장 규칙은 [문서 작성 형식](../README.md)을 따른다.
