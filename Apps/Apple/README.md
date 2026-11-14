# 🍎 KeyManage Apple 클라이언트 (iOS & macOS)

iOS·macOS 영지식(Zero-Knowledge) 패스워드 매니저 클라이언트입니다.

두 앱은 웹과 같은 공유 Kotlin RIB 트리(`Apps/Shared`)를 씁니다. 도메인·세션·화면 상태·RIB 라우팅은 Kotlin이 정하고, Swift에는 SwiftUI 화면·앱 진입점·디자인 시스템만 남깁니다([domain-migration-plan 12장](../../docs/research/kmp-migration/domain-migration-plan.md#12-ios-전환-swift-rib을-공유-rib으로-설계-2026-11-13)). 옛 Swift microRIBs 구조([ADR-0014](docs/adr/ADR-0014-apple-multiplatform-microribs.md), [ADR-0015](docs/adr/ADR-0015-tuist-micro-features-modularization.md))는 12.3 4단계에서 지웠습니다.

---

## 🛠️ 기술 스택

| 분류 | 내용 |
| :--- | :--- |
| **공유 로직** | Kotlin Multiplatform 정적 XCFramework `KeyManageShared`(`Apps/Shared/apple-app`가 `:domain`·`:feature`·`ribs-core`를 내보냄) |
| **화면** | SwiftUI. 공유 RIB 뷰 계약(`XxxViewContract`)을 구현하고 `RIBsKMPSwiftUI`(carti-RIBs-KMP)의 `RIBSlotView`로 붙임 |
| **Language** | Swift 6 (Strict Concurrency). Kotlin 객체는 `@MainActor`에서만 다룸 |
| **Modularization** | Tuist 4. 앱 프로젝트(`App`)와 `Core/DesignSystem` |
| **Security & Crypto** | cryptography-kotlin(Apple 제공자), Keychain·LocalAuthentication(Face ID / Touch ID)은 `secure-store` `appleMain` |
| **Testing** | Kotlin 테스트(`:domain`·`:feature`·`:apple-app`), Swift Testing(DesignSystem), XCUITest(가입 → 잠금 → 해제, RIB별 흐름) |

---

## 🧩 모듈 구조

```text
Apps/Apple/
├── Projects/
│   ├── App/
│   │   ├── Sources/iOS/            # iOS 진입점(KeyManageIOSApp), 문서 선택기
│   │   ├── Sources/macOS/          # macOS 진입점(KeyManageMacApp, 창 하나), 저장·열기 패널
│   │   ├── Sources/SharedUI/       # 두 앱이 함께 쓰는 SwiftUI 화면 7개·뷰 팩토리·SharedAppLauncher
│   │   └── UITests/                # iOS·macOS XCUITest (로컬 백엔드 localhost:8080)
│   └── Core/
│       └── DesignSystem/           # 디자인 토큰과 공용 UI 컴포넌트 (+ Tests)
├── Tuist/                          # 프로젝트 설정 헬퍼(swift6Settings, makeCore)
└── docs/                           # Apple 플랫폼 전용 ADR 및 바인딩 명세

Apps/Shared/apple-app/              # Apple 진입점 Kotlin(AppleKeyManageApp, 수명주기 배선, 플랫폼 구현)
```

---

## 🧪 빌드 및 검증

```bash
# 1. 정적 린트·정책 감사·XCFramework·Tuist 생성·두 앱 빌드·DesignSystem 테스트
bash Scripts/verify-apple.sh

# 2. 공유 Kotlin 테스트 (Apple 앱의 도메인·화면 상태·수명주기 행동)
cd Apps/Shared
./gradlew :domain:jvmTest :feature:jvmTest :apple-app:allTests -x kotlinStoreYarnLock

# 3. 수동 생성·빌드
./gradlew :apple-app:assembleKeyManageSharedDebugXCFramework
cd ../Apple
tuist install && tuist generate --no-open
xcodebuild -workspace KeyManageApple.xcworkspace -scheme KeyManageMac -destination 'platform=macOS' build
```

`RIBsKMPSwiftUI`는 저장소 옆 형제 폴더 `carti-RIBs-KMP`에서 읽습니다. 다른 곳에 받았으면 `TUIST_CARTI_RIBS_DIR`(Tuist)와 `CARTI_RIBS_DIR`(Gradle)에 절대 경로를 줍니다.

---

## 🔧 트러블 슈팅

- **Kotlin 콜백과 Swift 동시성**: Kotlin/Native 객체는 Sendable이 아니므로 공유 코드 호출과 콜백은 `@MainActor` 뷰·`SharedAppLauncher`에서만 받습니다.
- **macOS 백그라운드 판정**: 장면 비활성은 저장·열기 패널이나 경고창이 키 창을 가져갈 때도 생기므로, macOS는 `NSApplication` 활성 알림으로 SEC-003 백그라운드를 판정합니다.
- **iOS/macOS 네비게이션 배치**: iOS는 `TabView` 안 탭마다 `NavigationStack`, macOS는 `NavigationSplitView` 사이드바를 씁니다(`#if os(macOS)`).
