# 2026-11-02 · Apple VLT-004 암호화 백업 내보내기와 복원 클라이언트 영지식 백업 (#101)

- 이슈·PR: #101
- 커밋: 9b43a86

> **요약** VLT-004(암호화 백업 내보내기와 복원) 및 vault-spec.md 6장, ADR-0022에 따라 Apple 도메인 계층에 클라이언트 영지식 암호화 백업 엔진을 구현했다. 마스터 비밀번호 기반 PBKDF2(600,000회) + HKDF + AES-256-GCM(`keymanage-backup-YYYYMMDD.json`) 직렬화/복호화, 5MB+64KB 파일 한도 및 5MB 볼트 한도 검증, 백업 시점 되돌림 및 새 항목 보존 규칙(`restoreItems`), 타임스탬프 단조 증가(`max(now, existing.effectiveDate + 1s)`), 읽기 전용 볼트 복원 거절, Web-Apple 교차 플랫폼 상호 운용성 검증을 TDD로 완성했다. Argon(Medium 수준) 독립 코드 리뷰어가 요구한 5MB 복원 용량 한도 검증(P2), 읽기 전용 볼트 복원 거절 단위 테스트(P2), 민감 키 메모리 즉시 소거(`memset_s`), 그레고리력 고정, LocalizedError 연동을 완치하고 기계적 6중 검증을 통과했다.

## 사용 도구

- 도구: Antigravity (Gemini), Xcode 16.4, xcodebuild, Tuist 4.x
- 플러그인·스킬: `clean-architecture-layers`, `modern-swift-testing`, `code-review`

## 요청

사용자가 이슈 #101(VLT-004 암호화 백업 내보내기와 복원) 착수를 승인("ㅇㅇ")한 후 진행했다. "작업전엔 항상 main 최신화하고 pr전엔 항상 main에 리베이스 하고 진행해", "테스트 작성이랑 코드 리뷰만 서브 에이전트 두고 나머지 총괄을 단일로 가", "리뷰 서브 에이전트는 argon으로 해 medium 수준으로" 워크플로우를 엄격히 준수했다.

## AI 산출물

- `Apps/Apple/Projects/Core/Domain/Sources/Backup/`:
  - `KeyManageBackupStrings.swift`: VLT-004 표준 사용자 문구 상수 정의 (`exportTitle`, `exportDescription`, `exportButton`, `decryptFailedMessage`, `notABackupFileMessage`, `newerVersionMessage`, `restoreTitle`, `restoreDescription`, `restoreButton`, `restoreCompletedMessage(count:)`)
  - `EncryptedBackupPayload.swift`: `BackupFileFormat`, `BackupKdfPayload`, `KeyManageBackupError: LocalizedError`, 그레고리력 기반 파일명 헬퍼
  - `VaultBackupMerger.swift`: VLT-004 규칙 6~7 백업 복원 병합 엔진 (`restoreItems`), 중복 ID 거절, `isReadOnly` 방어, 내용 동일 항목 유지, 타임스탬프 단조 증가 계산
  - `BackupCipherService.swift`: 마스터 비밀번호 기반 PBKDF2-HMAC-SHA256 + HKDF + AES-256-GCM 암·복호화 엔진, 5MB+64KB 파일 한도 및 5MB 압축 JSON 볼트 한도 검증, 민감 데이터 메모리 즉시 소거(`memset_s`)
- `Apps/Apple/Projects/Core/Domain/Sources/Lock/VaultLockManager.swift`:
  - `VaultLockManagerProtocol`: `exportBackup`, `restoreBackup` 메서드 시그니처 추가
  - `VaultLockManager`: 잠금 해제 세션 기반 백업 내보내기, 읽기 전용 볼트 거절, 복원 병합 및 5MB 상한 검증, 암호화 볼트 갱신 연동
- `Apps/Apple/Projects/Core/Domain/Tests/VaultBackupTests.swift`:
  - 단위 테스트 23종 전수 통과: 백업 내보내기 직렬화/영지식 무결성, nonce 고유성, 복호화 라운드트립, 변조 거절, KDF iterations 경계값, 파일 크기 한도(5MB+64KB), 볼트 한도(5MB), 새 버전 거절, 복원 병합 8종 시나리오, Web-Apple 교차 플랫폼 복호화 상호 운용성, 표준 문구 불변식
- `Apps/Apple/Projects/Core/Domain/Tests/VaultLockTests.swift`:
  - 단위 테스트 추가: `VaultLockManager` 백업 내보내기/복원 연동, 읽기 전용 볼트 복원 거절 및 내보내기 허용, 5MB 한도 초과 복원 거절, LocalizedError 표준 문구 바인딩
- `Scripts/check-policy-acceptance.sh`:
  - Apple 섹션에 VLT-004 불변식 10개 규칙 추가 (파일명, 포맷 버전, 한도, 복원 규칙, 표준 안내 문구 등 Closed-Loop 검증)

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 채택 | PR 제출 전 항상 main 최신 리베이스 원칙 준수 채택 |
| 채택 | 리뷰 서브에이전트에 Gemini 4 Argon Medium Reasoning 적용 채택 |
| 채택 | 테스트 작성(RED)과 코드 리뷰(REVIEW)만 서브에이전트 위임, 구현 및 검증/PR은 메인 에이전트 단일 수행 워크플로우 채택 |
| 채택 | Argon 지적 사항(5MB 복원 용량 한도 검증, 읽기 전용 볼트 테스트 누락, 메모리 소거 보강) 전수 반영 채택 |

## 주요 대화 내용

- **사용자**: "ㅇㅇ" (이슈 #101 구현 착수 승인)
- **어시스턴트**:
  - `main` 브랜치 최신화 및 `feat/apple-encrypted-backup-vlt-004` 브랜치 작업 상태 점검
  - VLT-004 정책 및 Web 참조 구현 분석
  - 도메인 인터페이스 및 타입 스켈레톤 생성 (`KeyManageBackupStrings`, `EncryptedBackupPayload`, `VaultBackupMerger`, `BackupCipherService`)
  - RED Test Writer 서브에이전트 가동 -> `VaultBackupTests.swift` 23개 RED 테스트 작성 및 정책 스크립트 갱신
  - 메인 에이전트 GREEN 구현 -> `BackupCipherService`, `VaultBackupMerger`, `VaultLockManager` 연동
  - Argon Medium 코드 리뷰 서브에이전트 가동 -> P2 2건(5MB 복원 한도 검증, 읽기 전용 볼트 테스트 누락) 및 P3~P5 피드백 수신
  - 피드백 전수 반영 및 단위 테스트(총 45개) All Green 통과 확인
  - Argon 서브에이전트 최종 APPROVE 머지 승인 획득
  - Apple 6중 기계적 검증 수행 및 PR 제출

## 다음 작업

- Apple UI 계층(Settings 또는 VaultListFeature)에서 백업 내보내기 및 복원 파일 선택 창(FilePicker / DocumentPicker) 모달 연동
