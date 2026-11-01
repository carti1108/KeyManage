### ADR-0002 Kotlin/Spring Boot 기반 포트-어댑터 멀티 모듈 백엔드 채택
- 상태: 채택
- 날짜: 2026-10-27
- 배경: 비즈니스 로직(암호 생성 규칙, 엔트로피 감사 알고리즘)과 입출력 인프라(REST 컨트롤러, 저장소)의 관심사를 엄격히 분리하고, 향후 개발할 Android(Kotlin) 클라이언트와의 모델 및 언어적 일관성을 확보해야 했다.
- 결정: 기존 `MusicSearch/Back`의 검증된 헥사고날(Hexagonal / Ports and Adapters) 4개 멀티모듈 구조(`domain`, `api`, `infrastructure`, `app`)를 그대로 계승한 Kotlin 1.9 + Spring Boot 3.4 백엔드를 구축한다.
- 고려했던 대안: (1) Next.js Serverless Route Handlers (TypeScript) — Vercel 원클릭 배포는 쉬우나 안드로이드 클라이언트와의 도메인 모델 공유가 불가능하고 모듈화 경계가 느슨해져 기각. (2) Ktor 기반 백엔드 — 경량 비동기 장점이 있으나 팀 레퍼런스(`MusicSearch`)가 Spring Boot 멀티모듈로 이미 완성도 높게 검증되어 있어 러닝커브 및 설정 리스크 방지를 위해 기각.
- 결과: 도메인 계층이 프레임워크로부터 완전히 격리되어 단위 테스트가 매우 용이해지고, Android 개발 시 Kotlin 도메인 모델과 DTO를 재사용할 수 있다. 반면 초기 모듈 분리(Gradle 설정)로 인한 프로젝트 구조의 복잡도가 다소 증가한다.
- 관련: ADR-0001, ADR-0003
