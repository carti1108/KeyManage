# Apple 클라이언트 문서

> **요약** iOS·macOS 클라이언트의 플랫폼 문서(ADR, 구현 계약, 플랫폼 바인딩, 실험 보고서)를 모은다. 공통 정책과 공통 ADR은 루트 [`docs/`](../../../docs/README.md)를 따른다.

---

## 1. 아키텍처 개요

| 항목 | 내용 |
| :--- | :--- |
| 기반 라이브러리 | `microRIBs` (Swift 6 Strict Concurrency, `@MainActor`, `AsyncStream`) |
| 설계 원칙 | Clean Architecture + microRIBs + SwiftUI |
| iOS·macOS 공유 | `Domain`, `Data`, `Interactor`, `Viewless RIBs`는 iOS와 macOS가 같은 코드를 쓴다. |
| 플랫폼별 분리 | 화면 전환이 다른 `Viewable Router`, `Builder`, 최상위 컨테이너 `View`는 플랫폼별로 나눈다. |

> **주의** 공통 정책([`docs/policies/`](../../../docs/policies/README.md))은 Apple 구현 PR에서 고치지 않는다. 정책에 빈틈이 보이면 정책 이슈로 올린다.

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
