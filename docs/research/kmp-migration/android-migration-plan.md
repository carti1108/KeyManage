# Android 앱의 공유 RIB 전환 계획

- 상태: 조사 문서(실험 브랜치 `research/kmp-migration` 전용. main 머지 여부는 전환이 끝난 뒤 정한다)
- 날짜: 2026-11-20
- 관련: [target-architecture.md](target-architecture.md), [domain-migration-plan.md](domain-migration-plan.md)

> **요약** main에서 따로 만든 Android 앱(`Apps/Android`, uber/RIBs, 자체 `core:domain`·`core:data`)을 공유 모듈(`Apps/Shared`)과 carti-RIBs-KMP로 옮긴다. Android에는 Compose 뷰, 디자인 시스템, 진입점만 남긴다. 세션 메모리를 두 구현이 나눌 수 없으므로 화면 단위로 나눠 바꾸지 않고, 공유 쪽에 빠진 동작을 먼저 채운 뒤 RIB 트리를 한 번에 바꾼다.

---

## 1. 출발점 (2026-11-20)

이 브랜치를 `origin/main` 위로 리베이스했다. 그래서 한 브랜치 안에 두 구현이 함께 있다.

| 항목 | Android 앱(main에서 옴) | 공유 모듈 |
| :--- | :--- | :--- |
| 화면 로직 | uber/RIBs 0.17, 피처 7개 × 모듈 4개(`interface`·`implementation`·`testing`·`tests`) | `Apps/Shared/feature`의 공유 RIB 7개(carti-RIBs-KMP `ribs-core`) |
| 도메인 | `core:domain`(순수 Kotlin/JVM) | `Apps/Shared/domain`(`commonMain`) |
| 플랫폼 구현 | `core:data`(javax.crypto, Keystore, HTTP, 파일) | `crypto`, `secure-store`, `server-http`, `device-store` |
| 결정 문서 | ADR-AND-001(단독 프로젝트, KMP에 의존하지 않음), ADR-AND-002(생체 해제) | [ADR-0028](../../adr/ADR-0028-ktor-client-for-shared-kotlin.md) |

carti-RIBs-KMP에는 `ribs-compose` 모듈과 `ribs-core`의 Android 대상이 이미 있다.

## 2. 공유 쪽에 빠진 것

Android 앱에는 있고 공유 모듈에는 없는 동작이다. 전환 전에 공유 쪽에 채운다.

| 항목 | Android 구현 | 공유 모듈 상태 |
| :--- | :--- | :--- |
| 생체 해제 규칙(SEC-003 규칙 4·8, 14일마다 비밀번호 재요구, 키 무효화 처리) | `core:domain`의 `lock/BiometricUnlock.kt`, `core:data`의 `lock/KeystoreBiometricKeyStore.kt` | `domain`에 없음. `UnlockViewState.isBiometricsAvailable`은 항상 `false`이고 `UnlockWithBiometrics` 액션은 아무 일도 하지 않는다. `secure-store`의 `BiometricKeystoreStore`는 있지만 쓰는 곳이 없다 |
| 생체 해제 켜기·끄기(설정), 생체 인증 안내 창(Root) | `SettingsInteractor`, `RootInteractor` | 공유 Settings·Root RIB에 없음 |
| 로그아웃·비밀번호 변경·계정 삭제 때 감싼 키 지우기(ADR-AND-002 결정 5) | `MainActivity`, `RootInteractor`, `SettingsInteractor` | 없음 |
| 클립보드: 사용자가 다른 것을 복사했으면 비우지 않음, 민감 표시 | `ClipboardBurner`, `AndroidClipboard` | `ClipboardWriter`는 `write(text)`뿐이고 시간이 되면 무조건 비운다 |
| 기기 기록 저장소 | `FileDeviceRecordStore` | `device-store`는 `js` 대상뿐 |
| 자동 잠금 설정 저장소 | `PreferencesAutoLockStore` | Android 구현 없음 |
| `feature` 모듈의 Android 대상 | — | 없음(jvm·js·Apple만) |
| 정책 감사가 찾는 문구 | `클립보드 보안 소각`, `보안을 위해 복사된 내용이 30초 후 클립보드에서 자동 삭제됩니다.`, `생체 인증으로 잠금을 풀까요?` | 없음 |

반대로 공유 쪽에만 있는 동작(세션 없는 재연결, 기기 저장 실패 경고, 클립보드 비우기 재시도, 한국어 자연 정렬)은 전환하면 Android가 얻는다.

되돌림 수락·백업·비밀번호 변경·계정 삭제·오프라인 대기 전송은 양쪽에 다 있다. 동작이 같은지는 4단계에서 Android 피처 테스트를 공유 Interactor 테스트와 대조해 확인한다.

## 3. 기기에 저장된 데이터

지금 Android 앱이 쓴 데이터를 공유 구현은 읽지 못한다.

| 데이터 | Android 앱 | 공유 구현 | 읽을 수 있는가 |
| :--- | :--- | :--- | :--- |
| 기기 기록 | `device-record.json`. 평평한 키(`kdfSalt`, `baseRevision`, `hasPending` 등), `version` 없음 | `version = 1`, `kdf{salt, iterations}`, `revision`, `pendingSync` | 아니오. `parseDeviceRecord`가 `version`이 없으면 `null`을 돌려준다 |
| 생체용 감싼 키 | Keystore 별칭 `keymanage.biometric.vaultKey`, `noBackupFilesDir/biometric-key.json` | 별칭 `keymanage.biometric.<service>`, SharedPreferences | 아니오. Keystore 키는 꺼낼 수 없어 옮길 수도 없다 |
| 설정 | `settings` 환경설정의 `autoLockMillis`(Long), `biometricEnabled`, `lastPasswordUnlockMillis` | 자동 잠금은 밀리초 십진 문자열. Android 저장소 구현 없음 | 아니오 |

**결정(2026-11-20, 사용자)**: 옛 형식을 읽어 옮기는 코드는 두지 않는다. 전환 뒤 Android 앱은 공유 형식(웹과 같은 기기 기록 버전 1)만 읽고 쓴다. 옛 앱이 남긴 `device-record.json`, `biometric-key.json`, `settings` 환경설정은 읽지 않으며, 전환한 앱에서는 다시 로그인하고 생체 해제를 다시 켠다.

## 4. 순서

| 단계 | 내용 | 확인 기준 |
| :--- | :--- | :--- |
| 1 | 공유 모듈의 Android 대상: `feature`에 Android 대상 추가, `device-store`에 `commonMain` 인터페이스의 Android 파일 구현, 자동 잠금 설정·클립보드·백업 파일(SAF) 포트의 `androidMain` 구현. Kotlin·코루틴 버전을 Android 앱과 맞춤(2.4.21, 1.11.0) | 모든 모듈이 Android 대상으로 빌드. 기기 기록 저장·조회 기기 테스트 통과 |
| 2 | 생체 해제를 공유 `domain`으로: `BiometricUnlock` 규칙과 테스트를 `commonMain`으로 옮기고 `secure-store`의 `BiometricSecureStore`에 연결. 키 지우기 시점(로그아웃·비밀번호 변경·계정 삭제)을 공유 세션에 넣음 | Android 도메인의 생체 해제 테스트를 옮긴 것이 JVM에서 통과. 에뮬레이터에서 지문 일치·불일치·등록 변경 |
| 3 | 공유 RIB 보강: Unlock의 생체 해제, Settings의 생체 토글, Root의 생체 안내, 클립보드 "다른 복사 시 비우지 않음", 빠진 표준 문구 3개 | `feature` Interactor 테스트(테스트 먼저). 웹 화면 테스트와 `npm run check` 그대로 통과 |
| 4 | Android 뷰 연결: Compose 뷰가 공유 `XxxViewContract`를 구현(`ribs-compose`), `:app`이 공유 Root를 `LaunchRouter.launch`로 띄움. `FragmentActivity`만 `secure-store`에 넘김. 피처별 화면 상태 차이(아래 표)를 뷰에서 맞춤 | Android 빌드·lint. 옮긴 뷰 테스트. `Apps/Android/docs/device-checklist.md` 전 항목을 에뮬레이터에서 다시 확인 |
| 5 | 삭제와 감사: `core:domain`, `core:data`, `core:rib`, 피처의 Interactor·Router·Builder와 `interface`·`testing` 모듈, uber/RIBs 의존성 삭제. `check-policy-acceptance.sh`·`check-api-contract.sh`의 Android 규칙이 공유 모듈 테스트를 보게 변경. CI Android 잡에 공유 모듈 빌드와 carti-RIBs-KMP checkout 추가 | 감사 스크립트 3종 통과. CI 통과 |
| 6 | 문서: ADR-AND-001을 대체하는 ADR, ADR-AND-002의 구현 위치 변경 기록, `platform-bindings.md` 갱신 | `check-docs.sh` 통과 |

- 1~3단계 동안 Android 앱은 지금 구현 그대로 동작한다. 4단계에서 한 번에 바꾼다.
- 3단계는 웹에도 영향을 준다(공유 RIB 상태에 생체 필드가 늘어난다). 웹은 생체 인증이 없으므로 `isBiometricsAvailable = false`로 지금과 같게 둔다.

### 피처별 화면 상태 차이

4단계에서 Compose 뷰가 맞춰야 할 곳이다.

| 피처 | 차이 |
| :--- | :--- |
| Auth | Android `isSubmitting`·`errorMessage: String` 대 공유 `isLoading`·`error: ErrorMessage`. 액션 이름(`EmailChanged` 대 `UpdateEmail`) |
| Unlock | Android `Logout` 한 번 대 공유 `RequestLogout`·`ConfirmLogout`(대기 변경 확인) |
| VaultList | Android 상태의 `burnSeconds`·`isOffline`·`isPendingSync`·`canAcceptRollback`이 공유에서는 Main 상태에 있다 |
| Generator | 이름이 `PasswordGenerator`다. 공유는 `lengthText`와 문자 종류별 Boolean을 쓴다 |
| Settings | 공유는 평평한 상태와 `modal` 하나를 쓴다. 생체 토글은 3단계에서 추가 |
| Main | Android는 탭만, 공유는 동기화·재연결·오프라인 상태까지 가진다 |
| Root | 공유 Root에는 화면 상태가 없다. 생체 안내 창과 되돌림 안내는 3단계에서 자리를 정한다 |

## 5. 그 뒤

- iOS: Mac에서 진행한다. XCFramework, SwiftUI 뷰 연결, MicroRIBs와 Swift 도메인 삭제([domain-migration-plan.md](domain-migration-plan.md) 11.3의 4번). 2·3단계에서 넣은 생체 해제 규칙을 iOS도 그대로 쓴다.
- carti-RIBs-KMP 고정: 지금은 형제 폴더를 `includeBuild`로 가져온다. main에 올릴지 정하기 전에 커밋 고정이나 배포 좌표로 바꾼다.
- 공통 ADR: 클라이언트 간 Kotlin 코드 공유는 ADR-0004가 대안에서 기각했던 방식이다. main에 올리기로 하면 이를 대체하는 공통 ADR을 쓴다.

## 6. 알려진 부담

1. 생체 해제는 보안 경계다. 규칙은 SEC-003과 ADR-AND-002를 그대로 옮기고 새로 정하지 않는다. 옮긴 뒤 독립 리뷰를 받는다.
2. `feature`에 Android 대상을 넣으면 `ribs-core`의 Android 변형과 AGP 설정이 `Apps/Shared`와 `Apps/Android` 두 Gradle 프로젝트에 걸친다. Android 앱이 공유 모듈을 가져오는 방식(composite build 또는 한 프로젝트로 합치기)은 1단계에서 정하고 ADR로 남긴다.
3. Android 피처 테스트(`tests` 모듈)는 uber/RIBs Interactor를 대상으로 한다. 행동 어설션은 공유 `feature` 테스트로 옮기고, Android에는 "상태를 그리는가, 입력을 보내는가"만 남긴다(웹과 같은 방식, domain-migration-plan 11.4).
