### ADR-APL-002 Apple 앱이 공유 Kotlin RIB과 도메인을 쓴다
- 상태: 채택
- 날짜: 2026-11-21
- 영역: Apple

> **요약** iOS·macOS 앱의 화면 로직(RIB)·도메인·플랫폼 구현을 공유 Kotlin(Apps/Shared)으로 옮기고, Apple에는 SwiftUI 뷰와 진입점만 둔다. MicroRIBs와 Swift 도메인은 지운다.

#### 배경

Apple 앱은 MicroRIBs와 Swift 도메인으로 따로 구현돼 있었다([ADR-0014](ADR-0014-apple-multiplatform-microribs.md), [ADR-0015](ADR-0015-tuist-micro-features-modularization.md)). 웹과 Android는 이미 공유 Kotlin RIB(carti-RIBs-KMP)과 공유 도메인으로 옮겼다([modularization-plan](../../../../docs/research/kmp-migration/modularization-plan.md)). 같은 정책을 세 번 구현하고 세 번 검증하는 비용이 컸고, Apple 구현만 정책 변경을 따로 따라가야 했다.

#### 결정

1. Apple 앱은 공유 Kotlin의 Root 트리를 `AppleKeyManageApp`(Apps/Shared/apple-session)으로 띄운다. Swift는 각 RIB의 `ViewContract`를 SwiftUI로 구현하고, 생명주기와 사용자 조작만 알린다.
2. 공유 코드는 정적 XCFramework 두 벌로 받는다. 운영용(apple-session)과 테스트용(apple-example, 피처 구현·시나리오 포함)은 이름이 `KeyManageShared`로 같아 뷰 코드가 어느 쪽에서도 그대로 컴파일된다. 테스트용을 쓰는 타깃(Example, 뷰 테스트)은 빌드 결과 폴더를 따로 쓴다.
3. 피처 뷰는 프레임워크로 만들지 않고 앱·Example·뷰 테스트가 직접 컴파일한다(정적 Kotlin 프레임워크가 여러 Swift 프레임워크에 링크돼 런타임이 중복되는 것을 피한다).
4. 세션 토큰은 다른 클라이언트처럼 메모리에만 둔다. 앱을 다시 켜면 기기 기록으로 잠금 화면이 되고, 서버 동기화는 마스터 비밀번호로 다시 연결한 뒤에 한다([ADR-APL-001](ADR-APL-001-apple-device-storage-and-keychain.md)의 Keychain 토큰 보관을 따르지 않는다).
5. macOS 생체 해제는 서명한 앱의 키체인 권한을 정할 때까지 끈다. iOS는 현재 등록된 생체로만 열리는 키체인 항목에 볼트 키를 둔다(SEC-003).
6. 최소 버전은 iOS 17, macOS 14다(SwiftUI `@Observable`).

#### 고려한 대안

| 대안 | 기각 이유 |
| :--- | :--- |
| Swift 구현을 유지하고 정책만 맞춘다 | 정책 변경마다 Swift 도메인·RIB을 따로 고치고 검증해야 한다. 이미 웹·Android와 동작 차이가 생겼다 |
| 피처마다 Kotlin 프레임워크를 낸다 | Kotlin 런타임이 피처 수만큼 중복되고 프레임워크 사이 타입이 맞지 않는다 |
| 테스트용 묶음에 다른 이름을 준다 | 뷰 코드가 `import`를 조건부로 바꿔야 하고 앱과 Example이 같은 소스를 쓰지 못한다 |
| Swift 쪽에서 Example 조립(가짜 세션 등)을 한다 | Kotlin `suspend`·기본 구현이 있는 포트를 Swift가 다시 구현해야 한다. 조립은 Kotlin(AppleExamples)에 둔다 |

#### 결과

- 얻는 것: 정책·도메인 구현이 하나가 된다. Apple은 뷰 테스트와 앱 UI 테스트만 맡고, 정책 합격 감사도 공유 Kotlin 테스트를 본다.
- 비용: Apple 빌드에 JDK·Gradle·carti-RIBs-KMP가 필요하다. Kotlin 내보내기 이름이 겹치지 않게 공유 interface에 `@ObjCName`을 붙여야 한다. `Duration`처럼 Swift에서 해석할 수 없는 값은 공유 쪽에서 표시용 값을 따로 내야 한다. macOS 생체 해제가 빠진다.

#### 관련

ADR-0014·ADR-0015(대체됨), ADR-APL-001(세션 토큰 보관 부분), SEC-003, VLT-002, VLT-003, [modularization-plan F단계](../../../../docs/research/kmp-migration/modularization-plan.md)
