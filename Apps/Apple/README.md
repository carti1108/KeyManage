# 🍎 KeyManage Apple 클라이언트 (iOS & macOS)

iOS 및 macOS 멀티플랫폼을 지원하는 네이티브 영지식(Zero-Knowledge) 패스워드 매니저 클라이언트입니다.

Tuist 4 마이크로 피처(Micro-Features) 모듈화 아키텍처([ADR-0015](docs/adr/ADR-0015-tuist-micro-features-modularization.md))와 Uber microRIBs + SwiftUI 단방향 `State`/`Action`(`request(action:)` / `update(state:)`) 패턴([ADR-0014](docs/adr/ADR-0014-apple-multiplatform-microribs.md))을 적용하여 화면 계층 간 강한 격리와 높은 테스트 가능성을 확보했습니다.

---

## 🛠️ 기술 스택

| 분류 | 기술 규격 및 사양 |
| :--- | :--- |
| **Language** | Swift 6 (Strict Concurrency) |
| **Architecture** | Uber microRIBs (Router, Interactor, Builder) + `ViewState`/`ViewAction` 단방향 바인딩 |
| **UI Framework** | SwiftUI (`UIHostingController` / `NSHostingController` 어댑터) |
| **Modularization** | Tuist 4 (`Interface`, `Implementation`, `Testing`, `Tests` 타깃 분리) |
| **Security & Crypto** | CommonCrypto (`PBKDF2-HMAC-SHA256`), CryptoKit (`HKDF-SHA256`, `AES-256-GCM`), LocalAuthentication (Face ID / Touch ID), Keychain |
| **Testing** | Swift Testing (`@Suite`, `@Test`, `#expect`) |

---

## 🧩 모듈 구조

각 Feature 모듈은 `Interface`, `Implementation`, `Testing`, `Tests` 4개 타깃으로 분리되어 상위/형제 RIB가 구체 구현체에 직접 의존하지 않도록 강제합니다.

```text
Apps/Apple/
├── Projects/
│   ├── App/                        # 애플리케이션 진입점 (KeyManageiOS, KeyManageMac, AppRootAdapter)
│   ├── Features/                   # 화면 단위 기능 모듈 (microRIBs 4-Target 구조)
│   │   ├── Root/                   # 세션·잠금 상태 감시 및 Auth / Unlock / Main 루트 라우팅
│   │   ├── Auth/                   # 원격 계정 회원가입 및 마스터 키 유도 로그인 (SEC-002)
│   │   ├── Unlock/                 # 마스터 비밀번호·생체 인증 잠금 해제 및 원격 KDF 변경 감지 (SEC-003, SEC-004)
│   │   ├── Main/                   # iOS TabView / macOS NavigationSplitView 메인 대시보드 셸
│   │   ├── VaultList/              # 볼트 항목 CRUD, 검색, 클립보드 복사, 원격 볼트 동기화 및 오프라인 배너 (VLT-001, VLT-002)
│   │   ├── PasswordGenerator/      # CSPRNG 비밀번호 생성기 및 엔트로피 진단기 (GEN-001, COM-001)
│   │   └── Settings/               # 자동 잠금 설정, 암호화 백업 내보내기/복원, 비밀번호 변경, 계정 삭제 (SEC-003~005, VLT-004)
│   └── Core/                       # 플랫폼 공통 비즈니스 코어 및 인프라
│       ├── Domain/                 # 영지식 암호 엔진, 볼트 3-way 병합, 잠금·동기화 매니저, 클립보드 30초 자동 소각
│       ├── Data/                   # Keychain·파일 기반 암호화 기기 볼트 저장소 어댑터 (VLT-003)
│       ├── DesignSystem/           # shadcn/ui 기반 시멘틱 디자인 토큰 및 5대 핵심 UI 컴포넌트
│       └── UIAdapter/              # microRIBs 플랫폼 호스팅 어댑터
├── Tuist/                          # Tuist 프로젝트 설정 및 로컬 패키지 매니페스트
└── docs/                           # Apple 플랫폼 전용 ADR 및 바인딩 명세
```

---

## 🧪 빌드 및 검증

macOS 환경에서 Tuist 워크스페이스를 생성한 뒤 통합 검증 스크립트(`Scripts/verify-apple.sh`) 또는 `xcodebuild`를 실행합니다.

```bash
# 1. 정적 아키텍처·보안 불변식·단위 테스트 통합 검증
bash Scripts/verify-apple.sh

# 2. 워크스페이스 생성 및 macOS 타깃 단독 빌드
cd Apps/Apple
tuist generate --no-open
xcodebuild -workspace KeyManage.xcworkspace -scheme KeyManageMac -destination 'platform=macOS' build
```

---

## 🔧 트러블 슈팅

- **Swift 6 비동기 컨텍스트 내 `NSLock` 컴파일 오류 및 데드락 방지**: `async` 메서드 내부에서 `NSLock.lock()`/`unlock()`을 직접 호출하면 Swift 6 Strict Concurrency 컴파일 오류가 발생하므로, 공유 상태 변경을 동기 헬퍼 메서드(`prepareSync`, `applySyncSuccess` 등)로 분리해 락 점유 구간을 동기 블록 내부로만 제한했습니다.
- **비동기 동기화 중 자동 잠금 시 평문 메모리 부활 차단**: 네트워크 요청 진행 중 유휴 타임아웃이나 수동 잠금이 발생한 경우, 응답 도착 시 `isLocked == true`이면 복호화된 평문 볼트를 메모리에 적재하지 않고 암호문과 리비전만 갱신하도록 방어했습니다.
- **iOS/macOS 멀티플랫폼 네비게이션 뷰포트 왜곡 해결**: iOS에서는 `TabView` 내부 각 탭에 `NavigationStack`을 배치하고, macOS에서는 `NavigationSplitView`/사이드바 레이아웃을 분리하여 플랫폼별 네비게이션 바 중복 및 여백 왜곡을 해결했습니다.
