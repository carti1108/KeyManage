# Apple 워크플로우 강제 게이트 (Workflow Gate)

본 규칙은 Apple 플랫폼(`Apps/Apple`, `docs/iOS`) 개발 작업 시 반드시 준수해야 하는 강제 게이트입니다.

---

## 1. 커밋 승인 요청 전 필수 게이트

에이전트는 아래 3개 단계의 기계적 검증 증거 없이 사용자에게 커밋 승인 요청이나 PR 생성을 제안할 수 없습니다.

1. **레이어별 독립 서브에이전트 리뷰 완료**:
   - 최소 다음 4개 영역으로 분할하여 독립 코드 리뷰를 수행해야 함:
     1) `Build & Infra` (Tuist, Package, ProjectDescriptionHelpers, Gitignore)
     2) `Core Layer` (Domain, Data, DesignSystem)
     3) `Feature Layer` (Interface, Implementation, Testing, Tests, Example)
     4) `Governance & Policy` (`docs/iOS`, `docs/policies` 무결성 대조)
2. **리뷰 피드백(P1~P2) 전수 해결 및 무결함 증명**:
   - `P1(Blocker)` 및 `P2(Required)` 지적 사항이 100% 코드로 반영되어야 함.
   - 잔여 P1/P2 이슈가 0건임을 확인해야 함.
3. **통합 검증 스크립트 통과**:
   - `bash Scripts/verify-apple.sh` 실행 결과가 `exit 0`이어야 함.
   - 포함 검증: `Scripts/check-docs.sh`, Tuist 프로젝트 생성, `xcodebuild build`, `xcodebuild test`, Git hygiene.

---

## 2. 위반 시 조치

상기 게이트 증거 없이 "빌드가 성공했으니 커밋을 승인해 달라"는 텍스트를 출력하는 것은 본 시스템 규칙 위반으로 간주되며, 즉시 멈추고 독립 리뷰 절차로 강제 복귀해야 합니다.
