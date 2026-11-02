# Apple 워크플로우 강제 게이트 (Workflow Gate)

본 규칙은 Apple 플랫폼(`Apps/Apple`, `Apps/Apple/docs`) 개발 작업 시 반드시 준수해야 하는 강제 게이트입니다.

---

## 1. 커밋 승인 요청 전 필수 게이트

에이전트는 아래 4개 단계의 기계적 검증 증거 없이 사용자에게 커밋 승인 요청이나 PR 생성을 제안할 수 없습니다.

1. **레이어별 독립 서브에이전트 리뷰 완료**:
   - 최소 다음 4개 영역으로 분할하여 독립 코드 리뷰를 수행해야 함:
     1) `Build & Infra` (Tuist, Package, ProjectDescriptionHelpers, Gitignore)
     2) `Core Layer` (Domain, Data, DesignSystem)
     3) `Feature Layer` (Interface, Implementation, Testing, Tests, Example)
     4) `Governance & Policy` (`Apps/Apple/docs`, `docs/policies` 무결성 대조)
2. **리뷰 피드백(P1~P2) 전수 해결 및 무결함 증명**:
   - `P1(Blocker)` 및 `P2(Required)` 지적 사항이 100% 코드로 반영되어야 함.
   - 잔여 P1/P2 이슈가 0건임을 확인해야 함.
3. **6중 통합 검증 스크립트 통과 (`Scripts/verify-apple.sh`)**:
   - `bash Scripts/verify-apple.sh` 실행 결과가 `exit 0`이어야 함.
   - 포함 검증:
     1) `Scripts/check-docs.sh` (문서 거버넌스 및 ADR/정책 링크 무결성)
     2) `Scripts/check-apple-lint.sh` (`Scripts/check-swift-architecture.sh` 범용 Clean Architecture·스텁·동시성 린트 + KeyManage 도메인 보안 불변식 검사)
     3) `Scripts/check-policy-acceptance.sh`, `Scripts/check-api-contract.sh` & `Scripts/verify-tdd-integrity.sh` (멀티 플랫폼 정책 경계값 어설션 감사, API 계약 정합성 감사, GREEN 단계 테스트 임의 수정 차단)
     4) Tuist 프로젝트 생성
     5) `xcodebuild build` (Swift 6 Strict Concurrency)
     6) `xcodebuild test` (전 타깃 단위 테스트)
4. **실패 패턴의 정적 규칙 격상 및 코드 가드닝 (Post-Mortem Rule Upgrade & Gardening)**:
   - 독립 리뷰에서 P1/P2 결함이 적발된 경우, 해당 코드만 패치하는 데 그치지 않고 **동일 유형의 실수를 다음번에 0초 만에 기계적으로 차단할 수 있는 정적 규칙(`Scripts/check-swift-architecture.sh`, `Scripts/check-apple-lint.sh`, 또는 `Scripts/check-policy-acceptance.sh`) 추가 여부**를 반드시 검토·반영해야 함.
   - 작업 중 발견된 미사용 스텁(`notImplemented` 등)이나 불필요한 결합은 즉시 가드닝(Gardening)으로 정리함.

---

## 2. 위반 시 조치

상기 게이트 증거 없이 "빌드가 성공했으니 커밋을 승인해 달라"는 텍스트를 출력하는 것은 본 시스템 규칙 위반으로 간주되며, 즉시 멈추고 독립 리뷰 및 기계적 검증 절차로 강제 복귀해야 합니다.
