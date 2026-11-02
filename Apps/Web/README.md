# KeyManage Web·데스크톱 클라이언트

브라우저 및 데스크톱 네이티브 셸(Tauri v2) 환경을 동시 지원하는 크로스 플랫폼 클라이언트입니다 ([ADR-0010](../../docs/adr/ADR-0010-web-client-stack-and-architecture.md), [ADR-0012](../../docs/adr/ADR-0012-tauri-desktop-wrapper.md)).

서버 사이드 렌더링을 배제한 순수 정적 클라이언트로 구동되며, 오직 클라이언트 내부에서만 키 유도, 볼트 복호화 및 데이터 조작을 독립 처리합니다 ([SEC-001](../../docs/policies/SEC-001-zero-knowledge-encryption.md)).

---

## 기술 스택

| 분류 | 기술 규격 및 사양 |
| :--- | :--- |
| **Language** | TypeScript 5.8 |
| **Framework & Build** | React 19, Vite 6 |
| **Desktop Shell** | Tauri v2, Rust |
| **Styling** | Tailwind CSS v4, shadcn/ui |
| **Security & Crypto** | WebCrypto API (`globalThis.crypto`) |
| **Testing & Lint** | Vitest, oxlint |

---

## 모듈 구조

클린 아키텍처(Clean Architecture) 원칙에 따라 관심사를 물리적으로 분리하고, 핵심 비즈니스 도메인이 UI 컴포넌트나 브라우저 전역 객체에 직접 의존하지 않도록 격리합니다.

```text
src/
├── domain/     # 순수 비즈니스 로직: 암호 엔진 연동, 볼트 모델, 3-way 병합, 포트(인터페이스)
├── adapters/   # 인프라 어댑터: REST 통신, IndexedDB 암호화 스토리지, 클립보드 버퍼 제어
├── ui/         # 프레젠테이션 계층: 도메인 포트를 주입받아 구동되는 덤 뷰(Dumb View)
└── app/        # 조립 지점(Composition Root): 어댑터를 인스턴스화하여 화면에 의존성 주입

src-tauri/      # 데스크톱 네이티브 셸: Rust 기반 윈도우 프레임 및 시스템 트레이 제어
```

---

## 데스크톱 앱 (Tauri v2)

동일한 웹 UI 번들을 경량 Rust 네이티브 윈도우 창으로 감싸 독립된 데스크톱 애플리케이션으로 구동합니다.

- **엄격한 CSP 격리**: 스크립트와 스타일은 로컬 패키지 자원으로만 한정하며, 외부 네트워크 통신은 빌드 시점에 사전에 지정된 백엔드 서버 출처 1곳으로만 엄격히 차단·제한합니다 ([ADR-0013](../../docs/adr/ADR-0013-desktop-csp-and-api-origin.md)).
- **최소 권한 원칙**: 시스템 API 접근 권한은 필요한 기능 단위로만 최소한으로 허용합니다.

---

## 트러블 슈팅

웹 및 데스크톱 클라이언트 빌드, 브라우저 WebCrypto API 호환성 및 Tauri 네이티브 셸 연동 관련 주요 문제 해결 내역을 정리합니다.
