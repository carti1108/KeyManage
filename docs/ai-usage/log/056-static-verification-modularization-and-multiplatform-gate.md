# 2026-11-02 · 범용 정적 검증 플러그인 승격 및 멀티 플랫폼 자동 검증 루프 고도화 (#91, #92)

- 이슈·PR: #91, #92
- 커밋: 8c992bb, 8ba82aa

> **요약** 레포에 혼재되어 있던 범용 정적 검증 스크립트를 도메인 규칙과 분리해 플러그인(`qa-tester`, `ios-architect`, `issue-worktree`)으로 승격하고(#91), Apple에만 적용되던 정책 합격 기준 감사·워크플로우 게이트를 Web·Back을 포함한 멀티 플랫폼과 `.githooks/pre-commit`, API 계약 드리프트 감사(`check-api-contract.sh`)로 확장했다(#92).

## 사용 도구

- 도구: Antigravity (Gemini 4 Argon)
- 플러그인·스킬: `project-docs:ai-usage`, `issue-worktree:commit-rules`, `policy-manager:policy-manager`

## 요청

- "멀티플래폼~, 범용 정적 검증~ 관련 이슈 두 개 파악해 봐"
- "권장 진행 순서대로" (#91 범용 정적 검증 분리·승격 선행 후 #92 멀티 플랫폼 검증 비대칭 해소 및 자동 루프 고도화 진행)

## AI 산출물

- **이슈 #91 (범용 정적 검증 분리·모듈화 및 플러그인 승격)**:
  - `Scripts/check-swift-architecture.sh`: Clean Architecture 도메인 격리(규칙 1), 미구현 스텁·`fatalError`·`TODO` 금지(규칙 2), `NSLock` 콜백 데드락 차단(규칙 3)을 프로젝트 하드코딩 없는 범용 스크립트로 분리 및 `ios-architect` 플러그인(`skills/clean-architecture-layers/scripts/check-swift-architecture.sh`) 내장
  - `Scripts/check-apple-lint.sh`: 범용 `check-swift-architecture.sh` 호출 + KeyManage 전용 보안 불변식(규칙 4: `memset_s()`·`LocalizedError`, 규칙 5: `unlockWithBiometrics(*Key: Data)` 비옵셔널 키 시그니처) 모듈화
  - `Scripts/verify-tdd-integrity.sh`: `git rev-parse --show-toplevel` 기반 독립 실행, `--cached` 스테이징 검사, `GITHUB_BASE_REF` PR 커밋 전수 검사, `ALLOW_TEST_EDIT=1` 우회 옵션 추가 및 `qa-tester` 플러그인(`skills/tdd-integrity/scripts/verify-tdd-integrity.sh`, `hooks/hooks.json`) 내장
  - `.githooks/commit-msg` 및 `Scripts/mutation-score.py`: `issue-worktree`(`skills/commit-rules/scripts/commit-msg`) 및 `qa-tester`(`scripts/mutation-score.py`) 플러그인 내장
- **이슈 #92 (멀티 플랫폼 검증 비대칭 해소 및 AI-정적 검증 루프 자동화)**:
  - `Scripts/check-policy-acceptance.sh`: Apple(`Apps/Apple/Projects/Core/Domain/Tests`)과 Web(`Apps/Web/src/**/*.test.ts*`) 양쪽의 7대 정책(`vault-spec/SEC-001`, `VLT-001~003`, `SEC-003`, `GEN-001`, `COM-001`) 경계값·표준 문구 어설션 교차 감사로 확장
  - `Scripts/check-api-contract.sh`: `docs/api-spec.md`의 6개 REST 엔드포인트 및 11개 표준 오류 코드·한국어 메시지를 추출해 `Back/api`(`ApiError.kt`, Controller) 및 `Apps/Web`(`SERVER_ERROR_CODES`, HTTP 어댑터)과 대조하는 계약 드리프트 정적 게이트 신설
  - `.githooks/pre-commit`: 스테이징된 파일에 따라 `check-docs.sh`, `verify-tdd-integrity.sh --cached`, `check-apple-lint.sh`, `check-policy-acceptance.sh`, `check-api-contract.sh`를 1초 내 자동 실행하도록 구성
  - `.gemini/rules/multi-platform-workflow-gate.md`, `CLAUDE.md`, `.github/workflows/ci.yml`: 정책 변경 시 `check-policy-acceptance.sh` 동시 갱신(Closed-Loop), 백엔드 PIT 뮤테이션 생존 결함 타격 루프, CI `docs` 잡의 전 플랫폼 정적 감사 단계 반영

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 채택 | 현재 체크아웃된 `feat/encrypted-backup` 브랜치 대신 `origin/main` 기준으로 새 브랜치를 생성해 #91 → #92 순서로 진행하기로 결정 |
| 채택 | `KeyManage` 레포 내부 모듈화·게이트 구축과 상위 폴더 `carti-marketplace` 플러그인 레포 승격을 함께 진행하기로 결정 |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `Scripts/check-docs.sh` (문서 거버넌스 및 링크 무결성) | 통과 |
| `Scripts/check-swift-architecture.sh` & `Scripts/check-apple-lint.sh` | 통과 (위반 0건) |
| `Scripts/check-policy-acceptance.sh` (Apple·Web 멀티 플랫폼 감사) | 통과 (누락 0건) |
| `Scripts/check-api-contract.sh` (API 명세 ↔ Back/api·Apps/Web 대조) | 통과 (엔드포인트 6종, 오류 코드 11종 일치) |
| `Scripts/verify-tdd-integrity.sh` (TDD 어설션 불변성 감사) | 통과 |
| 독립 서브에이전트 코드 리뷰 (`79f33714`) | P1 1건, P2 3건, P3 1건, P4 1건 발견 및 전수 수정 완료 |

## 검증하지 못한 것

1. macOS 전용 `xcodebuild` 실행(`Scripts/verify-apple.sh` 5~6단계) — Windows 환경이므로 macOS CI 러너에서 검증한다.

## AI의 오류와 정정

| 오류 | 발견 | 조치 |
| :--- | :--- | :--- |
| `check-policy-acceptance.sh`의 Web 1단계 암호 모듈 규칙에서 복호화 검증 식별자를 `decryptVault`로 초기 작성해 실패 | `Scripts/check-policy-acceptance.sh` 로컬 실행 중 즉시 발견 | `Apps/Web/src/domain/crypto/vaultCipher.test.ts`의 실제 타입·함수명(`VaultDecryptError`)으로 정정해 통과 확인 |
| `qa-tester/hooks/hooks.json`의 `PreToolUse`가 모든 Bash 명령을 가로채 스테이징 해제(`git reset`)조차 막는 데드락 위험 | 독립 서브에이전트 코드 리뷰(`79f33714`)에서 P1으로 발견 | `hooks.json`에서 `PreToolUse`를 제거하고 `Stop` 훅과 `.githooks/pre-commit`으로 역할을 분리 |
| GitHub Actions PR 환경에서 `HEAD`가 Merge 커밋이라 `verify-tdd-integrity.sh`가 검사를 건너뛰는 우회 결함 및 `check-swift-architecture.sh` 규칙 3의 락 미보유 메서드 오탐 가능성 | 독립 서브에이전트 코드 리뷰(`79f33714`)에서 P2로 발견 | `GITHUB_BASE_REF` 설정 시 PR 내 `feat:`/`fix:` 커밋을 전수 검사하도록 보강하고, 규칙 3에서 메서드 내 `.lock()` 호출 여부를 먼저 확인하도록 수정 |
