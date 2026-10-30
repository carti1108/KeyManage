# KeyManage Apple 플랫폼 (iOS & macOS) 엔지니어링 거버넌스

이 디렉터리는 `KeyManage` Apple 클라이언트(iOS 및 macOS) 개발을 위한 전용 아키텍처 결정 기록(ADR)과 AI 협업 로그를 보존하는 독립 지식베이스입니다.

---

## 1. 플랫폼 아키텍처 개요

* **기반 라이브러리**: `microRIBs` (Swift 6 Strict Concurrency, `@MainActor`, `AsyncStream`)
* **설계 원칙**: Clean Architecture + microRIBs + SwiftUI
* **공유 정책**:
  * `Domain`, `Data`, `Interactor`, `Viewless RIBs`는 iOS와 macOS가 **100% 동일한 코드를 공유**.
  * 화면 전환이 상이한 `Viewable Router`, `Builder`, 최상위 컨테이너 `View`는 플랫폼별로 물리 분리.
* **불변 원칙**:
  * 서비스 정책([`docs/policies/`](../policies/README.md))은 어떠한 경우에도 임의 수정하지 않으며 100% 준수한다.

---

## 2. 관련 전사 거버넌스 및 인덱스

* [`adr/`](adr/README.md): Apple 플랫폼 아키텍처 결정 기록 인덱스 ([ADR-0014](../adr/ADR-0014-apple-multiplatform-microribs.md), [ADR-0015](../adr/ADR-0015-tuist-micro-features-modularization.md))
* [`ai-usage/`](ai-usage/README.md): Apple 플랫폼 기능 구현 및 AI 페어프로그래밍 작업 일지 인덱스 ([025](../ai-usage/log/025-apple-client-scaffold-and-governance.md))
