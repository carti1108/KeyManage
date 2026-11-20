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

## 7. 진행 기록

### 1단계: 공유 모듈의 Android 구현 (2026-11-20)

| 항목 | 결과 |
| :--- | :--- |
| `feature` Android 대상 | 추가. `compileAndroidMain` 성공, JVM 테스트 그대로 통과 |
| 기기 기록 저장소 | `device-store`에 `FileDeviceRecordStore`(도메인 `DeviceRecordStore` 구현) 추가. jvm·android 두 대상이 같은 소스 폴더(`src/fileMain`)를 쓴다. JVM 테스트 9/9 |
| 새 모듈 `Apps/Shared/android-session` | `web-session`의 Android 짝. Android 쪽 포트 구현과 앱 진입점을 둔다. Android 대상만 있다 |
| 자동 잠금 설정 저장소 | `android-session`의 `PreferencesAutoLockSettingStore`. 환경설정 `keymanage.settings`의 `autoLockMs`에 웹과 같은 밀리초 십진 문자열로 둔다. 에뮬레이터(API 37) 기기 테스트 7/7 |
| Android 앱이 공유 모듈을 가져오는 방식 | composite build. `Apps/Android/settings.gradle.kts`가 `includeBuild("../Shared")`로 `android-session`을 가져온다. `:app:compileDebugKotlin` 성공 |

- composite build를 고른 이유: `Apps/Shared`가 carti-RIBs-KMP를 가져오는 방식과 같고, 웹 CI처럼 공유 모듈만 따로 빌드·테스트할 수 있다. Gradle 프로젝트를 하나로 합치면 Android 앱의 `build-logic` 규칙(`FAIL_ON_PROJECT_REPOS`, 버전 카탈로그)을 공유 모듈과 웹 빌드에도 맞춰야 한다. 6단계의 ADR-AND-001 대체 ADR에 함께 적는다.
- Kotlin 버전이 달라도(Android 앱 2.4.21, 공유 모듈·carti-RIBs-KMP 2.3.21) composite build는 빌드된다. 버전 맞추기는 6단계 그대로 둔다.
- Android 저장소에서 빌드하려면 형제 폴더 `carti-RIBs-KMP`가 있어야 한다(없으면 `CARTI_RIBS_DIR`). CI Android 잡에는 아직 반영하지 않았다(5단계).
- 순서 변경: 클립보드 포트는 2단계에서 인터페이스가 바뀌므로("다른 것을 복사했으면 비우지 않음") Android 구현도 2단계에서 만든다. 백업 파일(SAF)은 Activity가 필요해 4단계 진입점과 함께 만든다.

### 2단계 설계: 세션의 생체 해제 (2026-11-20)

규칙은 SEC-003 규칙 4·8과 ADR-AND-002를 그대로 따른다. 새로 정한 것은 공유 세션 안에서의 자리뿐이다.

- 판정 규칙(`BiometricUnlock`, 14일 재요구, 키 무효화 시 끄기)은 `domain`의 `lock` 패키지로 옮겼다. 포트는 `BiometricKeyStore`(감싼 키 보관)와 `BiometricSettingStore`(켜짐 여부, 마지막 비밀번호 해제 시각)다.
- 세션은 `VaultSessionDeps.biometric`(없으면 생체 해제 없음, 웹)을 받는다. 볼트 암호화 키는 세션 밖으로 내보내지 않고, 세션이 직접 감싸고 꺼낸다.

| 세션 기능 | 동작 |
| :--- | :--- |
| `enableBiometricUnlock()` | 열린 볼트의 키 사본을 감싸 두고 켠다. 사본은 바로 0으로 덮는다. 인증 창이 떠 있는 사이 로그아웃·비밀번호 변경이 있었으면 다시 끈다 |
| `disableBiometricUnlock()` | 감싼 키를 지우고 끈다 |
| `canUnlockWithBiometrics()` | 잠긴 상태이고 켜져 있으며 키가 있고 14일 안일 때만 참 |
| `unlockWithBiometrics()` | 꺼낸 키로 기기 볼트를 복호화해 연다. 인증 해시가 없으므로 서버 재로그인은 하지 않는다(ADR-AND-002 결정 1). 세션 토큰이 남아 있으면 비밀번호 해제와 같이 서버와 맞추고(대기 내용 병합, 되돌림 검사), 없으면 세션 없이 연다. 그 뒤 연결은 이미 있는 `reconnect(password)`가 맡는다 |
| 꺼낸 키로 열리지 않음 | 감싼 키를 지우고 끈 뒤 `BiometricError.Invalidated`. 잠긴 상태 그대로다 |
| 마지막 비밀번호 해제 시각 | 가입·로그인·비밀번호 잠금 해제·재연결이 성공하면 남긴다 |
| 감싼 키 지우기(ADR-AND-002 결정 5) | 로그아웃(다른 탭의 로그아웃 포함), 계정 삭제, 비밀번호 변경, 다른 곳에서 비밀번호가 바뀐 것을 알아챘을 때, 다른 계정으로 로그인했을 때 |

- 앱을 다시 켠 뒤 새 비밀번호로 바로 로그인한 경우처럼 세션이 키가 낡았는지 미리 알 수 없는 때는, 다음 생체 해제에서 "꺼낸 키로 열리지 않음"으로 정리된다.

### 2단계: 빠진 동작을 공유 도메인에 추가 (2026-11-20)

| 항목 | 결과 |
| :--- | :--- |
| 생체 해제 판정 규칙 | `domain`의 `lock/BiometricUnlock.kt`. Android 테스트 10건을 `commonTest`로 옮겨 JVM·JS 통과 |
| 세션의 생체 해제 | `VaultSession`에 `enableBiometricUnlock`·`disableBiometricUnlock`·`canUnlockWithBiometrics`·`unlockWithBiometrics` 추가. 세션 테스트 36건(JVM·JS) |
| 감싼 키 지우기 | 로그아웃, 다른 탭의 로그아웃, 계정 삭제, 비밀번호 변경, 다른 곳의 비밀번호 변경 감지, 다른 계정 로그인, 바뀐 비밀번호로 로그인 |
| 클립보드 | `ClipboardBurner.onExternalChange()` 추가. 다른 곳에서 복사한 것을 알 수 있는 플랫폼만 부른다. 웹은 부르지 않아 동작이 그대로다. 테스트 5건 |
| 생체 설정의 Android 저장소 | `android-session`의 `PreferencesBiometricSettingStore`. 에뮬레이터 기기 테스트 5/5 |
| 전체 | `domain` JVM 테스트 454건 통과, `feature` JVM 테스트와 `web-session` 컴파일 그대로 통과 |

- 독립 리뷰(코드 정독)에서 CRITICAL·HIGH는 없었다. 아래를 실패하는 테스트로 먼저 재현한 뒤 고쳤다.
  - 다른 계정 로그인이 한 번 실패한 뒤 성공하면 옛 계정의 감싼 키가 남았다. 키를 지우는 곳을 기기 기록을 정리하는 자리(`prepareDeviceForSignIn`)로 옮겼다. 앱을 다시 켠 뒤 바뀐 비밀번호로 로그인할 때도 여기서 지운다.
  - 비밀번호 변경 뒤 14일 기준을 새로 세지 않았다(Android 원본은 셌다).
  - 인증 창이 떠 있는 사이 비밀번호로 연 볼트를 덮어쓸 수 있었다. 꺼낸 키로 열지 못하고 끝나는 경로에서 키를 0으로 덮지 않았다.
- 아직 만들지 않은 것: Android의 감싼 키 저장소(`BiometricKeyStore`의 Keystore 구현)와 클립보드 쓰기 구현. `FragmentActivity`와 포그라운드 화면이 필요해 4단계 진입점과 함께 만들고 에뮬레이터 지문으로 확인한다.
- 남은 차이: 공유 세션은 잠글 때 메모리의 암호화 키를 0으로 덮지 않고 참조만 버린다(웹 원본과 같다). Android 원본은 덮었다(SEC-003 규칙 1·6). 4단계 전에 정한다.

**정해야 할 것(보안 경계)**: 다른 기기에서 비밀번호를 바꾸면 이 기기는 세션 만료(401)로 로그인 화면이 된다. 앱을 다시 켜면 기기 기록으로 잠긴 화면이 되고, 이때 생체 해제는 서버 확인 없이 옛 볼트를 연다(마지막 비밀번호 해제 뒤 14일까지). Android 원본도 같다. 추천은 "세션이 만료되면 다음 잠금 해제는 비밀번호를 요구한다(감싼 키는 남긴다)"이다. SEC-003에 없는 규칙이라 사용자 확인 뒤 정책과 함께 반영한다.

### 3단계: 공유 RIB 보강 (2026-11-20)

| 항목 | 결과 |
| :--- | :--- |
| 잠글 때 메모리의 키 지우기 | 잠금·로그아웃 때 열린 볼트의 암호화 키를 0으로 덮고, 지운 키는 쓰려 하면 `EncryptionKeyWipedException`을 낸다(0으로 덮은 키로 암호화해 올리는 일을 막는다). 보내는 도중 잠겨 멈춘 대기 내용은 오류 없이 대기로 남아 다음 잠금 해제 때 보낸다. SEC-003 규칙 1·6에 이미 있던 내용이라 정책은 그대로다. 웹과 iOS에도 적용된다 |
| 생체 인증 사용 가능 여부 | `BiometricKeyStore.isAvailable()`, `VaultSession.isBiometricUnlockAvailable` |
| 세션 포트 | `SessionPort`에 생체 해제 기능 6개를 기본 구현(꺼짐)과 함께 추가했다. 웹은 그대로다 |
| Unlock RIB | 세션이 허용하면 생체 해제 버튼을 보인다. 취소는 조용히 끝내고, 14일 지남·키 무효·사용 불가는 문구로 알린 뒤 버튼을 보일지 다시 묻는다. 되돌림 경고를 받아들이면 같은 방법으로 다시 연다. 테스트 13건 |
| Settings RIB | 생체 해제 스위치(`SetBiometricUnlock`). 켤 때 세션이 인증 창을 띄운다. 취소는 조용히, 쓸 수 없으면 이유를 스위치 아래에 보인다. 테스트 10건 |
| 문구 | `core/feature-base`의 `BiometricUnlockMessages`. Android 앱이 쓰던 문구와 같다 |
| 시나리오와 Android 뷰 | Unlock에 4개, Settings에 3개 시나리오 추가. 에뮬레이터 Example에서 14일 지남 안내, 설정 스위치, 쓸 수 없음 안내를 확인했다 |
| 전체 | `domain` JVM 462건, 공유 피처 329건, 웹 `npm run check` 505건, 감사 스크립트 3종 통과 |

- 아직 안 한 것:
  - 가입 직후 "생체 인증으로 잠금을 풀까요?" 안내(SEC-003 값 표). Root는 화면 상태가 없고, 가입이 끝나면 세션 알림으로 바로 Main으로 넘어가므로 안내를 둘 자리를 먼저 정해야 한다.
  - 정책 감사가 찾는 클립보드 문구 2개(`클립보드 보안 소각` 등)와 위 생체 안내 문구. Android 규칙을 공유 모듈 기준으로 바꾸는 4단계에서 함께 넣는다.
  - 잠금 해제·생체 해제가 도중에 끝난 경로에서, 아직 세션에 넣지 않은 키는 0으로 덮지 않는 곳이 남아 있다(비밀번호 잠금 해제의 실패 경로).

### 3단계 추가: 세션 만료 뒤 마스터 비밀번호 요구 (2026-11-20)

사용자 승인을 받아 SEC-003 규칙 8에 추가하고 함께 구현했다.

| 항목 | 결과 |
| :--- | :--- |
| 정책 | SEC-003 규칙 8, 값 표, 사용자 문구, 검증 방법, 이력과 `CHANGELOG.md`. 적용 범위는 플랫폼 이름이 아니라 "생체 해제를 제공하는 클라이언트"다 |
| 도메인 | 세션이 만료되면(`expire`) `BiometricUnlock.requirePassword()`로 표시한다. 감싼 키는 남기고, 마스터 비밀번호로 풀거나 로그인하면 풀린다. 표시된 동안 생체 해제는 `BiometricError.PasswordRequired`다 |
| Unlock RIB | 표시된 동안 생체 버튼을 숨기고 "보안을 위해 마스터 비밀번호를 입력해 주세요."를 보인다 |
| Android | `PreferencesBiometricSettingStore`가 표시를 저장한다(앱을 다시 켜도 남는다). 기기 테스트 7/7. 잠금 화면 뷰가 안내를 그린다 |
| 정책 감사 | `check-policy-acceptance.sh`에 공유 Kotlin 생체 해제 규칙 4개 추가 |
| 전체 | `domain` JVM 470건, Unlock 45건, 웹 `npm run check` 505건, 감사 스크립트 3종 통과. Unlock Example에서 안내를 확인했다 |

- 데스크톱(Tauri)의 생체 해제(Windows Hello, Touch ID)는 아직 없다. SEC-003은 데스크톱 패키징 단계에서 정한다고 적어 두었다. 붙이게 되면 같은 세션 규칙이 그대로 적용된다.
