# 멀티 플랫폼 검증 게이트 및 AI-정적 검증 루프 (Multi-Platform Workflow Gate)

본 규칙은 `Apps/Web`, `Back`, `Apps/Apple`, `Apps/Android` 및 공통 문서(`docs/`) 변경 시 전 플랫폼에 대칭적으로 적용되는 강제 게이트입니다.

---

## 1. 공통 정적 게이트 (0초~1초 미만 즉시 실행)

커밋 승인 요청 또는 PR 생성 전, 그리고 `.githooks/pre-commit` 이벤트 시점에 다음 정적 검증이 `exit 0`을 통과해야 합니다.

1. **문서 거버넌스 및 링크 무결성 (`Scripts/check-docs.sh`)**:
   - `*.md` 변경 시 필수 실행. 상대 링크, ADR/정책/AI 로그 목록 표, 번호 참조 무결성 검증.
2. **TDD 어설션 불변성 감사 (`Scripts/verify-tdd-integrity.sh`)**:
   - 구현(`feat:` / `fix:`) 단계에서 기존 단위 테스트(`*Tests.swift`, `*.test.ts*`, `*Test.kt`, `*Test.java`)의 어설션 줄을 임의 삭제·변조(Silent Test Modification)하는 행위 즉시 차단.
3. **멀티 플랫폼 정책 합격 기준 감사 (`Scripts/check-policy-acceptance.sh`)**:
   - `Apps/Apple`과 `Apps/Web`(및 존재하는 클라이언트 플랫폼) 양쪽의 단위 테스트가 공통 정책(`SEC-001`, `SEC-003`, `VLT-001~003`, `GEN-001`, `COM-001`)의 필수 경계값과 표준 한국어 사용자 문구를 누락 없이 어설션하고 있는지 교차 검증.
4. **API 명세 ↔ 코드 계약 드리프트 감사 (`Scripts/check-api-contract.sh`)**:
   - `docs/api-spec.md`의 엔드포인트(메서드·경로) 및 표준 오류 코드·메시지가 `Back/api` 컨트롤러/`ApiError.kt` 및 클라이언트(`Apps/Web`의 `SERVER_ERROR_CODES`/HTTP 어댑터 등)와 100% 일치하는지 검증.

---

## 2. 정책(SSOT) ↔ 정적 감사 닫힌 루프 (Closed-Loop Rule)

- `docs/policies/` 또는 `docs/vault-spec.md`에 **경계값, 예외 조건, 표준 사용자 문구**가 추가되거나 변경될 때는 반드시 **같은 작업(PR) 안에서 `Scripts/check-policy-acceptance.sh`의 해당 플랫폼 불변식 규칙(`*_rules`)을 함께 갱신**해야 합니다.
- `docs/api-spec.md`에 엔드포인트나 오류 코드가 추가·변경될 때는 `Scripts/check-api-contract.sh`를 실행해 백엔드·클라이언트 상수와 동기화되었는지 확인해야 합니다.

---

## 3. 플랫폼별 필수 검증 및 PIT 뮤테이션 생존 결함 타격 루프

| 플랫폼 | 필수 실행 명령 | 추가 게이트 |
| :--- | :--- | :--- |
| **Back (`Back/`)** | `./gradlew check :domain:pitest --console=plain` | `python3 Scripts/mutation-score.py Back/domain/build/reports/pitest/mutations.xml`을 실행해 **살아남은 결함(Survived Mutants)** 이 있으면 해당 줄 번호·변이 조건을 타격하는 단위 테스트를 보강 |
| **Web (`Apps/Web/`)** | `npm run check && npx vite build` | `bash Scripts/check-policy-acceptance.sh` 및 `bash Scripts/check-api-contract.sh` 통과, 작성 맥락과 분리된 서브에이전트 독립 리뷰 수행 |
| **Apple (`Apps/Apple/`)** | `bash Scripts/verify-apple.sh` | `.gemini/rules/apple-workflow-gate.md`의 4대 영역 독립 리뷰 및 6중 통합 검증 통과 |
