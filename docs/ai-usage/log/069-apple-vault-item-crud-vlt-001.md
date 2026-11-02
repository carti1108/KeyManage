# 2026-11-02 · Apple VLT-001 볼트 아이템 추가, 수정, 삭제(CRUD) 모달 구현 (#114)

- 이슈·PR: #114
- 커밋: c10bea7

> **요약** VLT-001(볼트 동기화 및 툼스톤 삭제 정책), ADR-0014(Apple microRIBs)에 따라 Apple 플랫폼의 볼트 아이템 추가, 수정, 삭제(CRUD) 모달 폼과 도메인 파이프라인을 구현했다. `VaultLockManager`에 볼트 아이템 추가(`addVaultItem`), 수정(`updateVaultItem`), 툼스톤 삭제(`deleteVaultItem`)를 추가하고, 데이터 변경 즉시 AES-256-GCM 재암호화 및 5MB 페이로드 상한 검증을 연동했다. `VaultListFeature`에 신규 항목 추가 및 수정 모달 시트, VLT-001 표준 삭제 확인 팝업, `PasswordGenerator` 기반 16자 강력한 비밀번호 자동 생성 버튼을 구축했다. RED Test Writer 서브에이전트가 19개 도메인 단위 테스트(`VaultItemCRUDTests.swift`)를 작성하고, `VaultListTests.swift`에 14개 피처 단위 테스트를 보강하여 전수 검증을 완료했다.

## 사용 도구

- 도구: Antigravity (Gemini), Xcode 16.4, xcodebuild, Tuist 4.x
- 플러그인·스킬: `clean-architecture-layers`, `modern-swift-testing`, `code-review`, `policy-manager`

## 요청

사용자가 이전 PR #111(SEC-004 마스터 비밀번호 변경) 완료 후 다음 작업으로 승인("1순위")한 볼트 아이템 추가/수정/삭제 모달(`VaultItemEditor` / `VaultListFeature` 확장)을 진행했다. "작업전엔 항상 main 최신화하고 pr전엔 항상 main에 리베이스 하고 진행해", "테스트 작성이랑 코드 리뷰만 서브 에이전트 두고 나머지 총괄을 단일로 가", "리뷰 서브 에이전트는 argon으로 해 medium 수준으로" 워크플로우를 준수했다.

## AI 산출물

- `Apps/Apple/Projects/Core/Domain/Sources/Vault/VaultItemError.swift`: VLT-001 정책 에러 5종 (`emptyServiceName`, `vaultIsReadOnly`, `vaultTooLarge`, `itemNotFound`, `locked`) 및 `LocalizedError` 표준 문구 매핑.
- `Apps/Apple/Projects/Core/Domain/Sources/Lock/VaultLockManager.swift`:
  - `VaultLockManagerProtocol`에 `addVaultItem`, `updateVaultItem`, `deleteVaultItem` 선언 및 편의 기본값 extension 제공.
  - `VaultLockManager`: 스레드 안전 비재진입 `syncLock` 락 경계, 서비스 이름 공백 검증, VLT-001 규칙 6 툼스톤(삭제 시 id/createdAt/updatedAt/deletedAt 외 민감 필드 소거) 보장, AES-256-GCM 즉시 재암호화 및 5MB 한도 검증.
- `Apps/Apple/Projects/Core/Domain/Tests/VaultItemCRUDTests.swift`: RED Test Writer 서브에이전트가 작성한 19개 단위 테스트 (추가, 수정, 툼스톤 삭제, 기삭제 툼스톤 재삭제/변조 방어, 공백 검증, 읽기 전용/잠금 거부, 5MB 상한, 정책 문구 일치 검증) 전원 통과.
- `Apps/Apple/Projects/Features/VaultList/Implementation/`:
  - `VaultListViewModel.swift`: 모달 시트 상태, 폼 필드 바인딩, 공백 검증, `PasswordGenerator` 기반 16자 자동 생성 액션, 삭제 확인 알림 상태 관리.
  - `VaultListInteractor.swift`: `didRequestSaveItem`, `didRequestDeleteItem` 처리, 저장/수정/삭제 후 목록 갱신 및 토스트 노출, 시트 닫기 연동.
  - `VaultListViewController.swift`: `VaultListPresentableListener` 바인딩 및 `setIsReadOnly`, `dismissItemSheet` 브릿지 연동.
  - `VaultListView.swift`: 상단 바 `항목 추가` 버튼, 아이템 카드 `수정`/`삭제` 액션, `itemFormModalView` (디자인 시스템 서브뷰 분리, 서비스명/아이디/비밀번호+자동생성/URL/메모 필드), VLT-001 표준 삭제 확인 팝업.
- `Apps/Apple/Projects/Features/VaultList/Tests/VaultListTests.swift`: 단위 테스트 14종 (신규 추가, 수정, 삭제, 에러 토스트, 폼 생명주기 및 자동 생성 등) 전원 통과.

## 사람의 판단

| 판단 | 내용 |
| --- | --- |
| 단일 모달 시트 통합 | 별도 중첩 RIB 대신 `VaultListFeature` 내부 모달 시트(`itemFormModalView`)로 구현하여 불필요한 라우팅 보일러플레이트를 줄이고 응집도를 극대화. |
| 비밀번호 생성기 원클릭 연동 | 비밀번호 폼 필드에 `자동 생성` 버튼을 배치하여 사용자가 별도 탭 이동 없이 80점 이상의 안전한 비밀번호를 즉시 채울 수 있도록 UX 최적화. |
| 프로토콜 기본 구현 활용 | `VaultLockManagerProtocol` extension에 기본 CRUD 구현을 제공하여 타 기능(Main, Root, Settings 등)의 기존 Mock들이 깨지지 않도록 하위 호환성 유지. |
