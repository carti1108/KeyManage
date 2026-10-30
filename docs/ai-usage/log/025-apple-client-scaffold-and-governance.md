# 2026-10-30 · Apple 멀티플랫폼 모듈 스캐폴딩 및 microRIBs 로컬 연동 (#38)

> 요약: Apple 플랫폼(iOS & macOS)의 개발 및 거버넌스 보존을 위해 `docs/iOS/` 체계를 수립하고, Swift 6 Strict Concurrency 기반의 `microRIBs` 로컬 패키지를 연동하는 `Apps/Apple` Tuist 기반 Micro-Features 5계층 모듈화 아키텍처(ADR-0015)를 구축했다. 공통 Interactor와 플랫폼별 Router/Builder/View의 물리적 분리 원칙을 확립하고(ADR-0014), SwiftUI `NSHostingController` / `UIHostingController`를 `ViewControllable`로 브릿징하는 어댑터를 구현했다.

## 사용 도구

- 도구: Antigravity, Swift 6 Toolchain, Tuist 4.x
- 플러그인/스킬: `adr-manager`, `project-docs:ai-usage`, `policy-manager`, `ios-architect:tuist-modularization`, `ios-architect:state-patterns-ribs-tca`, `ios-architect:clean-architecture-layers`, `code-reviewer:anti-pattern-concurrency-audit`

## 요청

- Apple 플랫폼(iOS 및 macOS) 개발을 위한 전용 문서 거버넌스(`docs/iOS/`)를 수립하고, `microRIBs` 및 Tuist Micro-Features 표준을 기반으로 한 멀티플랫폼 프로젝트 골격 구성 요청.

## AI 산출물

- `docs/adr/`: ADR-0014 (microRIBs 채택 및 macOS 형제 계층 구조), ADR-0015 (Tuist Micro-Features 5계층 모듈화 아키텍처 채택) 작성.
- `docs/iOS/`: Apple 플랫폼 아키텍처 개요 및 관련 ADR/AI 로그 인덱스 수립.
- `Apps/Apple/`: Tuist 기반 워크스페이스, `ProjectDescriptionHelpers` (Feature 및 Core 팩토리), Core(`Domain`, `Data`, `DesignSystem`), Feature(`UnlockFeature` 5개 타깃), App(`KeyManageMac`, `KeyManageIOS`) 프로젝트 구성.
- `SwiftUIViewControllable.swift`: SwiftUI View를 `microRIBs.ViewControllable` 인터페이스에 무결하게 연결하는 크로스플랫폼 호스팅 어댑터.

## 사람의 판단

- 기존 프레임워크(`microRIBs`)의 원본을 임의 변경하는 대신, 하위 호환성을 100% 보장하도록 AppKit 지원을 추가 검증한 뒤 로컬 의존성으로 안정적으로 활용하기로 결정했다.
- 웹 및 백엔드 개발과의 문서 충돌을 방지하고 도메인 집중도를 높이기 위해 `docs/iOS/` 하위에 전용 가이드와 인덱스를 운영하되, 전사 거버넌스(`docs/adr/`, `docs/ai-usage/log/`)의 연속 순번 원칙을 엄격히 준수하기로 결정했다.
- `carti-marketplace`의 `tuist-modularization` 표준을 적용하여 `Interface`, `Implementation`, `Testing`, `Tests`, `Example` 5개 타깃으로 모듈을 엄격히 분할하고, DIP에 따라 구현체 간 직접 참조를 원천 차단하기로 결정했다.

## 검증

- `Scripts/check-docs.sh` 실행을 통한 문서 링크 및 표 정합성 검증 완료.
- `tuist generate`를 통한 Xcode 프로젝트 생성 및 의존성 그래프 검증.
- 4개 레이어 분할 독립 서브에이전트 리뷰(Build & Infra, Core, Feature, Governance)를 수행하고 지적된 결함(P1/P2) 전수 조치.

## 검증하지 못한 것

- 실제 볼트 암복호화 및 백엔드 REST API 연동 동작은 다음 단계인 CryptoKit 및 UseCase 구현 티켓에서 검증할 예정이다.

## AI의 오류와 정정

- 초안 계획에서는 모바일과 데스크톱의 Router를 하나의 파일에서 인라인 조건부 컴파일로 다루려 하였으나, 화면 전환 패러다임 차이로 인한 코드 복잡도를 방지하기 위해 플랫폼별 Router/Builder 물리 분리 구조로 개선하였다.
- macOS 환경에서 메인 윈도우가 닫힐 때 메뉴바 팝오버가 동반 해제되는 수직형 트리의 문제점을 감지하고, `LoggedInRIB` 하위에 형제(Sibling) 관계로 분리 배치하도록 교정하였다.
- 초기 스캐폴딩 후 독립 서브에이전트 리뷰 단계를 임의 생략하려던 오류를 사용자의 지적으로 바로잡고, 모듈별 4개 서브에이전트 정밀 리뷰를 통해 P1(ADR 식별자 충돌, Swift 6 빌드 플래그 누락) 이슈를 발굴 및 해결하였다.

## 근거 링크

- 이슈 #38
