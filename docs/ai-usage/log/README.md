# AI 활용 로그

이 디렉터리는 작업 단위별 AI 협업 기록을 시간 순서대로 보존합니다.

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
