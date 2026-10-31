# 2026-10-31 · Apple 볼트 잠금 해제·자동 잠금 서브에이전트 구현 및 6중 정적 검증 게이트 구축 (#40)

- **날짜**: 2026-10-31
- **작성자**: kiseok (with Antigravity / Gemini)
- **관련 이슈 / PR**: #40 ([ADR-0016](../../adr/ADR-0016-apple-collaboration-experiment-delegation-and-role-separation.md), [ADR-0017](../../adr/ADR-0017-single-agent-vs-subagent-experiment.md), [SEC-003](../../policies/SEC/SEC-003-unlock-and-auto-lock.md))

---

## 1. 작업 목표

1. [ADR-0017](../../adr/ADR-0017-single-agent-vs-subagent-experiment.md) 3단계(결승전) 과업인 [SEC-003](../../policies/SEC/SEC-003-unlock-and-auto-lock.md) 볼트 잠금 해제·자동 잠금·메모리 보안 모듈을 Zero-Hint 프롬프트, 동일 4단계 프로세스, 이중 맹검(Double-Blind) 판정 조건 아래 단일 에이전트 대 서브에이전트 분할 방식으로 비교 실험한다.
2. 블라인드 심사에서 승리한 서브에이전트 구현(`exp/autolock-sub`)에 심사 피드백을 반영하고, 1~3단계 실험에서 발견된 반복 결함 패턴을 0초 만에 기계적으로 차단하는 6중 검증 스크립트 및 GitHub Actions `apple` CI Job을 구축한다.

---

## 2. AI 협업 방식 및 개입 내역

- **수행 구조**:
  1. `autolock-test-writer` (RED) ➡️ `autolock-implementer` (GREEN) ➡️ `autolock-reviewer` (독립 리뷰: `.immediate` 포그라운드 잠금 버그, 생체 인증 볼트 복원 누락, 가림막 미해제 및 콜백 락 분리 적발) ➡️ 재작업 TDD 루프로 해결.
  2. 독립 판정 서브에이전트(`Blind Evaluation Judge`)가 익명 후보군(`Candidate_X`, `Candidate_Y`)을 채점하여 서브에이전트 분할 방식(86점 vs 74점)을 최종 승자로 판정.
  3. 블라인드 심사에서 지적된 레거시 오버로드 제거, SEC-003 표준 한국어 문구(`LocalizedError`, `VaultLockUIStrings`) 및 `VaultData.empty` 이식, 중간 파생 키 버퍼 `defer` 제로화 반영.
  4. 프롬프트 지시에 의존하던 규칙들을 `Scripts/check-apple-lint.sh`, `Scripts/check-policy-acceptance.sh`, `Scripts/verify-tdd-integrity.sh` 및 `.github/workflows/ci.yml`의 `apple` Job으로 격상.

---

## 3. 검증 결과

- `bash Scripts/verify-apple.sh`: 6단계 전체 통과 (`exit 0`)
  1. 문서 검사 (`Scripts/check-docs.sh`): 통과
  2. Apple 정적 아키텍처·보안·스텁 린트 (`Scripts/check-apple-lint.sh`): 위반 0건
  3. 정책 합격 기준 감사 (`Scripts/check-policy-acceptance.sh`) 및 TDD 테스트 불변성 (`Scripts/verify-tdd-integrity.sh`): 위반 0건
  4. Tuist 프로젝트 생성 및 `KeyManageMac` Swift 6 빌드: 성공
  5. `KeyManageDomain` 단위 테스트: `VaultLockTests` 18개 포함 총 50개 테스트 100% 통과
