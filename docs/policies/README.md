# 서비스 정책

> **요약** 앱의 동작 규칙, 값과 경계, 사용자 문구, 예외의 유일한 기준이다. 코드와 정책이 다르면 정책을 기준으로 본다. 모든 플랫폼에 같은 정책이 적용된다.

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
| [SEC-004](SEC/SEC-004-master-password-change.md) | 마스터 비밀번호 변경 정책 | 확정 | 보안 및 마스터키 |
| [GEN-001](GEN/GEN-001-password-generation-and-entropy-scoring.md) | 무작위 암호 생성 및 엔트로피 점수 산출 규칙 | 확정 | 비밀번호 생성 및 진단 |
| [VLT-001](VLT/VLT-001-cross-platform-vault-sync.md) | 크로스 플랫폼 볼트 동기화 및 충돌 해결 정책 | 확정 | 볼트 저장 및 동기화 |
| [VLT-002](VLT/VLT-002-offline-pending-sync.md) | 오프라인 동기화 대기 및 재전송 정책 | 확정 | 볼트 저장 및 동기화 |
| [VLT-003](VLT/VLT-003-device-vault-persistence.md) | 기기 볼트 보존 정책 | 확정 | 볼트 저장 및 동기화 |
| [VLT-004](VLT/VLT-004-encrypted-backup-export-restore.md) | 암호화 백업 내보내기와 복원 정책 | 확정 | 볼트 저장 및 동기화 |
| [COM-001](COM/COM-001-clipboard-auto-burn.md) | 클립보드 자동 비우기 | 확정 | 공통 예외 및 클립보드 |
| [COM-002](COM/COM-002-error-state-guidance.md) | 오류 상태 안내 정책 | 확정 | 공통 예외 및 클립보드 |

---

## 정책 쓰기

1. 정책의 조회·추가·수정·폐기는 `policy-manager` 스킬 절차를 따른다.
2. 새 정책은 [`templates/policy.md`](../templates/policy.md)를 복사해 쓴다.
3. 문장 규칙은 [문서 작성 가이드](../writing-guide.md)를 따른다.
4. 한 플랫폼의 구현 중에 정책의 빈틈이 보이면, 공유 정책을 그 자리에서 고치지 않고 정책 이슈로 올린다.
5. 바뀐 내용은 [`CHANGELOG.md`](CHANGELOG.md)에 한 줄 추가한다. 아직 정하지 못한 항목은 [`open-questions.md`](open-questions.md)에 둔다.
