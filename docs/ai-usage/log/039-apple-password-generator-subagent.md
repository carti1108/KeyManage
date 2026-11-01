# 2026-11-01 · Apple 비밀번호 무작위 생성기 및 엔트로피 점수 진단기 서브에이전트 구현 (#40)

- **날짜**: 2026-11-01
- **작성자**: kiseok (with Antigravity / Gemini)
- **관련 이슈 / PR**: #40 ([GEN-001](../../policies/GEN/GEN-001-password-generation-and-entropy-scoring.md), [ADR-0016](../../adr/ADR-0016-apple-collaboration-experiment-delegation-and-role-separation.md), [ADR-0017](../../adr/ADR-0017-single-agent-vs-subagent-experiment.md))

---

## 1. 작업 목표

1. [GEN-001](../../policies/GEN/GEN-001-password-generation-and-entropy-scoring.md) 정책에 기반하여 비밀번호 무작위 생성기, 엔트로피 점수 산출기, 연속 ASCII 숫자 진단기를 Apple 플랫폼 순수 도메인 로직으로 구현한다.
2. [ADR-0016](../../adr/ADR-0016-apple-collaboration-experiment-delegation-and-role-separation.md) 및 [ADR-0017](../../adr/ADR-0017-single-agent-vs-subagent-experiment.md) 결론에 따라 확립된 **서브에이전트 파이프라인(RED ➡️ GREEN ➡️ 독립 REVIEW ➡️ VERIFY & DOCS)** 표준을 적용하고, TDD 테스트 불변성을 100% 준수한다.
3. 6중 기계적 검증 게이트(`check-apple-lint`, `check-policy-acceptance`, `verify-tdd-integrity`)를 통과하고 회귀 방지 정책 감사 항목을 확장한다.

---

## 2. AI 협업 방식 및 개입 내역

- **수행 구조**:
  1. **Step 1 (RED - `RED Test Writer`)**:
     - `PasswordGeneratorTests.swift`에 경계값(8, 64자), 4개 문자군 1,000회 반복 거부 샘플링 검증, 플랫폼 공통 검증 벡터 4종(`123456`, `abc~~~~~`, 12자, 16자), 연속 숫자 샘플 6종, 표준 문구 4종 전수 작성.
     - `PasswordGenerator.swift` 최소 스텁 작성 후 RED(16건 실패) 확인.
     - `test: 비밀번호 생성 및 엔트로피 점수 단위 테스트 작성` 커밋 (`c1895a2`).
  2. **Step 2 (GREEN - `GREEN Implementer`)**:
     - `verify-tdd-integrity.sh` 기계 감시 아래 테스트 파일 수정 0건을 준수하며 순수 도메인 구현.
     - CSPRNG `SecRandomCopyBytes` 및 `limit = 256 - (256 % poolSize)` 거부 샘플링으로 모듈로 편향(Modulo Bias) 완전 차단.
     - 4개 문자군 필수 포함 거부 샘플링, `unicodeScalars.count` 기반 엔트로피 점수 공식, 3자리 단방향 연속 숫자 판정 로직 구현.
     - `feat: 비밀번호 생성기 및 엔트로피 진단 엔진 구현` 커밋 (`0226d47`).
  3. **Step 3 (REVIEW - `Independent Code Reviewer`)**:
     - 독립 서브에이전트 감사 수행 (암호학적 무결성, CSPRNG, Sendable 적합성, 정책 문구 전수 검증).
     - 최종 승인(LGTM) 및 P4 성능 제안(연속 숫자 검사기 내 `asciiDigits` 정적 캐싱) 도출 ➡️ 리팩토링 반영 (`bd15b62`).
  4. **Step 4 (VERIFY & DOCS - `documenter`)**:
     - `Scripts/check-policy-acceptance.sh`에 GEN-001 8대 필수 정책 감사 항목 공식 추가 (`9a80a96`).
     - `bash Scripts/verify-apple.sh` 6단계 전체 기계적 검증 완벽 통과.

---

## 3. 검증 결과

- `bash Scripts/verify-apple.sh`: 6단계 전체 통과 (`exit 0`)
  1. 문서 거버넌스 및 ADR 무결성 검증 (`Scripts/check-docs.sh`): 통과
  2. Apple 정적 아키텍처·보안·스텁 린트 (`Scripts/check-apple-lint.sh`): 위반 0건 통과
  3. 정책 합격 기준 감사 (`Scripts/check-policy-acceptance.sh`) 및 TDD 테스트 불변성 (`Scripts/verify-tdd-integrity.sh`): 위반 0건 통과
  4. Tuist 의존성 확인 및 프로젝트 생성: 성공
  5. `KeyManageMac` macOS 빌드: 성공
  6. Apple 타깃 단위 테스트 실행: 총 73개 테스트 100% 통과 (도메인 신규 23개 포함)
