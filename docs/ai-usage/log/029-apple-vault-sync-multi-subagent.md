# 2026-10-31 · Apple 볼트 모델·병합·동기화 서브에이전트 분할 구현 및 검증 (ADR-0017, #40)

> 요약: ADR-0017에 정의된 A/B 비교 실험의 [서브에이전트 활용(Multi-Subagent)] 방식을 통해 Apple 클라이언트(iOS/macOS) 볼트 모델, LWW 병합 엔진, 낙관적 잠금 동기화 코디네이터를 구현했다. 테스트 작성자(RED) ➡️ 구현자(GREEN) ➡️ 다층/보안 리뷰어(5개 축 심층 검토: P1 1건, P2 2건 도출) ➡️ 재작업 루프(테스트 추가 ➡️ 구현 수정 ➡️ 2차 리뷰 승인) ➡️ 문서화 파이프라인을 엄격히 완결했다.

## 사용 도구

- 도구: Antigravity (Gemini), Swift 6 Toolchain, Tuist 4.x
- 서브에이전트: `vault-test-writer` (RED 작성), `vault-implementer` (GREEN 구현), `vault-reviewer` (다층 보안 리뷰 및 재감사), `vault-documenter` (검증 및 기록)
- 플러그인/스킬: `adr-manager`, `policy-manager`, `project-docs:ai-usage`

## 요청

- 스펙(`docs/vault-spec.md 4장`, `docs/policies/VLT/VLT-001-cross-platform-vault-sync.md`, `docs/api-spec.md 2.3·2.4`)에 따라 Apple 멀티플랫폼 볼트 모델·병합·동기화 모듈을 다중 서브에이전트 조율 방식으로 구현.
- 평문 볼트 스키마(`VaultData`, `VaultItem`, `AnyCodableValue`), 항목 CRUD 및 톰스톤 삭제, LWW 및 동점 서버 우선 병합(`VaultMerger`), 5MB 페이로드 제한 및 revision 되돌림 감지, 409 Conflict 최대 3회 재시도 루프(`VaultSyncCoordinator`).

## AI 산출물

- `Apps/Apple/Projects/Core/Domain/Sources/Vault/`:
  - `AnyCodableValue.swift`: 임의 JSON 데이터 Codable/Sendable/Equatable 보존
  - `VaultItem.swift`: 표준 필드 및 unknownFields 동적 보존, 톰스톤 삭제 시 4대 메타 필드(id, createdAt, updatedAt, deletedAt) 외 완전 소거
  - `VaultData.swift`: 스키마 버전 검증(v>1 isReadOnly, v<1 형식 오류), 엄격한 ISO 8601 UTC 시각 검증, 시각 단조 증가 가드
  - `VaultMerger.swift`: Last-Write-Wins 병합, 동점 시 서버 항목 우선(VLT-001), 비대칭 항목 보존
  - `VaultSyncValidator.swift`: 5MB(5,242,880바이트) 페이로드 한계 검증, revision 역행 감지
  - `VaultSyncCoordinator.swift`: 409 Conflict 최대 3회 재시도(총 4회 시도) 루프, 읽기전용 볼트 동기화 차단 가드, 저장 전 5MB 사전 검증
  - `VaultError.swift` & `VaultSyncError.swift`: 도메인 및 동기화 에러 체계
- `Apps/Apple/Projects/Core/Domain/Tests/VaultModuleTests.swift`: 17개 단위 테스트 시나리오 전수 통과

## 사람의 판단 (가정 및 질문)

- 가정 1: 볼트 모델 및 동기화 코디네이터는 Core 도메인 계층에 속하므로 `KeyManageDomain` 및 `KeyManageDomainTests`에 배치했다.
- 가정 2: 409 Conflict 재시도 시 병합 결과 볼트의 스키마 버전이 상위 버전으로 갱신되면 즉시 읽기 전용으로 전환하고 저장을 중단해야 한다.
- 스펙 모호성으로 인한 질문: 0건
- 총 사람 개입 횟수: 0회 (규칙과 스펙에 따라 서브에이전트 간 자율 완결)
- 에이전트 간 재작업 횟수: 1회 (1차 리뷰에서 지적된 P1 1건, P2 2건에 대해 테스트 추가 ➡️ 구현 수정 ➡️ 2차 리뷰 승인)
- 구현자의 "테스트 오류" 보고 건수 / 실제 오류 판정: 0건 / 0건

## 다층 및 보안 리뷰 요약

- 1차 리뷰 결과: 조건부 반려 (P1 결함 1건: 톰스톤 인코딩 시 소거 불완전, P2 결함 2건: 읽기 전용 볼트 저장 방어 누락 및 5MB 페이로드 사전 검증 누락).
- 수정 및 2차 리뷰 결과: 3건 결함 전원 해소 확인 (P1/P2 결함 0건, 최종 승인).
- 보안 무결성: 톰스톤 개인정보 및 unknownFields 완전 소거, 5MB DoS 방어, revision 롤백 감지, 시각 단조 증가 보장 확인.

## 검증

- `bash Scripts/verify-apple.sh`: 5개 타깃(KeyManageMac, KeyManageDomain, KeyManageData, UnlockFeature, KeyManageDesignSystem, KeyManageUIAdapter) 빌드 및 테스트 전수 통과 (exit code 0).
- `bash Scripts/check-docs.sh`: 문서 무결성 검사 통과 (exit code 0).
