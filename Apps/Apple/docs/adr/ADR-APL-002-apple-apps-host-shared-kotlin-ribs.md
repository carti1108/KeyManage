### ADR-APL-002 Apple 앱은 공유 Kotlin RIB 트리를 띄우고 Swift는 화면과 디자인 시스템만 둔다
- 상태: 채택
- 날짜: 2026-11-14
- 영역: Apple

> **요약** iOS·macOS 앱은 공유 Kotlin RIB 트리(XCFramework `KeyManageShared`)를 `RIBsKMPSwiftUI`로 띄우고, Swift에는 SwiftUI 화면·진입점·문서 선택기와 `Core/DesignSystem`만 남긴다. ADR-0014(microRIBs)와 ADR-0015(Tuist Micro-Features 5계층)를 대체한다.

#### 배경

웹은 공유 Kotlin 도메인·RIB(`Apps/Shared`)을 쓰고, Apple 앱은 같은 규칙을 Swift microRIBs와 Swift 도메인으로 따로 구현하고 있었다. 같은 정책([SEC-003](../../../../docs/policies/SEC/SEC-003-unlock-and-auto-lock.md), [COM-001](../../../../docs/policies/COM/COM-001-clipboard-auto-burn.md), [VLT-002](../../../../docs/policies/VLT/VLT-002-offline-pending-sync.md))을 두 언어로 지키느라 동작이 어긋날 수 있었다. 연구 브랜치(`research/kmp-migration`)에서 Apple 앱을 공유 RIB 위로 옮기는 실험을 0~4단계로 진행했고, 두 앱 빌드·Kotlin 테스트·iOS UI 테스트가 통과했다.

#### 결정

1. iOS 앱과 macOS 앱은 `Apps/Shared/apple-app`이 만드는 정적 XCFramework `KeyManageShared` 하나(iosArm64·iosSimulatorArm64·macosArm64)를 링크하고, 진입점 `AppleKeyManageApp`이 세션과 RIB 트리를 조립한다.
2. RIB 뷰 연결은 로컬 패키지 `RIBsKMPSwiftUI`가 맡는다. Swift 화면은 Kotlin 상태를 그리고 입력을 `listener`로 넘기기만 한다.
3. Swift에는 `Sources/SharedUI`(두 플랫폼 공용 화면), `Sources/iOS`·`Sources/macOS`(진입점·문서 선택기), `Core/DesignSystem`만 둔다. Swift microRIBs·`Core/Domain`·`Features/*`는 지운다.
4. 플랫폼 포트(기기 저장소, 클립보드, 키체인·생체 인증, 네트워크 상태, 자동 잠금 설정)는 Kotlin `appleMain`·`iosMain`·`macosMain`에서 구현한다.

#### 고려한 대안

| 대안 | 기각 이유 |
| :--- | :--- |
| Swift microRIBs를 그대로 둔다 | 웹과 Apple이 같은 정책을 두 번 구현하고 테스트도 두 벌이다. 정책을 바꿀 때마다 두 구현이 어긋날 위험이 남는다 |
| iOS만 공유 RIB으로 옮긴다 | macOS가 옛 Swift 도메인을 계속 쓰므로 Swift 도메인을 지울 수 없어 중복이 줄지 않는다 |
| iOS와 macOS를 함께 옮긴다 (채택) | 같은 XCFramework·같은 진입점·같은 SwiftUI 화면을 두 앱이 쓴다. 배치 차이는 `#if os(macOS)`로 둔다 |

#### 결과

- 얻는 것: Apple·웹이 같은 Kotlin 도메인·RIB을 쓴다. 정책 테스트가 한 벌(Kotlin `commonTest`·`appleTest`)이다. XCFramework는 하나다.
- 비용: iOS 최소 버전이 17.0, macOS 최소 버전이 14.0으로 오른다(`RIBsKMPSwiftUI` 요구). Kotlin/Native 빌드가 Apple 빌드 앞에 붙는다.
- 비용: Swift 전용 테스트는 지웠다. 같은 행동을 보는 Kotlin 테스트를 대조했고, 없던 것은 Kotlin으로 옮겼다.
- 남은 위험: macOS UI 테스트는 이 Mac의 UI 자동화 승인이 없어 돌지 않았다. 실제 기기의 Touch ID·Face ID 키 저장·해제를 확인하지 않았다. 잠글 때 키 바이트를 0으로 지우지 않고 참조만 버린다(SEC-003).

#### 관련

[SEC-003](../../../../docs/policies/SEC/SEC-003-unlock-and-auto-lock.md), [COM-001](../../../../docs/policies/COM/COM-001-clipboard-auto-burn.md), [ADR-0026](../../../../docs/adr/ADR-0026-ktor-client-for-shared-kotlin.md), [ADR-0027](../../../../docs/adr/ADR-0027-biometric-unlock-stores-derived-key.md), 대체: ADR-0014, ADR-0015
