### ADR-0015 Tuist 기반 Micro-Features 5계층 모듈화 아키텍처 채택
- 상태: 채택
- 날짜: 2026-10-30
- 배경: Apple 멀티플랫폼(iOS & macOS) 클라이언트의 확장성, 모듈 간 결합도 완화, 클린 아키텍처 준수 및 빌드 속도 최적화를 위해 체계적인 모듈화 도구와 구조가 필요하다. 단일 프로젝트 또는 느슨한 멀티타깃 구조는 Feature 간의 불필요한 결합을 유발하고, 구현체(Implementation) 간의 직접 참조로 인해 의존성 역전 원칙(DIP)이 훼손되기 쉽다.
- 결정: (1) 프로젝트 생성 및 의존성 관리 도구로 Tuist를 채택한다. (2) carti-marketplace의 `tuist-modularization` 표준에 따라 모든 비즈니스 Feature 모듈을 5개 단위 타깃(`Interface`, `Implementation`, `Testing`, `Tests`, `Example`)으로 분할한다. (3) 의존성 역전 원칙에 따라 Feature 간 직접 참조를 일체 금지하고 오직 `Interface`만 참조하도록 제한한다. (4) microRIBs 아키텍처와 결합 시 `Interface`에는 `Buildable`, `Routing`, `Listener`, `Dependency`, 공유 모델만 배치하고, 구체적인 `Builder`, `Router`, `Interactor`, `ViewControllable`, `Component`는 `Implementation` 내부에 캡슐화한다. (5) 공통 기반 모듈은 `Core/Domain`, `Core/Data`, `Core/DesignSystem`, `Core/Infrastructure`, `Core/Testing`으로 분리하여 전역 재사용성을 확보한다.
- 고려했던 대안: (1) 순수 SPM 단일 패키지 구조 — 소규모 프로젝트에는 간결하나 Interface/Implementation 타깃 분리 및 빌드 캐시, 리소스 번들링, 데모 앱(Example) 타깃 관리에 제약이 있어 기각. (2) Xcode 수동 프로젝트 워크스페이스 — .pbxproj 파일 충돌이 빈번하고 의존성 규칙 강제가 불가능하여 기각.
- 결과: Feature 간 완벽한 경계 분리와 병렬 빌드 캐시 효율을 확보하며, RIBs의 계약 중심 설계가 물리적 타깃 레벨에서 강제된다. 반면 Tuist 스캐폴딩 및 헬퍼 파일 관리 비용이 수반된다.
- 관련: SEC-001, ADR-0014, 이슈 #38
