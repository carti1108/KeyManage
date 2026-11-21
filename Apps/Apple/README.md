# 🍎 KeyManage Apple 클라이언트 (iOS & macOS)

iOS 및 macOS 멀티플랫폼을 지원하는 네이티브 영지식(Zero-Knowledge) 패스워드 매니저 클라이언트입니다.

화면 로직(RIB)·도메인·플랫폼 구현은 웹·Android와 함께 쓰는 공유 Kotlin(`Apps/Shared`)에 있고, 이 폴더에는 SwiftUI 뷰와 앱 진입점만 둡니다([ADR-APL-002](docs/adr/ADR-APL-002-shared-kotlin-ribs.md)).

---

## 🛠️ 기술 스택

| 분류 | 내용 |
| :--- | :--- |
| **Language** | Swift 6 (Strict Concurrency), 공유 코드는 Kotlin Multiplatform |
| **Architecture** | 공유 Kotlin RIB(carti-RIBs-KMP). Swift 뷰는 RIB의 `ViewContract`를 구현하고 `RIBsKMPSwiftUI`의 `RIBScreenSlot`으로 자식 화면을 붙인다 |
| **공유 코드 연결** | 정적 XCFramework `KeyManageShared` 두 벌: 운영용(`apple-session`), 테스트용(`apple-example`, 시나리오·가짜 세션 포함) |
| **Modularization** | Tuist 4. 피처마다 `Sources`(뷰)·`Example`(시나리오 앱)·`ViewTests` |
| **Security & Crypto** | 공유 Kotlin 도메인. iOS 생체 해제는 LocalAuthentication과 생체 전용 Keychain 항목 |
| **최소 버전** | iOS 17, macOS 14 |
| **Testing** | Swift Testing(뷰 테스트), XCTest UI 테스트(앱 흐름), 공유 Kotlin 테스트 |

---

## 🧩 모듈 구조

```text
Apps/Apple/
├── Projects/
│   ├── App/
│   │   ├── Sources/iOSShared/      # iOS 진입점(AppleKeyManageApp 시작, 생명주기·터치 알림)
│   │   ├── Sources/macOSShared/    # macOS 진입점
│   │   ├── Sources/AppleShared/    # 두 앱이 함께 쓰는 뷰 팩토리와 루트 화면
│   │   └── UITests/Shared/         # 가입 → 항목 추가 → 잠금 → 잠금 해제 흐름 (로컬 백엔드 필요)
│   ├── Features/<Name>/            # Unlock, Auth, PasswordGenerator, VaultList, Settings, Main
│   │   ├── Sources/                # SwiftUI 뷰 (앱·Example·뷰 테스트가 직접 컴파일)
│   │   ├── Example/                # 시나리오 목록 → 그 피처의 RIB 하나만 띄우는 앱
│   │   └── ViewTests/              # 상태를 그리는가, 입력을 보내는가
│   └── Core/
│       ├── DesignSystem/           # 디자인 토큰과 공용 컴포넌트
│       ├── FeatureExample/         # Example 앱의 시나리오 목록 틀
│       └── SharedUI/               # 여러 뷰가 함께 쓰는 SwiftUI 조각(오류 문구, 대화상자 바인딩, 입력 수정자)
├── Tuist/                          # Tuist 설정과 RIBsKMPSwiftUI(형제 폴더 carti-RIBs-KMP) 패키지
└── docs/                           # Apple 플랫폼 전용 ADR
```

---

## 🧪 빌드 및 검증

JDK 21과 carti-RIBs-KMP(저장소 옆 폴더 `../carti-RIBs-KMP` 또는 환경 변수 `CARTI_RIBS_DIR`)가 필요합니다.

```bash
# 1. 정적 검사, 공유 Kotlin 묶음 빌드, 앱 빌드, 뷰 테스트 통합 검증
bash Scripts/verify-apple.sh

# 2. 묶음 프레임워크만 만들고 워크스페이스 생성
cd Apps/Shared && ./gradlew :apple-session:assembleKeyManageSharedDebugXCFramework :apple-example:assembleKeyManageSharedDebugXCFramework
cd ../Apple && tuist install && tuist generate --no-open
```

앱 UI 테스트는 로컬 백엔드(`Back`에서 `./gradlew :app:bootRun`)를 띄운 뒤 `KeyManageIOS` 스킴의 test로 돌립니다.

---

## 🔧 트러블 슈팅

- **설정 화면 버튼이 눌리지 않음**: 자동 잠금 시간을 다시 재려고 루트 뷰에 SwiftUI 탭 제스처를 걸면 `List` 안 버튼의 탭을 가로챈다. 창에 터치를 보기만 하고 바로 실패하는 UIKit 인식기를 단다(macOS는 `NSEvent` 로컬 감시).
- **`KeyManageShared.framework`를 두 번 만든다는 빌드 오류**: 운영용·테스트용 묶음은 이름이 같아, 앱과 Example을 한 번에 빌드하면 같은 결과 폴더에 겹친다. 테스트용을 쓰는 타깃은 `SharedTesting/` 결과 폴더를 쓴다(`SharedFeature.testingBundleSettings`).
- **"모든 iOS 시뮬레이터" 빌드의 링크 오류**: 공유 Kotlin 묶음은 arm64 시뮬레이터만 낸다. `ARCHS=arm64`로 빌드한다.
- **`render(state____:)` 같은 Swift 이름**: 피처마다 같은 이름의 메서드가 Objective-C로 내보내질 때 선언 순서에 따라 이름이 바뀐다. 공유 interface 매개변수에 `@ObjCName`을 붙여 `render(unlockState:)`처럼 고정한다.
