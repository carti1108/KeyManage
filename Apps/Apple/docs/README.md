# Apple 클라이언트 문서

> **요약** iOS·macOS 클라이언트의 플랫폼 문서(ADR, 구현 계약, 플랫폼 바인딩, 실험 보고서)를 모은다. 공통 정책과 공통 ADR은 루트 [`docs/`](../../../docs/README.md)를 따른다.

---

## 1. 아키텍처 개요

| 항목 | 내용 |
| :--- | :--- |
| 공유 로직 | Kotlin Multiplatform XCFramework `KeyManageShared`(`Apps/Shared/apple-app`). 도메인·세션·RIB 라우팅·화면 상태는 웹과 같은 Kotlin 코드다 |
| 설계 원칙 | 공유 Kotlin RIB + SwiftUI 뷰 계약(`RIBsKMPSwiftUI`) |
| iOS·macOS 공유 | SwiftUI 화면(`Sources/SharedUI`)과 Kotlin `appleMain`을 함께 쓴다 |
| 플랫폼별 분리 | 진입점·문서 선택기(`Sources/iOS`, `Sources/macOS`), 클립보드(Kotlin `iosMain`·`macosMain`), 배치 차이(`#if os(macOS)`) |

옛 Swift microRIBs 구조(ADR-0014, ADR-0015)는 [domain-migration-plan 12.3](../../../docs/research/kmp-migration/domain-migration-plan.md) 4단계에서 지웠다.

공통 정책([`docs/policies/`](../../../docs/policies/README.md))은 Apple 구현 PR에서 고치지 않는다. 정책에 빈틈이 보이면 정책 이슈로 올린다.

---

## 2. 구현 계약

| 계약 | 내용 |
| :--- | :--- |
| 생체 해제용 키 보관 ([SEC-003](../../../docs/policies/SEC/SEC-003-unlock-and-auto-lock.md)) | 생체 인증으로 보호되는 Keychain 항목에 둔다. 접근성은 `ThisDeviceOnly`다. iCloud 동기화와 백업에서 뺀다. |

---

## 3. 문서 목록

| 문서 | 내용 |
| :--- | :--- |
| [`adr/`](adr/README.md) | Apple ADR(ADR-0014~0017). 새 결정은 `ADR-APL-NNN` |
| [`platform-bindings.md`](platform-bindings.md) | 암호 라이브러리, 색상 토큰, 글꼴, 아이콘 이름 |
| [`reports/`](reports/) | 단일 에이전트와 서브에이전트 비교 실험 보고서 |

AI 활용 기록은 공통 로그 [`docs/ai-usage/log/`](../../../docs/ai-usage/log/README.md)에 쓴다.
