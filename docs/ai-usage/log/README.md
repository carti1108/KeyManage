# AI 활용 로그

작업 단위별 AI 활용 기록을 번호 순서대로 둔다. 기록 원칙과 템플릿은 [AI 활용 기록 README](../README.md)에 있다.

## 항목 목록

| 번호 | 날짜 | 제목 |
|---|---|---|
| 001 | 2026-10-27 | [초기 아키텍처 설계 및 거버넌스 문서 체계 구축 (소급 기록)](001-initial-architecture-and-governance-setup.md) |
| 002 | 2026-10-27 | [크로스 플랫폼 병렬 개발을 위한 3대 공통 명세 수립](002-cross-platform-contracts-and-design-system.md) |
| 003 | 2026-10-27 | [VLT-001 크로스 플랫폼 볼트 동기화 정책 수립](003-vlt-001-cross-platform-sync-policy.md) |
| 004 | 2026-10-27 | [shadcn/ui 기반 크로스 플랫폼 디자인 시스템 통합 및 UIKit/SwiftUI 공용 토큰 수립](004-shadcn-ui-cross-platform-design-system.md) |
| 005 | 2026-10-27 | [5대 핵심 UI 컴포넌트 카탈로그 및 파라미터 규격 수립](005-core-ui-components-catalog.md) |
| 006 | 2026-10-28 | [KeyManage DS Builder 피그마 플러그인 제작 및 토큰 자동 드로잉 파이프라인 구축](006-figma-builder-plugin-creation.md) |
| 007 | 2026-10-28 | [백엔드 헥사고날 4모듈 스캐폴딩 및 domain 프레임워크 격리 검사 구축](007-backend-multimodule-scaffolding.md) |
| 008 | 2026-10-28 | [영지식 충돌 발견에 따른 비밀번호 생성·진단 클라이언트 전용 전환](008-client-only-password-generation-and-audit.md) |
| 009 | 2026-10-28 | [볼트 동기화 충돌 해결을 낙관적 잠금으로 전환](009-vault-optimistic-locking.md) |
| 010 | 2026-10-28 | [볼트 동기화 도메인 모델 및 낙관적 잠금 저장 유스케이스 TDD 구현](010-domain-vault-sync-optimistic-locking.md) |
| 011 | 2026-10-28 | [파일 기반 볼트 저장소 어댑터 TDD 구현 및 원자성 검증](011-file-vault-repository-adapter.md) |
| 012 | 2026-10-28 | [볼트 조회·저장 REST API 구현 및 오류 응답 매핑](012-vault-rest-api-controller.md) |
| 013 | 2026-10-28 | [계정 인증 설계: 인증 해시 분리와 서버 세션 (ADR-0008, SEC-002)](013-account-auth-design.md) |
| 014 | 2026-10-28 | [계정 인증 도메인·저장소 TDD 구현 (Argon2id, 서버 비밀키)](014-auth-domain-and-repository.md) |
| 015 | 2026-10-28 | [검증 위임 체계 구축: CI, 뮤테이션 테스트, 독립 리뷰 (ADR-0009)](015-verification-delegation-pipeline.md) |
| 016 | 2026-10-28 | [인증 API 구현과 볼트 API 토큰 전환 (#19, #10 해결)](016-auth-api-and-token-vault.md) |
| 017 | 2026-10-28 | [클라이언트 구성 결정: iOS 네이티브와 웹 코드 공유 (ADR-0010)](017-client-composition-adr.md) |
| 018 | 2026-10-28 | [결정 확인 기준 조정과 잠금 해제 정책(SEC-003)](018-decision-reversibility-and-unlock-policy.md) |
| 019 | 2026-10-29 | [플랫폼 간 암호 테스트 벡터 작성 (#26)](019-crypto-test-vectors.md) |
| 020 | 2026-10-29 | [웹 클라이언트 골격과 데스크톱 틀 결정 (ADR-0012, #28)](020-web-client-scaffold.md) |
| 021 | 2026-10-29 | [Tauri v2 데스크톱 틀 추가와 CSP 강화 (#30)](021-tauri-desktop-shell.md) |
| 022 | 2026-10-29 | [웹 클라이언트 암호 모듈 구현 (#32)](022-web-crypto-module.md) |
| 023 | 2026-10-29 | [API 클라이언트와 계정 인증 구현 (ADR-0013, #34)](023-web-auth-api.md) |
| 024 | 2026-10-29 | [볼트 모델·항목 병합·동기화 구현 (#36)](024-web-vault-sync.md) |
| 025 | 2026-10-30 | [Apple 멀티플랫폼 모듈 스캐폴딩 및 microRIBs 로컬 연동 (#38)](025-apple-client-scaffold-and-governance.md) |
| 026 | 2026-10-30 | [Apple 멀티플랫폼 암호 모듈 단일 에이전트 TDD 구현 (#40)](026-apple-crypto-module-single-agent.md) |
| 027 | 2026-10-31 | [볼트 잠금·잠금 해제와 자동 잠금 구현 (SEC-003, #44)](027-web-auto-lock.md) |
| 028 | 2026-10-31 | [웹 디자인 토큰과 핵심 컴포넌트 5종 구현 (#46)](028-web-design-system.md) |
| 029 | 2026-10-31 | [Apple 볼트 모델·병합·동기화 서브에이전트 분할 구현 및 검증 (ADR-0017, #40)](029-apple-vault-sync-multi-subagent.md) |
| 030 | 2026-10-31 | [웹 로그인·가입·잠금 해제 화면과 자동 잠금 연결 (#48)](030-web-auth-screens.md) |
| 031 | 2026-10-31 | [웹 볼트 항목 목록·상세·추가·수정·삭제 화면 (#51)](031-web-vault-items.md) |
| 032 | 2026-10-31 | [웹 비밀번호 생성기와 강도 경고 (#53)](032-web-password-generator.md) |
| 033 | 2026-10-31 | [비밀번호에 이어지는 숫자가 있으면 안내 (#55)](033-sequential-digits-hint.md) |
| 034 | 2026-10-31 | [웹 클립보드 복사와 30초 자동 소각 (#57)](034-web-clipboard.md) |
| 035 | 2026-10-31 | [웹 클라이언트와 실제 백엔드 통합 테스트 (#59)](035-web-backend-integration.md) |
| 036 | 2026-10-31 | [웹 오프라인 저장 1단계: 연결이 끊겨도 수정 내용 유지 (#61)](036-web-offline-pending-sync.md) |
| 037 | 2026-10-31 | [Apple 볼트 잠금 해제·자동 잠금 서브에이전트 구현 및 6중 정적 검증 게이트 구축 (#40)](037-apple-autolock-multi-subagent.md) |
| 038 | 2026-11-01 | [웹 오프라인 저장 2단계: 기기 저장과 재시작 뒤 잠금 화면 (#64)](038-web-device-storage.md) |
| 039 | 2026-11-01 | [Apple 비밀번호 무작위 생성기 및 엔트로피 점수 진단기 서브에이전트 구현 (#40)](039-apple-password-generator-subagent.md) |
| 040 | 2026-11-01 | [문서 경계 기준과 VLT 정책 분할 (#69 PR A)](040-docs-boundaries-and-policy-split.md) |
| 041 | 2026-11-01 | [플랫폼 ADR과 문서를 각 플랫폼 폴더로 분리 (#69 PR B)](041-platform-docs-split.md) |
| 042 | 2026-11-01 | [문서 템플릿과 한국어 STE 규칙, 정책 10개 변환 (#70 PR 1)](042-policy-template-and-ste.md) |
| 043 | 2026-11-01 | [공통 명세 3개 정리와 플랫폼 바인딩 분리 (#70 PR 2)](043-common-spec-readability.md) |
| 044 | 2026-11-01 | [문서 지도·작성 가이드 분리와 README 정리 (#70 PR 3)](044-readme-readability.md) |
| 045 | 2026-11-01 | [웹 여러 탭 기기 기록 동기화](045-web-multi-tab-device-record.md) |
| 046 | 2026-11-01 | [Apple 오프라인 동기화 대기 및 기기 볼트 보존 구현 (#68)](046-apple-offline-sync-and-device-storage.md) |
| 047 | 2026-11-01 | [웹 자동 잠금 시간 설정](047-web-auto-lock-setting.md) |
| 048 | 2026-11-01 | [Apple 클립보드 복사 및 30초 자동 소각 엔진 구현 (COM-001)](048-apple-clipboard-auto-burn.md) |
| 049 | 2026-11-01 | [데스크톱 릴리스 빌드의 서버 통신 확인](049-desktop-release-server-check.md) |
| 050 | 2026-11-01 | [Apple 디자인 시스템 토큰 및 5대 핵심 컴포넌트 구현 (#82)](050-apple-design-system-core-components.md) |
| 051 | 2026-11-01 | [Apple UnlockFeature microRIBs 잠금 해제 인터랙터 및 뷰 조립 (#84)](051-apple-unlock-feature-ribs.md) |
| 052 | 2026-11-01 | [로컬 Docker Compose 데모 실행 구성](052-docker-local-demo.md) |
| 053 | 2026-11-01 | [웹 정책 불일치 2건 수정(가입 안내, 오프라인 배너)](053-web-policy-gaps.md) |
| 054 | 2026-11-01 | [Apple RootFeature microRIBs 루트 라우팅 및 앱 조립 (#86)](054-apple-root-feature-ribs.md) |
| 055 | 2026-11-02 | [Apple MainFeature microRIBs 메인 대시보드 셸 및 루트 라우팅 연동 (#90)](055-apple-main-feature-ribs.md) |
| 056 | 2026-11-02 | [범용 정적 검증 플러그인 승격 및 멀티 플랫폼 자동 검증 루프 고도화 (#91, #92)](056-static-verification-modularization-and-multiplatform-gate.md) |
| 057 | 2026-11-02 | [웹 암호화 백업 내보내기와 복원 (VLT-004)](057-web-encrypted-backup.md) |
| 058 | 2026-11-02 | [AGENTS.md 범용화 및 작업 전 최신화·PR 전 Rebase 강제 게이트 구축](058-agents-md-and-rebase-enforcement.md) |
| 059 | 2026-11-02 | [웹 세션 파일 분리(동작 변경 없음)](059-web-session-split.md) |
| 060 | 2026-11-02 | [Apple VaultListFeature microRIBs 볼트 목록 및 소각 연동 (#97)](060-apple-vault-list-feature-ribs.md) |
| 061 | 2026-11-02 | [Apple VLT-004 암호화 백업 내보내기와 복원 클라이언트 영지식 백업 (#101)](061-apple-encrypted-backup-vlt-004.md) |
| 062 | 2026-11-02 | [Apple SettingsFeature microRIBs 환경설정 및 백업/자동잠금 연동 (#103)](062-apple-settings-feature-ribs.md) |
| 063 | 2026-11-02 | [Web 와이어프레임(03~06) 기반 UI 레이아웃 정렬 및 컴포넌트 구조화 (#106)](063-web-wireframe-layout-alignment.md) |
| 064 | 2026-11-02 | [Apple PasswordGeneratorFeature microRIBs 비밀번호 생성기 구현 (#105)](064-apple-password-generator-ribs.md) |
| 065 | 2026-11-02 | [마스터 비밀번호 변경 (SEC-004)](065-master-password-change.md) |
| 066 | 2026-11-02 | [Web 사이드바 마스터 비밀번호 변경 버튼 스타일 정합성 개선 (#109)](066-web-change-password-sidebar-styling.md) |
| 067 | 2026-11-02 | [Apple SEC-004 마스터 비밀번호 변경 및 원격 KDF 변경 감지 (#110)](067-apple-sec-004-master-password-change.md) |
| 068 | 2026-11-02 | [계정 삭제 (SEC-005)](068-account-deletion.md) |
| 069 | 2026-11-02 | [Apple VLT-001 볼트 아이템 추가, 수정, 삭제(CRUD) 모달 구현 (#114)](069-apple-vault-item-crud-vlt-001.md) |
| 070 | 2026-11-02 | [Apple 시스템 네비게이션 표준 정렬 및 뷰포트 왜곡 수정 (#115)](070-apple-system-navigation-and-viewport-fix.md) |
| 071 | 2026-11-02 | [Apple SEC-005 계정 삭제 (회원 탈퇴) UI 및 도메인 연동 (#116)](071-apple-sec-005-account-deletion.md) |
| 072 | 2026-11-02 | [Apple SEC-002 원격 계정 로그인 및 회원가입 UI 연동 (#117)](072-apple-sec-002-auth-feature.md) |
| 073 | 2026-11-02 | [Apple VLT-001 & VLT-002 원격 볼트 동기화 파이프라인 및 백그라운드 동기화 (#118)](073-apple-vault-sync-vlt-001.md) |
| 074 | 2026-11-02 | [Apple Feature RIBs State-Action 단방향 아키텍처 통일](074-apple-ribs-state-action-refactor.md) |
| 075 | 2026-11-11 | [디자인 시스템 공통 계약층·플랫폼 표현층 분리와 흑백 팔레트](075-design-system-layers-and-monochrome.md) |
| 076 | 2026-11-12 | [가입·항목 저장 폼 비밀번호 강도 게이지와 다크모드 버튼 수정 (#122)](076-signup-strength-gauge.md) |
| 077 | 2026-11-12 | [기기 암호를 끌 때 생체 해제 무효화 정책 추가](077-sec-003-passcode-off-biometric.md) |
| 078 | 2026-11-14 | [디자인 토큰 JSON 원본과 웹·Apple 코드 생성기 도입](078-design-token-generation.md) |
| 079 | 2026-11-14 | [Apple 화면 간격·모서리 리터럴을 토큰으로 교체하고 금지 게이트 추가](079-apple-spacing-radius-tokens.md) |
| 080 | 2026-11-14 | [Apple 화면 색상 리터럴을 의미 토큰으로 교체하고 금지 게이트 추가](080-apple-color-tokens.md) |
| 081 | 2026-11-14 | [웹 화면 간격·색 리터럴을 토큰으로 맞추고 검사 스크립트 추가](081-web-design-literal-gate.md) |
| 082 | 2026-11-14 | [타이포그래피 역할 토큰 도입과 웹·Apple 적용](082-typography-role-tokens.md) |
| 083 | 2026-11-14 | [컴포넌트 계약 게이트 추가와 문서 어긋남 정정](083-component-contract-gate.md) |
