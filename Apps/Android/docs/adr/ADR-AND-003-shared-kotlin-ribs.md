### ADR-AND-003 Android 클라이언트는 Compose 뷰와 진입점만 두고, 나머지는 carti-RIBs-KMP 기반 공유 Kotlin 모듈을 쓴다
- 상태: 채택
- 날짜: 2026-11-21
- 영역: Android

> **요약** Android 앱은 Compose 뷰, Activity, 플랫폼 구현 조립만 갖는다. 도메인·RIB(Builder·Interactor·Router)·ViewState/ViewAction·테스트는 `Apps/Shared`의 Kotlin Multiplatform 모듈에 두고 composite build로 가져온다. RIB 엔진은 uber/RIBs에서 carti-RIBs-KMP로 바꾼다. ADR-AND-001을 대체한다.

#### 배경

[ADR-AND-001](ADR-AND-001-android-architecture.md)은 Android를 단독 Gradle 프로젝트로 두고 uber/RIBs와 `:core:domain`·`:core:data`를 Android 안에 두었다. 그때는 공유 모듈이 실험 브랜치에만 있었다.

그 뒤 웹이 공유 Kotlin 도메인과 RIB을 쓰게 됐고, 같은 정책(SEC-003, COM-001, VLT-001~004)을 Android가 Kotlin으로 한 번 더 구현하고 있었다. 정책이 바뀔 때마다 두 구현과 두 벌의 테스트를 맞춰야 했다. 목표는 뷰를 뺀 전부를 플랫폼이 함께 쓰는 것이다.

#### 결정

1. **공유 코드 사용**: `Apps/Android`는 `includeBuild("../Shared")`로 공유 모듈을 가져온다. 도메인, 피처 RIB, 문구, 테스트 더블은 `Apps/Shared`에만 둔다.
2. **RIB 엔진**: carti-RIBs-KMP(`ribs-core`, `ribs-compose`)를 쓴다. uber/RIBs 의존성은 없앤다.
3. **Android에 남는 모듈**: `:app`(Activity, 프로세스 단위 조립), `:core:designsystem`, `:core:example`, 피처마다 `:feature:<이름>:ui`(공유 ViewContract를 구현하는 Compose 뷰)와 `:feature:<이름>:example`(공유 Testing 시나리오로 그 피처 화면만 띄우는 앱).
4. **플랫폼 구현 위치**: Keystore, BiometricPrompt, 클립보드, 저장소 접근 프레임워크, 설정 저장은 공유 쪽 `android-session` 모듈의 Android 소스에 둔다. 앱은 이를 조립해 Activity 수명주기만 넘긴다.
5. **View 규칙 유지**: ADR-AND-001의 ViewState/ViewAction 규칙은 그대로다. 타입만 공유 모듈로 옮긴다.
6. **기기 데이터**: 옛 앱이 기기에 저장한 데이터는 옮기지 않는다. 출시 전이라 사용자가 없다.

#### 고려한 대안

| 대안 | 기각 이유 |
| :--- | :--- |
| 도메인만 공유하고 Android RIB은 uber/RIBs로 유지 | Interactor의 상태 전이와 그 테스트가 플랫폼마다 남는다. 화면 흐름 정책(잠금 시 트리 해제, 가입 직후 안내)을 두 번 구현한다. |
| 공유 모듈을 Maven 저장소에 올려 버전으로 받기 | 공유 코드와 뷰를 한 변경에서 함께 고치는 일이 많은데, 그때마다 배포·버전 올림이 끼어든다. 저장소가 하나라 composite build로 충분하다. |
| Compose Multiplatform으로 뷰까지 공유 | 플랫폼 디자인 지침(Android는 Material 3, Apple은 HIG)을 따르는 화면을 플랫폼마다 만든다는 방침과 맞지 않는다. |
| ADR-AND-001 유지(단독 구현) | 같은 정책의 Kotlin 구현이 둘이라 계속 어긋날 수 있다. |

#### 결과

- 얻는 것:
  - 정책 구현과 테스트가 한 벌이다. 정책 감사도 공유 테스트 하나를 본다.
  - Android 코드는 뷰와 조립뿐이라 피처 Example 앱으로 화면만 빠르게 확인할 수 있다.
- 비용:
  - Android 빌드가 `Apps/Shared`와 carti-RIBs-KMP 빌드에 묶인다. CI가 carti-RIBs-KMP 저장소를 따로 받아야 한다.
  - carti-RIBs-KMP는 지금 옆 폴더 소스를 그대로 쓴다. 버전 고정은 아직 하지 않았다.
  - 계층 침범을 Android 모듈 경계가 아니라 공유 모듈 경계가 막는다.
  - 공유 코드가 JS에서도 돌아야 하므로 JVM 전용 API를 공통 코드에 쓸 수 없다.

#### 관련

ADR-AND-001(대체), ADR-AND-002, ADR-0004, ADR-0010, ADR-0028, SEC-003, COM-001
