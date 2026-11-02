# KeyManage Web·데스크톱 클라이언트

브라우저 및 데스크톱 네이티브 셸(Tauri v2) 환경을 동시 지원하는 크로스 플랫폼 영지식(Zero-Knowledge) 패스워드 매니저 클라이언트입니다 ([ADR-0010](../../docs/adr/ADR-0010-client-composition-ios-and-web-sharing.md), [ADR-0012](docs/adr/ADR-0012-web-client-and-desktop-wrapper.md)).

서버 사이드 렌더링을 배제한 순수 정적 클라이언트로 구동되며, 오직 클라이언트 내부에서만 마스터 키 유도, 볼트 암·복호화 및 데이터 조작을 독립 처리합니다 ([SEC-001](../../docs/policies/SEC/SEC-001-client-side-zero-knowledge-encryption.md)).

---

## 기술 스택

| 분류 | 기술 규격 및 사양 |
| :--- | :--- |
| **Language** | TypeScript 5.8 |
| **Framework & Build** | React 19, Vite 6 |
| **Desktop Shell** | Tauri v2, Rust |
| **Styling** | Tailwind CSS v4, shadcn/ui 디자인 토큰 |
| **Security & Crypto** | WebCrypto API (`globalThis.crypto.subtle`), IndexedDB 암호화 저장소 |
| **Testing & Lint** | Vitest, ESLint |

---

## 모듈 구조

클린 아키텍처(Clean Architecture) 원칙에 따라 관심사를 물리적으로 분리하고, 핵심 비즈니스 도메인이 UI 컴포넌트나 브라우저 전역 객체에 직접 의존하지 않도록 격리합니다 ([ADR-0013](docs/adr/ADR-0013-api-client-and-account-auth.md)).

```text
Apps/Web/
├── src/
│   ├── domain/             # 순수 비즈니스 도메인
│   │   ├── auth/           # 사전 로그인, 회원가입, 로그인, 비밀번호 변경, 계정 삭제 포트 및 모델
│   │   ├── crypto/         # PBKDF2-HMAC-SHA256, HKDF-SHA256, AES-256-GCM 암호화·백업 모듈
│   │   ├── generator/      # CSPRNG 비밀번호 생성기 및 엔트로피·연속 숫자 진단기 (GEN-001)
│   │   ├── lock/           # 유휴 타이머 자동 잠금 및 메모리 소거 제어 (SEC-003)
│   │   ├── session/        # 세션 상태 전이, 오프라인 대기 큐, 백업 내보내기/복원 오케스트레이션
│   │   └── vault/          # 볼트 스키마 검증, 낙관적 잠금 동기화, 3-way 항목 병합 (VLT-001~004)
│   ├── adapters/           # 외부 인프라 어댑터
│   │   ├── http/           # 백엔드 REST API 통신 어댑터
│   │   └── storage/        # IndexedDB 기기 암호화 저장소 및 BroadcastChannel 다중 탭 동기화
│   ├── ui/                 # 도메인 포트를 주입받아 렌더링하는 프레젠테이션 컴포넌트 (Dumb View)
│   ├── app/                # 조립 루트(Composition Root) 및 전역 라우팅
│   └── integration/        # 실제 백엔드 연동 통합 테스트
├── src-tauri/              # Tauri v2 데스크톱 네이티브 셸 (Rust)
└── docs/                   # Web·데스크톱 플랫폼 전용 ADR 및 기기 저장소 명세
```

---

## 데스크톱 앱 (Tauri v2)

동일한 웹 UI 번들을 경량 Rust 네이티브 윈도우 창으로 감싸 독립된 데스크톱 애플리케이션으로 구동합니다.

- **엄격한 CSP 및 출처 격리**: 스크립트와 스타일은 로컬 패키지 자원으로만 한정하며, 외부 네트워크 통신은 빌드 시점에 지정된 백엔드 서버 출처(`VITE_API_BASE_URL`)로만 제한합니다 ([ADR-0020](docs/adr/ADR-0020-desktop-release-api-origin.md)).
- **최소 권한 원칙**: 시스템 API 접근 권한은 필요한 기능 단위로만 최소한으로 허용합니다.

---

## 빌드 및 검증

```bash
cd Apps/Web
npm ci
npm run lint
npm test
npm run build
```

---

## 트러블 슈팅

- **다중 브라우저 탭 간 기기 저장소 충돌 방지**: 여러 탭에서 동시에 볼트를 수정하거나 잠금/로그아웃할 때 상태 불일치가 발생하지 않도록 IndexedDB 변경 이벤트와 `BroadcastChannel`을 연동해 탭 간 세션·잠금 상태를 즉시 동기화했습니다.
- **Tauri 데스크톱 릴리스 빌드의 API 출처(Origin) 연결**: 데스크톱 앱의 커스텀 프로토콜(`tauri://localhost`)에서 상대 경로 API 호출이 실패하는 문제를 방지하기 위해 빌드 타임 `VITE_API_BASE_URL` 검증 및 CSP `connect-src` 동적 주입을 적용했습니다 ([ADR-0020](docs/adr/ADR-0020-desktop-release-api-origin.md)).
- **오프라인 수정 중 로그아웃 데이터 유실 방지**: 미동기화 변경분(`pendingSync`)이 남아 있는 상태에서 로그아웃을 시도하면 경고 대화상자를 먼저 노출하고, 사용자가 확인한 경우에만 세션을 종료하도록 보호했습니다 ([VLT-002](../../docs/policies/VLT/VLT-002-offline-pending-sync.md)).
