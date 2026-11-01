# KeyManage Apple 플랫폼 (iOS & macOS) 엔지니어링 거버넌스

이 디렉터리는 `KeyManage` Apple 클라이언트(iOS 및 macOS)의 플랫폼 문서(ADR, 구현 계약, 실험 보고서)를 모은다. 공통 정책과 공통 ADR은 루트 [`docs/`](../../../docs/README.md)를 따른다.

---

## 1. 플랫폼 아키텍처 개요

* **기반 라이브러리**: `microRIBs` (Swift 6 Strict Concurrency, `@MainActor`, `AsyncStream`)
* **설계 원칙**: Clean Architecture + microRIBs + SwiftUI
* **공유 정책**:
  * `Domain`, `Data`, `Interactor`, `Viewless RIBs`는 iOS와 macOS가 **100% 동일한 코드를 공유**.
  * 화면 전환이 상이한 `Viewable Router`, `Builder`, 최상위 컨테이너 `View`는 플랫폼별로 물리 분리.
* **불변 원칙**:
  * 서비스 정책([`docs/policies/`](../../../docs/policies/README.md))은 어떠한 경우에도 임의 수정하지 않으며 100% 준수한다.

---

## 2. 플랫폼 구현 계약

* **생체 해제용 키 보관** ([SEC-003](../../../docs/policies/SEC/SEC-003-unlock-and-auto-lock.md)): 생체 인증으로 보호되는 Keychain 항목에 두고, 접근성은 `ThisDeviceOnly`로 한다. iCloud 동기화와 백업에서 뺀다.

---

## 3. 문서 목록

* [`adr/`](adr/README.md): Apple 플랫폼 ADR (ADR-0014~0017, 새 결정은 `ADR-APL-NNN`)
* [`platform-bindings.md`](platform-bindings.md): 암호 라이브러리, 색상 토큰, 글꼴, 아이콘 이름
* [`reports/`](reports/): 단일 에이전트와 서브에이전트 비교 실험 보고서
* AI 활용 기록은 공통 로그 [`docs/ai-usage/log/`](../../../docs/ai-usage/log/README.md)에 쓴다.
