# 도메인 층 commonMain 이전 계획

- 상태: 조사 문서(실험 브랜치 `research/kmp-migration` 전용, main에 합치지 않는다)
- 날짜: 2026-11-12
- 관련: [target-architecture.md](target-architecture.md), [react-bridge.md](react-bridge.md)

> **요약** 웹 TypeScript 도메인(`Apps/Web/src/domain`, 3,445줄, 테스트 5,021줄)을 기준으로 삼아 `Apps/Shared/domain`의 `commonMain`으로 옮긴다. 웹 도메인은 이미 플랫폼 기능을 포트로 분리해 두었고, Apple 도메인에 없는 기기 볼트 보존(VLT-003)까지 갖췄기 때문이다. 의존 순서대로 아래부터 옮기고, 각 단계는 TypeScript 테스트를 Kotlin 테스트로 먼저 옮긴 뒤 구현한다. 저장 데이터 형식(볼트 JSON, 백업 파일, 기기 기록)은 바꾸지 않는다.

---

## 1. 지금 두 도메인

| 항목 | 웹 (TypeScript) | Apple (Swift) |
| :--- | :--- | :--- |
| 규모 | 40개 파일 3,445줄, 테스트 29개 파일 5,021줄 | 58개 파일 5,161줄 |
| 세션 구조 | `session/` 10개 파일로 나뉨(로그인, 잠금 해제 단계, 대기 전송, 기기 동기화, 비밀번호 변경, 계정 삭제) | `VaultLockManager.swift` 한 파일 1,733줄에 몰림 |
| 플랫폼 분리 | 서버·저장소·탭·타이머·클립보드·백업 파일을 모두 포트로 받는다. 직접 쓰는 것은 `crypto.subtle`·`getRandomValues`·`TextEncoder`·`Date`뿐 | CryptoKit·CommonCrypto·Security·URLSession을 도메인이 직접 쓴다 |
| 오프라인 대기 동기화(VLT-002) | 있음 | 일부. `PendingSyncEngine`은 만들어 두었지만 쓰는 곳이 없다 |
| 기기 볼트 보존(VLT-003) | 있음(IndexedDB) | 포트만 있고 구현 없음 |
| 되돌림 감지·백업·비밀번호 변경·계정 삭제 | 있음 | 있음 |
| 탭 간 통신(WEB-001) | 있음 | 해당 없음 |

결론: **웹 도메인을 옮길 원본으로 삼는다.** 구조가 이미 포트로 나뉘어 있어 Kotlin `commonMain`에 그대로 대응하고, 테스트 5,021줄을 행동 명세로 쓸 수 있다. Apple은 공유 도메인으로 바꾸는 순간 VLT-002·VLT-003을 함께 얻는다.

## 2. 목표 모듈

```text
Apps/Shared/
  crypto/        (있음) 키 유도·볼트 암호
  secure-store/  (있음) 키체인·Keystore·생체 인증
  device-store/  (있음, 웹만) 기기 기록 저장 위치 → Android·Apple 구현 추가
  domain/        (새로) commonMain: auth, vault, backup, device, lock, clipboard, generator, tabs, session
  server-http/   (새로) commonMain: AuthApi·VaultApi의 HTTP 구현
```

- `domain`은 한 모듈에 패키지로 나눈다. 웹 폴더 간 의존이 서로 얽혀 있어(세션이 거의 전부를 쓴다) 모듈을 잘게 나누면 순환만 생긴다.
- `domain`의 `commonMain`에는 플랫폼 타입을 두지 않는다. 시간·난수·저장소·서버·탭·클립보드·백업 파일은 모두 인터페이스로 받는다.

## 3. 포트 대응

| 웹 포트 | Kotlin 포트(`commonMain`) | 웹 구현 | Android 구현 | iOS 구현 |
| :--- | :--- | :--- | :--- | :--- |
| `AuthApi`, `VaultApi` | 같은 이름, `suspend` 함수 | `server-http` | `server-http` | `server-http` |
| `DeviceStore` | `device-store`의 `DeviceStore`(있음) | IndexedDB(있음) | 앱 전용 파일 | 앱 전용 파일 |
| `TabMemory`, `Tabs` | 같은 이름 | sessionStorage, BroadcastChannel·Web Locks | 단일 탭 구현(공통 기본값) | 단일 탭 구현 |
| `Scheduler`, `now` | `Clock`(`kotlin.time`), `Scheduler` | 브라우저 타이머 | 코루틴 기본 구현 | 코루틴 기본 구현 |
| `ClipboardWriter` | 같은 이름 | Clipboard API | ClipboardManager | UIPasteboard |
| `BackupFiles` | 같은 이름 | 다운로드·파일 선택 | SAF | 문서 선택기 |
| `AutoLockSettingStore` | 같은 이름 | localStorage | SharedPreferences | UserDefaults |
| `crypto.getRandomValues` | cryptography-kotlin의 보안 난수 | WebCrypto | 플랫폼 | 플랫폼 |

- 웹 전용 개념(탭 간 통신, 탭 메모리)은 공통 인터페이스로 두고, 모바일은 "탭이 하나뿐"인 공통 기본 구현을 쓴다. 세션 코드에 플랫폼 분기가 생기지 않는다.
- Promise는 `suspend`로, 리스너(`onLock`, `onSyncChange` 등)는 `Flow`로 바꾼다. JS·Swift로 내보내는 일은 도메인이 아니라 그 위 RIB·브리지 층이 맡는다(ADR-0006·ADR-0009 방식).

## 4. 순서

의존이 적은 것부터 옮긴다. 각 단계는 같은 방식으로 진행한다: **TypeScript 테스트를 Kotlin `commonTest`로 옮김(RED) → 구현(GREEN) → JVM·JS에서 통과 → 문서 기록.**

| 단계 | 대상(웹 기준) | 규모(코드 / 테스트) | 확인 기준 |
| :--- | :--- | :--- | :--- |
| 0 | 기반: `server` 오류 타입, base64, 시간·난수 포트, kotlinx.serialization 설정, `domain` 모듈 뼈대 | 약 80 / 0 | 모듈이 JVM·JS·Android·Apple 대상으로 빌드 |
| 1 | `vault`의 순수 부분: `vaultData`, `itemList`, `mergeVaults` | 약 250 / 약 300 | 웹이 만든 볼트 JSON을 그대로 읽고, 다시 쓴 결과가 바이트 단위로 같다 |
| 2 | 작은 독립 모듈: `generator`, `lock`, `clipboard` | 375 / 588 | 옮긴 테스트 통과. 비밀번호 생성 규칙(GEN-001) 동일 |
| 3 | `auth`, `device`(`deviceRecord`, `reconcileDeviceRecord`, `devicePersistence`, `tabMemory`), `tabs` | 591 / 596 | 웹이 저장한 기기 기록을 그대로 읽는다(형식 버전 1) |
| 4 | `vaultSync`, `backup` | 523 / 약 880 | 웹이 만든 백업 파일을 Kotlin이 열고, 반대도 된다 |
| 5 | `session` 10개 파일 | 1,403 / 2,468 | 옮긴 세션 테스트 전부 통과. 상태 전이·되돌림·대기 전송·탭 동기화 동일 |
| 6 | `server-http`: `AuthApi`·`VaultApi` HTTP 구현 | 웹 `adapters/http` 약 300 | 로컬 백엔드에 대한 통합 테스트(가입 → 저장 → 잠금 해제) |

- 1~4단계는 서로 독립인 부분이 있어 단계 안에서는 순서를 바꿔도 된다. 5단계는 1~4가 끝나야 시작한다.
- 각 단계가 끝나도 웹·Apple 앱 코드는 아직 바꾸지 않는다. 공유 도메인이 TypeScript 테스트와 같은 행동을 한다는 것만 확인한다.

## 5. 앱 전환과 기존 코드 삭제

도메인을 다 옮긴 뒤(5·6단계 이후) 위 층부터 바꾼다.

1. 공유 RIB(잠금 해제부터)을 `commonMain`에 만든다. 이 RIB은 공유 세션을 직접 쓴다.
2. 웹: 해당 화면을 React 뷰 + 공유 RIB으로 바꾸고, 바꾼 범위의 `Apps/Web/src/domain`·`ui/session` 코드를 지운다. 기존 화면 테스트(`SessionRoot.test.tsx` 등)가 그대로 통과해야 한다.
3. Android: 같은 RIB에 Compose 뷰를 붙인다(새 앱).
4. iOS: 같은 RIB에 SwiftUI 뷰를 붙이고 `VaultLockManager`와 Swift 도메인을 지운다(Mac에서 진행).

옮기는 동안에는 TypeScript 도메인과 Kotlin 도메인이 함께 있다. 지우는 기준은 "그 코드를 쓰던 화면이 공유 RIB으로 바뀌었고, 그 화면 테스트가 통과한다"이다.

## 6. 바꾸지 않는 것

- 볼트 평문 JSON, 백업 파일, 기기 기록(버전 1)의 형식. 단계마다 웹이 만든 데이터로 교차 확인한다.
- 키 유도·암호 규격(이미 `crypto`에서 확인).
- 서버 API 계약.
- 정책(`docs/policies/`)의 경계값과 문구. 문구 상수는 `domain`으로 옮기되 내용은 그대로 둔다.

## 7. 정해야 할 것

| 항목 | 추천 | 이유 |
| :--- | :--- | :--- |
| HTTP 클라이언트(6단계) | Ktor Client(플랫폼별 엔진: JS fetch, OkHttp, Darwin) | KMP 표준 선택지이고 세 대상을 모두 지원한다. 직접 `expect/actual`로 만들면 재시도·타임아웃·오류 처리를 플랫폼마다 다시 써야 한다. 새 외부 의존성이라 ADR을 쓰고 확인받는다 |
| 직렬화 | kotlinx.serialization | `crypto` 테스트에서 이미 쓴다 |
| 시간 | `kotlin.time.Clock`(표준 라이브러리) | 별도 의존성이 필요 없다 |

## 8. 알려진 부담

1. 세션(5단계)이 전체의 40%다. 웹 세션은 Promise 동시성(직렬화된 기기 쓰기, 탭 잠금)에 기대므로 코루틴 `Mutex`로 옮길 때 순서가 바뀌지 않는지 테스트로 확인해야 한다.
2. TypeScript 테스트는 `vi.fn`·가짜 타이머를 많이 쓴다. Kotlin에서는 손으로 만든 가짜 구현과 `runTest`의 가상 시간으로 바꾼다.
3. Apple 도메인에만 있는 동작이 있는지 아직 대조하지 않았다. 5단계 전에 `VaultLockManager`를 웹 세션 테스트 목록과 대조해 빠진 동작을 찾는다.
4. 오류를 사용자 문구로 바꾸는 코드(`describeError`)는 웹 `ui`에 있다. 플랫폼 공통 문구라면 RIB 단계에서 `commonMain`으로 옮긴다.

## 9. 진행 기록

### 0단계: 기반 (2026-11-12)

`Apps/Shared/domain` 모듈을 만들었다. 대상은 JVM, Android, JS(브라우저·Node), iOS, macOS다.

| 옮긴 것 | 원본 | Kotlin |
| :--- | :--- | :--- |
| 서버 오류 | `server/serverErrors.ts` | `server/ServerErrors.kt`: `ServerErrorCode`(모르는 코드는 `UNKNOWN`), 공통 상위 타입 `ServerFailure` 아래 `ServerError`·`NetworkError`·`UnexpectedResponseError` |
| Base64 | `crypto/base64.ts` | `encoding/Base64.kt`: 표준 라이브러리 `kotlin.io.encoding.Base64`에 원본과 같은 엄격한 형식 검사(공백·패딩 누락·URL용 문자 거부)를 더했다 |
| 난수 | `crypto.getRandomValues` 직접 호출 | `platform/RandomBytes.kt`: 인터페이스와 기본 구현 `SecureRandomBytes`(cryptography-kotlin 보안 난수) |
| 시간 | `new Date()`·주입한 `now` | 표준 라이브러리 `kotlin.time.Clock`을 그대로 쓴다. 별도 포트를 만들지 않았다 |

| 대상 | 테스트 15건(Base64 8, 서버 오류 4, 난수·시간 3) |
| :--- | :--- |
| JVM | 15/15 통과 |
| JS Node | 15/15 통과 |
| JS 브라우저(Chrome Headless) | 15/15 통과 |
| Android | 컴파일 통과(기기 테스트는 1단계부터) |
| iOS·macOS | Windows라 확인 안 함 |

- 원본 TypeScript에는 Base64·서버 오류 전용 테스트가 없어서, 원본 코드의 주석과 동작에서 테스트를 만들었다. 5MB 볼트 크기 입력도 확인한다.
- `kotlin.time.Clock`·`Instant`는 Kotlin 2.3에서 아직 실험 API라 모듈 전체에 `ExperimentalTime` 사용을 켰다.

### 1단계: 볼트 데이터 (2026-11-12)

| 옮긴 것 | 원본 | Kotlin(`domain/vault`) |
| :--- | :--- | :--- |
| 볼트 해석·편집 | `vaultData.ts` | `VaultData.kt`: `parseVaultData`, `encodeVaultData`, `addItem`·`updateItem`·`deleteItem`, `activeItems`, `isReadOnly`, `formatTime` |
| 항목 병합 | `mergeVaults.ts` | `MergeVaults.kt` |
| 목록·입력 검사 | `itemList.ts` | `ItemList.kt`(`listItems`, `checkItemFields`, `itemChanges`), `KoreanNaturalOrder.kt` |

- `VaultData`·`VaultItem`은 JSON 객체를 그대로 감싼다. 모르는 필드와 키 순서가 원본과 같게 남는다. 편집도 원본 객체 펼치기(`{ ...item, ...set }`)와 같은 순서로 키를 둔다.
- 원본의 `null`을 넣어 필드를 지우는 방식(`url: null`)은 `OptionalChange.Keep`·`Remove`·`Set`으로 바꿨다. "그대로 둠"과 "지움"을 타입으로 구분한다.
- 정렬: 원본은 `Intl.Collator('ko', numeric)`를 썼다. 공통 코드에 같은 기능이 없고 플랫폼 정렬기는 서로 결과가 달라서 `KoreanNaturalOrder`를 직접 만들었다(숫자는 값으로, 한글 → 라틴 순, 대소문자 무시). 악센트·구두점 세부 순서는 원본과 다를 수 있다.

| 테스트 | 건수 | JVM | JS Node | JS 브라우저 |
| :--- | :--- | :--- | :--- | :--- |
| 원본 `vaultData.test.ts`·`itemList.test.ts`에서 옮긴 것 | 30 | 통과 | 통과 | 통과 |
| 원본에 없던 것(숫자 정렬, `itemChanges`, UUID, 초 단위 시각) | 6 | 통과 | 통과 | 통과 |
| 웹과 바이트 단위 비교(`VaultCompatTest`) | 6 | 통과 | 통과 | 통과 |
| 0단계 | 15 | 통과 | 통과 | 통과 |

- 바이트 비교: `Apps/Shared/domain/compat/generate-vault-fixtures.ts`가 실제 웹 코드(`vaultData.ts`, `mergeVaults.ts`)로 해석 → 추가 → 수정 → 삭제 → 양방향 병합 → 빈 볼트를 만들고, 그 결과를 Kotlin 테스트 상수로 저장한다. 입력에는 모르는 필드, 다른 플랫폼의 `null`, 오프셋 시각, 따옴표·역슬래시·줄바꿈·제어 문자·U+2028·이모지가 들어 있다. Node 23.6 이상이 TypeScript를 바로 실행하므로 빌드 단계가 없다.
- 이 비교가 실제로 형식 차이를 잡는지 확인하려고 `addItem`의 키 두 개 순서를 바꿔 돌렸다. 6건 중 4건이 실패했고 원래대로 되돌렸다.
- 원본과 다를 수 있는 곳: 웹의 `Date.parse`는 2월 30일 같은 날짜를 다음 달로 넘겨 받지만 Kotlin `Instant.parse`는 거절한다. 다른 플랫폼이 이런 시각을 쓴 볼트는 Kotlin에서 형식 오류가 된다.

### 2단계: 생성기·자동 잠금·클립보드 (2026-11-12)

| 옮긴 것 | 원본 | Kotlin |
| :--- | :--- | :--- |
| 비밀번호 생성 | `generator/passwordGenerator.ts` | `generator/PasswordGenerator.kt`: 거부 샘플링·나머지 편향 제거 그대로. 32비트 난수 포트 `RandomSource`와 기본값 `SecureRandomSource` |
| 강도 점수·연속 숫자 | `generator/passwordStrength.ts` | `generator/PasswordStrength.kt`: 길이는 원본처럼 코드포인트로 센다 |
| 자동 잠금 | `lock/autoLock.ts` | `lock/AutoLock.kt`: 시간은 `Clock`·`Duration`, 타이머는 `Scheduler` 포트 |
| 자동 잠금 설정 | `lock/autoLockSetting.ts` | `lock/AutoLockSetting.kt`: 저장 형식(밀리초 십진 문자열)은 그대로, 저장소 포트 `AutoLockSettingStore` |
| 클립보드 소각 | `clipboard/clipboardBurner.ts` | `clipboard/ClipboardBurner.kt`: `suspend` 함수와 `StateFlow` 상태. 타이머 만료 뒤 비우기는 주입한 `CoroutineScope`에서 띄운다 |

| 테스트 | 건수 | JVM | JS Node | JS 브라우저 |
| :--- | :--- | :--- | :--- | :--- |
| 원본 5개 테스트 파일에서 옮긴 것 | 45 | 통과 | 통과 | 통과 |
| 원본에 없던 것(보안 난수 범위, 길이·문자군 문제 순서, 설정 저장 형식, 아주 큰 숫자 문자열) | 4 | 통과 | 통과 | 통과 |
| 0·1단계 | 57 | 통과 | 통과 | 통과 |
| 합계 | 106 | 통과 | 통과 | 통과 |

- 원본 길이 검사 사례 중 `12.5`·`NaN`은 길이가 `Int` 타입이라 표현할 수 없어 옮기지 않았다. 대신 `0`·`-1`을 넣었다.
- 클립보드의 경쟁 상태 보호(늦게 끝난 비우기가 새 복사를 덮지 않음, 복사 중 잠금)가 테스트로 지켜지는지 확인하려고, 보호 조건을 하나씩 지운 코드로 돌렸다. 각각 해당 테스트 1건이 실패했고 원래대로 되돌렸다.

### 3단계: 키·볼트 페이로드·탭·인증·기기 기록 (2026-11-12)

계획표에 없던 것이 하나 늘었다. Kotlin `crypto` 모듈에는 암호 연산만 있어서, 웹 `crypto/keys.ts`(반복 횟수·salt 검사, 짝 없는 서로게이트 거부)와 `crypto/vaultCipher.ts`(암호화 페이로드 JSON 형식)를 `domain/crypto`로 함께 옮겼다. 인증이 이 둘을 쓴다. 커밋은 3a(키·페이로드·탭)와 3b(인증·기기 기록)로 나눴다.

| 옮긴 것 | 원본 | Kotlin |
| :--- | :--- | :--- |
| 키 유도 | `crypto/keys.ts` | `crypto/Keys.kt`: `deriveKeys`, `assertValidKdf`, `preprocessPassword`. 암호화 키는 바이트를 밖으로 내보이지 않는 `EncryptionKey`로 감싼다 |
| 볼트 페이로드 | `crypto/vaultCipher.ts` | `crypto/VaultPayload.kt`: `sealVault`·`openVault`, 서버·기기에서 온 값을 믿지 않는 `parseEncryptedVaultPayload` |
| 탭 포트 | `tabs/tabs.ts`, `tabs/inMemoryTabs.ts` | `tabs/Tabs.kt`: `TabLock`(코루틴 `Mutex`), `TabChannel`, 테스트용 `InMemoryTabHub`, 모바일·대체용 `SingleTab` |
| 인증 | `auth/authApi.ts`, `auth/authService.ts`, `auth/masterPassword.ts` | `auth/`: 서버 포트 `AuthApi`(`suspend`), `AuthService`, 이메일 정규화, 새 마스터 비밀번호 검사 |
| 기기 기록 | `device/deviceRecord.ts`, `reconcileDeviceRecord.ts`, `devicePersistence.ts`, `tabMemory.ts` | `device/`: 기록 형식과 검사, 탭 간 맞추기 판단, 순서대로 쓰기, 탭 기억 포트 |

| 테스트 | 건수 | JVM | JS Node | JS 브라우저 |
| :--- | :--- | :--- | :--- | :--- |
| 원본 테스트 파일 11개에서 옮긴 것 | 66 | 통과 | 통과 | 통과 |
| 원본에 없던 것(키를 문자열로 찍어도 바이트가 안 나옴, 페이로드 키 순서, 탭 메시지 이름, 기기 기록 키 순서, 기기 저장 순서·실패 3건 등) | 7 | 통과 | 통과 | 통과 |
| 0~2단계 | 106 | 통과 | 통과 | 통과 |
| 합계 | 179 | 통과 | 통과 | 통과 |

- 고정 IV로 만든 페이로드는 테스트 벡터 JSON과 키 순서까지 글자 단위로 같다. 키 유도는 벡터 6건의 인증 해시와 암호화 키를 바이트 단위로 비교한다.
- 기기 기록은 1단계처럼 실제 웹 코드로 만든 결과와 비교하지 못했다. `deviceRecord.ts`가 확장자 없는 import를 써서 Node가 TypeScript를 바로 실행할 수 없기 때문이다. 대신 `session/deviceSync.ts`가 기록을 만드는 순서와 같은 문자열이 나오는지 테스트로 고정했다.
- `devicePersistence.ts`는 원본에 전용 테스트가 없어(세션 테스트가 덮는다) 주석의 동작에서 3건을 만들었다. 세션 테스트를 옮기는 5단계에서 다시 확인된다.
- `device-store` 모듈은 아직 JS 대상만 있어서 `domain`이 의존할 수 없다. 그래서 같은 모양(JSON 문자열)의 `DeviceRecordStore` 포트를 `domain`에 두었다. IndexedDB 구현은 이 포트에 맞춘다.
- 이메일 검사의 공백 문자는 원본 정규식 `\s`와 같은 문자 목록을 직접 적었다. Kotlin `\s`는 플랫폼마다 범위가 다르다.
- 테스트를 옮기다 생긴 문제 두 가지(코드 문제는 아니다):
  - 원본 테스트의 공백이 실제로는 U+2003(em space)이었는데 보이는 대로 일반 공백으로 옮겨 실패했다. 바이트를 확인하고 고쳤다.
  - 짝 없는 서로게이트를 문자열 상수로 쓰면 Kotlin/JS가 UTF-8 출력에서 U+FFFD로 바꿔 JS 대상에서 검사가 무의미해졌다. 실행 중에 `Char`로 이어 붙여 만들도록 고쳤다(별도 커밋). 3a 실행에서는 통과로 나왔는데 다음 실행에서 실패로 드러났다.

### 4단계: 동기화·백업 (2026-11-12)

| 옮긴 것 | 원본 | Kotlin |
| :--- | :--- | :--- |
| 볼트 동기화 | `vault/vaultSync.ts`, `vault/vaultApi.ts` | `vault/VaultSync.kt`: 불러오기·저장·409 충돌 병합(최대 3회 재시도)·되돌림 감지, 서버 포트 `VaultApi`, `RevisionMemory`, `isTransientSyncError`, 5MB 한도 |
| 백업 파일 | `backup/backupFile.ts` | `backup/BackupFile.kt`: 파일 만들기·읽기·비밀번호로 열기, 파일 포트 `BackupFiles` |
| 복원 규칙 | `backup/restoreBackup.ts`의 `restoreItems` | `backup/RestoreItems.kt` |

- 세션 상태를 쓰는 `restoreBackupInto`는 세션과 함께 5단계에서 옮긴다.
- 백업 파일 이름은 기기의 현지 날짜를 쓴다. 공통 코드에는 시간대가 없어서 `LocalCalendar` 포트로 플랫폼이 날짜를 알려 준다. kotlinx-datetime을 쓰면 포트가 필요 없지만 새 외부 의존성이라 넣지 않았다.
- 백업 파일은 원본처럼 두 칸 들여쓰기로 쓴다. 기대값을 Node의 `JSON.stringify(body, null, 2)` 실제 출력에서 가져와 글자 단위로 비교한다.
- 서버 오류 `UnexpectedResponseError`를 `open`으로 바꿨다. 원본처럼 `RevisionMismatchException`이 그 하위 타입이어야 화면이 응답 형식 오류로 함께 다룬다.
- 브라우저 테스트의 테스트당 시간 한도(Mocha 기본 2초)를 `domain/karma.config.d/timeout.js`에서 120초로 늘렸다. 5MB 볼트를 여러 번 암호화하는 왕복 테스트가 Chrome에서만 2초를 넘었다. JVM·Node에서는 처음부터 통과했다.

| 테스트 | 건수 | JVM | JS Node | JS 브라우저 |
| :--- | :--- | :--- | :--- | :--- |
| 원본 `vaultSync.test.ts`에서 옮긴 것 | 17 | 통과 | 통과 | 통과 |
| 원본 `backupFile.test.ts`에서 옮긴 것 | 19 | 통과 | 통과 | 통과 |
| 원본 `backupSession.test.ts` '복원 규칙'에서 옮긴 것 | 9 | 통과 | 통과 | 통과 |
| 원본에 없던 것(일시 실패 판정 2건, 백업 파일 들여쓰기 글자 비교) | 3 | 통과 | 통과 | 통과 |
| 0~3단계 | 179 | 통과 | 통과 | 통과 |
| 합계 | 226 | 통과 | 통과 | 통과 |

- 원본 파일 이름 테스트는 `new Date(2026, 0, 5)`처럼 현지 시각으로 날짜를 정했다. Kotlin에서는 `LocalCalendar`에 날짜를 직접 넣어 같은 두 경우를 확인한다.

### 5단계: 세션 (2026-11-12)

| 옮긴 것 | 원본 | Kotlin(`domain/session`) |
| :--- | :--- | :--- |
| 오류·상태 타입 | `sessionTypes.ts` | `SessionTypes.kt`: 상태는 `SessionState`(`SignedOut`·`Locked`·`Unlocked`), 의존성은 `VaultSessionDeps` |
| 상태 묶음 | `sessionContext.ts` | `SessionContext.kt`: 대기 전송 줄(`pushChain`)은 `Deferred` 사슬과 `inPushChain`으로 옮겼다 |
| 기기 기록 쓰기·탭 맞추기 | `deviceSync.ts`, `recordMerge.ts` | `DeviceSync.kt`, `RecordMerge.kt` |
| 대기 전송 | `pendingPush.ts` | `PendingPush.kt` |
| 로그인·잠금 해제 단계 | `signIn.ts`, `unlockSteps.ts` | `SignIn.kt`, `UnlockSteps.kt` |
| 비밀번호 변경·계정 삭제 | `changePassword.ts`, `deleteAccount.ts` | `ChangePassword.kt`, `DeleteAccount.kt` |
| 세션 진입점 | `vaultSession.ts` | `VaultSession.kt`: 클래스. 리스너 등록은 해제 함수를 돌려준다 |
| 백업 복원 | `backup/restoreBackup.ts`의 `restoreBackupInto` | `backup/RestoreBackup.kt`: 세션 포트 `RestoreTarget` |

- 커밋은 5a(하위 모듈), 5b(세션 진입점·비밀번호 변경·계정 삭제·복원과 테스트)로 나눴다. 하위 모듈의 테스트는 모두 세션 진입점을 거치므로 5a만으로는 테스트를 돌릴 수 없다.
- 순서가 바뀌었다: 이번에는 구현을 먼저 쓰고 테스트를 옮겼다. 대신 테스트가 결함을 잡는지 확인하려고 보호 조건 두 개(다른 탭 기록 병합, 보내는 사이 고친 볼트 확인)를 지운 코드로 돌렸다. 세션 테스트 7건이 실패했고 원래대로 되돌렸다.

| 테스트 | 건수 | JVM | JS Node | JS 브라우저 |
| :--- | :--- | :--- | :--- | :--- |
| 원본 세션 테스트 9개 파일에서 옮긴 것 | 153 | 통과 | 통과 | 통과 |
| 원본 `backupSession.test.ts`의 내보내기·복원·복원 예외 | 16 | 통과 | 통과 | 통과 |
| 0~4단계 | 226 | 통과 | 통과 | 통과 |
| 합계 | 395 | 통과 | 통과 | 통과 |

Android는 컴파일만 확인했다. iOS·macOS는 Windows라 확인하지 않았다.

원본과 다른 점:

- 세션 상태에는 잠금이 없다. 원본(JS 한 스레드)처럼 한 스레드에서만 부른다고 가정한다. 앱은 메인 디스패처에서 부른다.
- 기다리지 않는 작업(쓰지 않게 된 토큰 폐기, 다른 탭 알림에 따른 기기 기록 다시 읽기)을 띄울 곳으로 `VaultSessionDeps.scope`를 새로 받는다. 원본처럼 첫 중단점까지는 바로 실행한다(`CoroutineStart.UNDISPATCHED`).
- `RevisionMemory`를 클래스에서 인터페이스로 바꿨다. 세션이 로그인마다 기준을 바꿀 수 있는 창을 따로 만들어 넘기기 때문이다. 기본 구현은 같은 이름의 함수 `RevisionMemory()`라 기존 코드는 그대로다.
- 비밀번호 변경의 새 salt는 `VaultSessionDeps.random`에서 받는다. 원본은 `crypto.getRandomValues`를 직접 불렀다.
- `exportBackup`은 `LocalCalendar`를 함께 받는다(4단계와 같은 이유).
- 원본 오류 `DeviceStorageError`를 `device/DeviceRecord.kt`의 `DeviceStorageException`으로 옮겼다.
- 테스트 가짜(서버·기기 저장소)의 모든 `suspend` 함수는 먼저 `yield()`한다. 원본 가짜의 `async` 함수가 늘 한 번 기다리던 것과 같은 끼어들기 순서를 낸다. 원본 테스트가 메서드를 바꿔 끼우던 곳은 `*Hook` 변수로 바꿨다.
- JVM의 암호 연산은 중단하지 않고 JS의 암호 연산은 실제 이벤트 루프에서 끝난다. 원본 테스트 가운데 "암호화하는 사이 잠금·저장" 같은 끼어들기에 기대는 것은 대상마다 결과가 달라지지 않도록, 작업이 서버 요청의 문(`Gate`)에 닿거나 상태가 바뀔 때까지 기다린 뒤 끼어들게 바꿨다. 바꾼 테스트에는 주석을 달았다. 그중 둘은 기대 결과도 달라졌다.
  - "저장을 기다리는 동안 잠그면 다시 열리지 않는다"는 원본에서 저장이 취소 오류로 끝났다. Kotlin에서는 서버 요청 중에 잠그므로 저장은 끝나고, 다시 열리지 않는 것만 확인한다.
  - 두 탭 저장 테스트 2건(두 탭의 대기 내용 보존, 쓰기 전 병합)은 다른 탭의 기록이 메모리에 병합되기를 기다린 뒤 다음 저장을 한다. 원본은 Promise 순서상 병합이 먼저 끝나 있었다. JS 대상에서 기다리지 않으면, 다음 저장이 병합 전의 볼트로 기기 기록을 덮어 한쪽 항목이 사라졌다. 이 경쟁은 원본에도 있다(병합이 끝나기 전에 화면이 옛 볼트로 저장하면 같다). 별도로 확인할 일로 남긴다.

### 6단계: 서버 HTTP 구현 (2026-11-12)

[ADR-0026](../../adr/ADR-0026-ktor-client-for-shared-kotlin.md)에 따라 새 모듈 `Apps/Shared/server-http`를 만들었다. Ktor Client 3.3.1이고, 엔진은 JS fetch, Android OkHttp, iOS·macOS Darwin, JVM CIO다.

| 옮긴 것 | 원본(`Apps/Web/src/adapters/http/`) | Kotlin(`server-http`, 패키지 `com.keymanage.serverhttp`) |
| :--- | :--- | :--- |
| 서버 주소 검사 | `baseUrl.ts` | `BaseUrl.kt`: `resolveBaseUrl`, `InvalidBaseUrlException` |
| API 클라이언트 | `apiClient.ts` | `ApiClient.kt`: `ApiClient`, 설정을 묶은 `keyManageHttpClient()` |
| 인증·볼트 어댑터 | `httpAuthApi.ts`, `httpVaultApi.ts`, `responseBody.ts` | `HttpAdapters.kt`: `HttpAuthApi`, `HttpVaultApi` |

- 테스트를 먼저 옮겼다. 시그니처만 있고 본문이 `TODO()`인 코드로 29건 모두 실패하는 것을 확인한 뒤 구현했다.
- 테스트는 Ktor `MockEngine`으로 한다.

| 테스트 | 건수 | JVM | JS Node | JS 브라우저 |
| :--- | :--- | :--- | :--- | :--- |
| 원본 `apiClient.test.ts`에서 옮긴 것 | 16 | 통과 | 통과 | 통과 |
| 원본 `httpAuthApi.test.ts`·`httpVaultApi.test.ts`에서 옮긴 것 | 12 | 통과 | 통과 | 통과 |
| 원본에 없던 것(리다이렉트를 따라가지 않음) | 1 | 통과 | 통과 | 통과 |
| 로컬 백엔드 통합(가입 → 저장 → 잠금 → 잠금 해제) | 1 | 건너뜀 | 대상 아님 | 대상 아님 |
| `domain` 0~5단계 | 395 | 통과 | 통과 | 통과 |

Android는 두 모듈 모두 컴파일만 확인했다. iOS·macOS는 Windows라 확인하지 않았다.

- 세 대상을 함께 돌린 한 번의 실행에서 `domain`의 5MB 볼트 병합 테스트 1건이 JS Node에서 Mocha 기본 한도(2초)를 넘었다. 직전 실행에서는 통과했다. 4단계에서 브라우저에 준 것과 같이 Node 테스트 한도도 120초로 늘렸고(`domain`·`server-http`의 `build.gradle.kts`), 다시 돌려 통과했다.

원본과 다른 점:

- 원본은 `fetch`에 `redirect: 'error'`를 줘 리다이렉트를 연결 실패로 끝냈다. Ktor는 `followRedirects = false`로 3xx 응답을 받으므로, 3xx를 `NetworkError`로 바꿔 원본과 같게 했다. 이를 확인하는 테스트를 더했다.
- 원본의 `credentials: 'omit'`(쿠키를 보내지 않음)과 `AbortSignal` 존재 확인은 Kotlin 테스트로 옮기지 않았다. 대신 쿠키 플러그인을 설치하지 않고, 요청마다 시간 초과 30초가 걸려 있는지(`HttpTimeoutCapability`)를 확인한다. JS 엔진이 fetch에 넘기는 `credentials` 값은 이번에 확인하지 않았다. 웹 화면을 공유 코드로 바꿀 때 교차 출처 요청에서 다시 확인한다.
- kotlinx `Json`은 따옴표 없는 글자(`<html></html>` 등)도 값으로 받는다. 원본 `response.json()`처럼 JSON 문법에 맞지 않는 응답은 형식 오류로 보도록 직접 걸렀다.
- 원본은 볼트 페이로드의 필드를 복호화할 때 검사했다. Kotlin 포트의 `RemoteVault`는 타입이 있는 페이로드를 담으므로, 응답을 받을 때 `parseEncryptedVaultPayload`로 검사한다. 필드가 틀리면 같은 오류(`UnsupportedPayloadException`)가 더 일찍 난다.
- prelogin의 `iterations`는 원본처럼 숫자인지만 보지 않고, `Int` 범위의 정수인지까지 본다. Kotlin 포트가 `Int`로 담기 때문이다. 규격 범위 검사는 그대로 domain이 한다.
- 같은 출처(`""`) 요청은 브라우저에서 페이지 출처로 풀린다(테스트에서 확인함). 모바일 앱은 늘 서버 주소를 넘겨야 한다.

로컬 백엔드 통합 테스트는 통과했다(1건, 가입 → 저장 → 잠금 → 잠금 해제). 테스트는 `server-http/src/jvmTest`에 있고, `KEYMANAGE_IT_BASE_URL`이 있을 때만 돈다.

- 처음에는 이 PC에 JDK 21이 없어 실행하지 못했다. Eclipse Temurin 21(winget)을 설치한 뒤 실행했다.
- 백엔드는 `Back`의 `:app:bootJar`로 만든 jar를 임시 폴더에서 `--server.port=18080 --server.address=127.0.0.1`로 띄웠다. `docker-compose.yml`은 원래 폴더의 데모 스택과 이름·포트·데이터 볼륨이 겹쳐 쓰지 않았다.
- 실행: `KEYMANAGE_IT_BASE_URL=http://localhost:18080 ./gradlew :server-http:jvmTest --tests '*LocalBackendIntegrationTest*'`. 서버 데이터 폴더에 계정·볼트 파일이 생긴 것으로 실제 요청을 확인했다.

## 10. 앱 전환 1조각: 웹 세션 포트 교체 (설계)

5장의 순서를 하나 바꾼다. 공유 Kotlin 세션은 메모리 상태를 TypeScript 세션과 나눌 수 없으므로 잠금 해제 화면만 따로 옮길 수 없다. 그래서 화면(RIB)보다 먼저 웹 `SessionPort` 뒤의 세션을 통째로 Kotlin으로 바꾼다. React 화면은 그대로 둔다. 화면을 RIB으로 옮기는 일은 그다음 조각이다(그때 carti-RIBs-KMP는 composite build로 가져온다).

### 10.1 구조

| 층 | 위치 | 하는 일 |
| :--- | :--- | :--- |
| Kotlin/JS 내보내기 | 새 모듈 `Apps/Shared/web-session` (`js { browser() }`, `binaries.library()`, TypeScript 정의, ES 모듈) | `VaultSession`과 `server-http`를 묶어 `@JsExport` 퍼사드로 내보낸다. suspend 함수는 `Promise`로, 상태는 JS 객체로, 볼트는 `encodeVaultData` JSON 문자열로 넘긴다 |
| 브라우저 포트 | 같은 모듈이 TypeScript 함수 객체를 받는다 | 기기 저장소·탭 잠금·탭 채널·탭 기억은 지금 TypeScript 어댑터(IndexedDB, `navigator.locks`, `BroadcastChannel`, `sessionStorage`)를 그대로 쓴다. Kotlin은 `Promise`를 `await()`로 감싼다. 이 어댑터들은 이미 브라우저 테스트가 있다 |
| TypeScript 어댑터 | `Apps/Web/src/adapters/kotlin/kotlinSession.ts` | `SessionPort`를 구현한다. 상태의 볼트 JSON은 웹 `parseVaultData`로 읽고, Kotlin 오류는 오류 이름(`kind`)으로 웹 오류 클래스(화면이 `instanceof`로 가르는 22종)로 바꾼다 |
| 조립 | `app/services.ts` | `createVaultSession` 대신 `createKotlinSession`을 넘긴다 |

HTTP는 6단계의 `server-http`(Ktor JS 엔진, 브라우저 `fetch`)를 쓴다. 웹 `adapters/http`는 이 조각에서는 지우지 않는다.

### 10.2 확인 기준

- `kotlinSession` 계약 테스트(vitest): 가짜 `fetch` 서버와 메모리 포트로 가입 → 저장 → 잠금 → 잠금 해제, 오류 변환(잘못된 비밀번호, 서버 오류, 되돌림), 리스너 해제를 Kotlin 빌드 결과에 대고 확인한다.
- 웹 전체 테스트(`npm test`)와 타입 검사가 그대로 통과한다. 화면 테스트는 가짜 세션을 쓰므로 바뀌지 않는다.
- 웹이 저장한 기기 기록(IndexedDB)을 Kotlin 세션이 그대로 읽는다(3단계에서 형식 호환 확인함).
- 지우기: 이 조각이 끝나면 `Apps/Web/src/domain/session/`과 이를 쓰던 테스트를 지운다. 다른 웹 domain 코드(`vaultData` 등)는 화면이 아직 쓰므로 남긴다.

### 10.3 진행 기록 (2026-11-13)

웹 `services.ts`가 이제 TypeScript 세션 대신 공유 Kotlin 세션(`Apps/Shared/web-session`)을 쓴다. React 화면과 브라우저 어댑터(IndexedDB·`navigator.locks`·`BroadcastChannel`·`sessionStorage`)는 그대로다.

| 확인 | 결과 |
| :--- | :--- |
| `web-session` Kotlin 테스트(Chrome Headless, MockEngine 서버, 실제 JS fetch 거부) | 37/37 |
| 웹 어댑터 단위 테스트(가짜 Kotlin 모듈) | 94/94 |
| 웹 계약 테스트(실제 Kotlin 빌드 + 가짜 `fetch` 서버): 가입 → 저장 → 잠금 → 잠금 해제, 오류 변환, 기기 기록 복원 | 3/3 |
| 웹 전체(`npm run check`: 타입 검사·oxlint·vitest) | 859/859 |
| `vite build` | 성공 |

- 빌드 순서: 웹을 빌드·테스트하기 전에 `npm run kotlin`(`:web-session:jsBrowserProductionLibraryDistribution`)으로 Kotlin 결과물을 만들어야 한다. Vite 별칭과 `tsconfig.app.json` `paths`가 그 결과물을 가리킨다. `npm run kotlin`은 `scripts/buildKotlin.ts`가 운영체제에 맞는 Gradle 래퍼(Windows는 `gradlew.bat`)를 불러 어디서나 돈다. 웹 CI(`web`·`integration`·`desktop` job)는 `.github/actions/build-web-session`으로 JDK 21 준비, carti-RIBs-KMP checkout, 이 빌드를 먼저 한다.
- 번들 크기: 메인 JS가 358KB(gzip 110KB)에서 1.21MB(gzip 344KB)로 늘었다. Kotlin 표준 라이브러리·코루틴·Ktor·cryptography-kotlin이 들어간 몫이다. 줄이는 일은 아직 하지 않았다.
- Ktor JS는 Node 웹소켓용으로 `ws`를 동적으로 가져온다. 이 앱은 웹소켓을 쓰지 않으므로 Vite 별칭으로 빈 모듈(`adapters/kotlin/wsStub.ts`)을 넣었다.
- Kotlin/JS는 nullable을 `T | null | undefined`로 내보낸다. 어댑터는 `== null`로 둘 다 받는다.
- 지운 것: `Apps/Web/src/domain/session/`의 TS 세션 구현과 테스트(오류 클래스·`SessionState`·`VaultSession` 인터페이스만 `sessionTypes.ts`에 남김), 그것만 쓰던 `domain/backup/restoreBackup.ts`, `domain/device/{devicePersistence,reconcileDeviceRecord}.ts`, `domain/vault/itemList.ts`, 세션 테스트 픽스처. 남긴 것: 웹 `adapters/http`(`Scripts/check-api-contract.sh`가 이 폴더의 엔드포인트를 대조한다)와 `domain/generator`(`check-policy-acceptance.sh`가 TS 형태의 19/40점 샘플을 찾는다). 두 감사가 Kotlin 쪽 테스트·`server-http`를 보게 바뀌면 함께 지운다.
- 독립 코드 리뷰(실제 빌드 결과로 재현)에서 나온 문제를 고쳤다. 모두 수정 전 실패하는 테스트를 먼저 추가했다.
  - CRITICAL: Ktor JS는 연결 실패를 `kotlin.Error`(Exception 아님)로 던져 `server-http`가 `NetworkError`로 바꾸지 못했다. 오프라인 잠금 해제가 실패했다. `catch (Throwable)`로 바꿨다.
  - HIGH: 브라우저 포트가 reject한 JS `Error`도 Exception이 아니어서 도메인의 오류 처리를 건너뛰었다(기기 저장 실패 시 로그아웃이 서버 토큰을 폐기하지 않음 등). 기기 저장소 오류는 `DeviceStorageException`, 탭 잠금 오류는 `TabLockException`으로 감싼다.
  - HIGH: `scope.promise`가 바로 시작하지 않아 원래 TypeScript의 "부르는 즉시 실행되는 앞부분"이 늦었다(잠금 해제 직후 잠금이 무시됨, 잠금 화면 로그아웃 후 화면이 남음). `CoroutineStart.UNDISPATCHED`로 시작한다.
  - 그 밖에: 잠그면 상태 캐시의 평문 볼트를 버린다, 알 수 없는 오류는 `Error`로 감싼다, `Promise<Unit>`은 `undefined`로 끝낸다, 통합 테스트 설정도 같은 별칭(`vite.alias.ts`)을 쓴다.
- 실제 브라우저에서 확인했다(Vite 개발 서버 + 로컬 백엔드 jar, 임시 데이터 폴더): 가입 → 볼트 화면, 잠그기 → 틀린 비밀번호 문구("마스터 비밀번호가 올바르지 않습니다.") → 잠금 해제, 새로고침 → IndexedDB 기록으로 잠긴 화면. 요청 순서는 원래 웹과 같다(가입 직후 볼트 GET 404 `VAULT_NOT_FOUND` → 첫 저장).

## 11. 화면 전환: 웹·iOS를 공유 RIB으로 (설계)

범위: 웹과 iOS 화면만 옮긴다. Android 뷰는 아직 없으므로 보류한다. 공유 RIB(Interactor·Presenter·Router·Builder)은 `commonMain`에 두고, 각 플랫폼은 `XxxPresentable` 구현(웹은 `ViewStore` + React, iOS는 SwiftUI)만 쓴다(carti-RIBs-KMP ADR-0006, ADR-0009).

### 11.1 RIB 트리

지금 iOS 트리를 그대로 쓴다. 웹 화면은 이 트리에 맞춰 나눈다.

| 공유 RIB | 웹 화면(지금) | iOS RIB(지금) | 맡는 일 |
| :--- | :--- | :--- | :--- |
| Root | `SessionRoot` + `App` | Root | 세션 상태(`signedOut`·`locked`·`unlocked`)로 Auth·Unlock·Main 중 하나를 붙인다. 로그인 화면 안내 문구(세션 만료, 다른 기기 비밀번호 변경, 계정 삭제), 세션 리스너 구독 |
| Auth | `AuthScreen` | Auth | 로그인·가입 모드, 가입 비밀번호 확인·강도, 되돌림 경고 수락 |
| Unlock | `UnlockScreen` | Unlock | 잠금 해제, 틀린 비밀번호, 로그아웃(대기 변경 확인). 생체 해제는 iOS만 쓰는 선택 기능으로 둔다 |
| Main | `VaultHomeScreen` | Main | 탭(볼트·생성기·설정), 잠그기·로그아웃, 재연결 안내, 동기화·기기 저장 경고, 오프라인·읽기 전용 배너 |
| VaultList | `VaultScreen` | VaultList | 목록·검색·상세·추가·수정·삭제·복사 |
| PasswordGenerator | `PasswordGenerator` | PasswordGenerator | 옵션·생성·강도·복사 |
| Settings | `SettingsView` + 사이드바 액션 | Settings | 자동 잠금 시간, 백업 내보내기·복원, 비밀번호 변경, 계정 삭제, 정보 표시 |

- 기능은 두 플랫폼을 합친 쪽을 공유 RIB에 둔다. 웹에만 있던 것(재연결, 되돌림 수락, 저장 실패 경고, 세션 만료 안내)은 iOS도 얻고, iOS에만 있던 것(검색, 생체 해제, 연속 숫자 안내)은 웹도 얻을 수 있다. 화면 배치(웹 사이드바 대 iOS 탭, 상세 화면 대 시트)는 플랫폼 뷰가 정한다.
- 앱 수명주기 연결(자동 잠금 타이머, 탭 가림, 클립보드 소각, 네트워크 감지, 대기 재전송)은 RIB이 아니라 플랫폼 진입점이 세션에 직접 붙인다. 웹은 지금 `app/*Wiring.ts`를 그대로 쓴다.

### 11.2 모듈과 의존

| 모듈 | 대상 | 내용 |
| :--- | :--- | :--- |
| `Apps/Shared/feature`(새) | jvm(테스트)·js·iOS·macOS | 공유 RIB 7개. 의존: `:domain`, carti-RIBs-KMP `ribs-core` |
| `Apps/Shared/web-session` | js | 지금 세션 퍼사드 + RIB 뷰 구현(`WebXxxView`, `ViewStore`)과 진입점 `WebKeyManageApp(rootSlot)`. 의존 추가: `:feature`, `ribs-web` |
| 웹 React | — | `ribs-react`의 `useViewStore`·`RibSlot`으로 화면을 그린다. 기존 컴포넌트(폼·목록·모달)는 props만 바꿔 다시 쓴다 |
| iOS(Mac에서) | — | `:feature`가 `ribs-core`를 `export`하는 XCFramework 하나를 만들고 SwiftUI 뷰가 `XxxPresentable`을 구현한다. Swift 도메인과 Swift RIB은 그 뒤 지운다 |

- carti-RIBs-KMP는 `Apps/Shared/settings.gradle.kts`의 `includeBuild`로 가져온다. 기본 경로는 `../../../carti-RIBs-KMP`(저장소 바깥 형제 폴더)이고, 환경 변수 `CARTI_RIBS_DIR`로 바꿀 수 있다(CI는 워크스페이스 안에만 checkout할 수 있어 이 변수를 쓴다). 좌표가 정해져 있지 않으므로 `dependencySubstitution`으로 `:ribs-core`·`:ribs-web` 프로젝트에 묶는다.
- 세션 접근: 공유 RIB은 `VaultSession`(Kotlin)을 직접 쓴다. 웹 진입점은 `web-session`이 이미 만든 세션을 RIB 트리와 TypeScript 앱 배선(`*Wiring.ts`)에 함께 넘긴다.

### 11.3 순서와 확인 기준

1. composite build 연결, `:feature` 뼈대, Root·Auth·Unlock RIB(Interactor 테스트 먼저, JVM에서 가짜 세션으로).
2. Main·VaultList·PasswordGenerator·Settings RIB.
3. 웹 뷰(`WebXxxView`)와 React 연결. 기존 화면 테스트(`SessionRoot.test.tsx` 등)를 새 구조에 맞게 옮기고 행동은 그대로 확인한다. 이 단계가 끝나면 `SessionRoot`와 화면 상태 분기 코드를 지운다.
4. iOS(Mac): XCFramework, SwiftUI 뷰 연결, Swift RIB·도메인 삭제. 이 PC에서는 하지 않는다.

### 11.4 React 연결 (3단계 설계, 2026-11-13)

- **진입점**: `app/kotlinApp.ts`가 브라우저 어댑터(기기 저장소·탭·탭 기억·클립보드·백업 파일·네트워크·자동 잠금 설정)를 JS 포트로 바꿔 `WebKeyManageApp`을 만든다. `main.tsx`가 React 밖에서 `start()`를 부르고(StrictMode 이중 마운트 회피, carti ADR-0009), `App`은 `rootSlot`을 `RibSlot`으로 그린다.
- **앱 배선**: `*Wiring.ts`·`useAutoLockSetting`은 `WebKeyManageApp.session`을 감싼 웹 `VaultSession`을 쓴다. 감싸는 코드는 `createKotlinSession`에서 `wrapKotlinSession`으로 떼어 낸다(같은 세션을 RIB 트리와 배선이 함께 쓴다).
- **화면**: `ui/rib/<Rib>Screen.tsx`가 `useViewStore(view.store)`로 상태를 읽고 입력은 뷰 메서드로 보낸다. 기존 프레젠테이션 컴포넌트(`components/*`, `ItemList`, `ItemForm`, `PasswordField` 등)는 props만 맞춰 다시 쓴다. 세션을 직접 부르던 화면(`SessionRoot`, `VaultScreen`, `*Action.tsx`)은 지운다.
- **ribs-react**: carti-RIBs-KMP `react/ribs-react/src/index.tsx`는 npm 패키지가 아니라 파일 하나이므로 `ui/rib/ribsReact.tsx`에 복사하고 출처를 적는다.
- **테스트**: 화면 테스트는 Kotlin 없이 가짜 store(`subscribe`·`getSnapshot`)와 가짜 뷰 메서드로 돈다. 세션 행동(잠금·만료 안내·대기 확인 등)은 이미 `:feature` Interactor 테스트가 확인하므로, 옛 `SessionRoot.*.test.tsx`는 지우고 화면 테스트는 "상태를 그리는가, 입력을 뷰로 보내는가"만 본다. 끝에 실제 브라우저로 가입·잠금·해제·저장을 확인한다.

### 11.5 carti-RIBs-KMP ADR-0010 반영 (2026-11-13)

- 라이브러리가 RIBs-iOS 구조로 바뀌었다(별도 Presenter 수명주기, Interactor의 Router 소유, `dispatchAttach` 삭제). 공유 RIB 7개를 같은 모양으로 옮긴다.
- 뷰 계약은 `XxxPresentable`(Interactor용) + `XxxViewControllable`(Router용) + 둘을 합친 `XxxViewContract`(플랫폼 뷰가 구현)로 나눈다. `listener` 설정은 Interactor의 `didBecomeActive`·`willResignActive`가 한다. 자식이 있는 RIB은 Router가 `interactor.router`에 자신을 넣는다. 앱은 `LaunchRouter.launch`/`shutdown`을 부른다.
- 동작은 바꾸지 않는다. 테스트는 가짜 뷰의 타입과 수명주기 호출만 바꾸고 어설션은 그대로 둔다. 붙일 때 "활성화 → 로드" 순서, 탭 자식을 계속 붙여 두는 Main의 활성 상태, 비활성 시 `listener = null` 시점은 리뷰에서 따로 확인한다.

### 11.6 RIB 파일 나누기 (2026-11-13)

- 공유 RIB을 한 파일에 두던 것을 RIB 폴더 안 역할별 파일로 나눈다(Apple Features와 같은 단위): `XxxViewState`(State·Action), `XxxViewContract`(Presentable·ViewControllable·ViewContract·ViewFactory), `XxxDependency`(Dependency·Listener), `XxxBuilder`(Builder·Component), `XxxInteractor`, `XxxRouter`, 필요하면 `XxxMessages`. 패키지는 그대로라 다른 코드는 바뀌지 않는다.
- RIB마다 Gradle 모듈(api/impl)로 나누는 일은 iOS XCFramework 묶음 모듈 설계와 함께 Mac에서 정한다(결정하면 ADR).

## 12. iOS 전환: Swift RIB을 공유 RIB으로 (설계, 2026-11-13)

11.3의 4단계를 Mac에서 진행한다. 범위는 iOS 앱(`KeyManageIOS`)이다. macOS 앱(`KeyManageMac`)은 iOS가 끝난 뒤 같은 방법으로 옮긴다.

### 12.1 지금 상태

| 항목 | 지금 | 옮긴 뒤 |
| :--- | :--- | :--- |
| Swift RIB 7개(`Features/*`) | 약 7,100줄, 테스트 약 3,200줄 | SwiftUI 뷰와 `XxxViewContract` 구현만 남는다 |
| Swift 도메인(`Core/Domain`) | 약 5,200줄(`VaultLockManager` 1,733줄 포함), 테스트 약 7,900줄 | 지운다. 행동은 `:domain`·`:feature` Kotlin 테스트가 확인한다 |
| `Core/DesignSystem` | 약 730줄 | 그대로 둔다 |
| 플랫폼 기능 | Swift(`ClipboardManager`, `SystemPasteboard`, 키체인·생체 인증은 `VaultLockManager` 안) | `iosMain` Kotlin. `secure-store`는 `appleMain`이 있고, `device-store`는 `jsMain`만 있어 `appleMain`을 새로 만든다 |
| 서버 주소 | `URLSessionAuthNetworkClient`의 `http://localhost:8080/api/v1` | `server-http`(Ktor Darwin 엔진). 주소는 진입점이 넘긴다 |

### 12.2 모듈

| 모듈 | 대상 | 내용 |
| :--- | :--- | :--- |
| `Apps/Shared/ios-app`(새) | iosArm64·iosSimulatorArm64 | 앱 전용 Kotlin 진입점 `IosKeyManageApp`. `:feature`·`ribs-core`·`:domain`을 `export`하는 정적 XCFramework `KeyManageShared` 하나를 만든다. 앱에는 Kotlin 프레임워크를 하나만 넣을 수 있다(carti-RIBs-KMP README) |
| `Apps/Shared/device-store` | `appleMain` 추가 | 파일 기반 기기 저장소(앱 Application Support) |
| `RIBsKMPSwiftUI`(carti-RIBs-KMP `swift/`) | Swift 패키지 | `SwiftUIRenderable`·`RIBScreenSlot`·`RIBSlotView`. Tuist `packages`에 로컬 경로로 넣는다 |
| `Apps/Apple/Projects/Features/*` | Swift | `Implementation`의 Interactor·Router·Builder를 지우고 `XxxView`(SwiftUI)와 `XxxViewContract` 구현만 남긴다. `Interface`·`Testing` 타깃은 필요 없어지면 지운다 |

- XCFramework는 Tuist가 미리 만든 결과를 참조한다(`.xcframework(path:)`, carti 샘플과 같은 방식). `tuist generate` 전에 `./gradlew :ios-app:assembleKeyManageSharedDebugXCFramework`를 돌리고, `Scripts/verify-apple.sh`에도 이 단계를 넣는다.
- RIB별 Gradle 모듈 분리(11.6 끝)는 하지 않는다. 프레임워크가 하나뿐이라 나눠도 Swift 쪽에서 얻는 격리가 없다. 필요해지면 ADR로 다시 정한다.

### 12.3 순서와 확인 기준

각 단계 끝에서 `xcodebuild` iOS 시뮬레이터 빌드와 해당 단계 테스트가 통과해야 다음으로 간다.

| 단계 | 할 일 | 확인 기준 |
| :--- | :--- | :--- |
| 0 | `ios-app` 모듈, XCFramework 생성, Tuist에 XCFramework·`RIBsKMPSwiftUI` 연결. 아직 화면은 Swift RIB 그대로 | 앱이 빌드되고 Swift에서 `import KeyManageShared`가 된다 |
| 1 | 플랫폼 구현: `device-store` `appleMain`, 클립보드(`UIPasteboard`), 생체 인증(`LAContext`)·키체인은 `secure-store` 사용, 네트워크 감지(`NWPathMonitor`) | 키체인·기기 저장소 테스트를 호스트 앱 안에서 통과(target-architecture 6.1 방식) |
| 2 | 진입점: `IosKeyManageApp`이 세션을 만들고 `LaunchRouter.launch`. `App`은 Root 슬롯을 `RIBSlotView`로 그린다. 앱 수명주기 연결(백그라운드 잠금, 자동 잠금 타이머, 클립보드 소각, 대기 재전송)은 진입점이 세션에 직접 붙인다(11.1 끝) | 로컬 백엔드로 가입 → 잠금 → 해제가 시뮬레이터에서 된다 |
| 3 | 화면을 RIB 단위로 바꾼다: Auth·Unlock → Main → VaultList·PasswordGenerator → Settings. 각 SwiftUI 뷰가 `XxxViewContract`를 구현하고 기존 화면 배치(탭, 시트)는 유지한다 | RIB마다 XCUITest 한 흐름(로그인, 해제, 항목 추가·복사, 생성기, 자동 잠금 변경) 통과 |
| 4 | 삭제: Swift RIB 코드, `Core/Domain`, `Core/Data`, Swift 도메인 테스트, `Scripts/check-swift-architecture.sh`의 지운 계층 규칙 | 남은 Swift는 뷰·진입점·DesignSystem뿐. `verify-apple.sh` 통과 |
| 5 | 실기기 확인: 키체인 접근 제어, Face ID 해제 | target-architecture 4장 기준 통과 |

- Swift 테스트는 단계 3·4에서 지운다. 지우는 기준은 5장과 같다: 같은 행동을 Kotlin 테스트가 확인하고, 그 화면의 XCUITest가 통과한다. 지울 테스트 중 Kotlin에 대응 테스트가 없는 것은 먼저 `:feature`·`:domain` `commonTest`로 옮긴다.
- 생체 해제는 지금 Swift Unlock에만 있다. 공유 Unlock RIB에 선택 기능(포트 `BiometricUnlock`, 웹은 없음)으로 넣고 Interactor 테스트부터 쓴다.

### 12.4 정해야 할 것

| 항목 | 추천 | 이유 |
| :--- | :--- | :--- |
| XCFramework 연결 | 미리 만든 결과 참조 + 검증 스크립트에서 생성 | Xcode 빌드 단계에서 Gradle을 부르면 증분 빌드가 느려지고 Tuist 캐시와 맞지 않는다. carti 샘플이 이미 이 방식으로 확인됐다 |
| Swift 동시성 | Kotlin 콜백은 `@MainActor` 뷰에서만 받는다 | Kotlin/Native 객체는 Sendable이 아니다. carti ADR-0006의 어댑터가 메인 스레드 전달을 전제한다 |
| 기존 저장 데이터 | 옮기지 않는다(실험 브랜치) | iOS 앱은 배포 전이다. 배포 뒤라면 Swift 저장 형식 → Kotlin 기기 기록(버전 1) 변환이 필요하고 그때는 확인받는다 |
| macOS 앱 | iOS 완료 뒤 같은 방법 | `secure-store`가 macOS에서 이미 통과했다. `ios-app`에 macosArm64를 더하면 된다 |

### 12.5 진행 기록

#### 0단계: XCFramework 연결 (2026-11-13)

- `Apps/Shared/ios-app` 모듈을 만들었다. 대상은 iosArm64·iosSimulatorArm64이고, `:feature`·`:domain`·`ribs-core`를 export하는 정적 XCFramework `KeyManageShared`를 만든다(`./gradlew :ios-app:assembleKeyManageSharedDebugXCFramework`).
- 소스가 없으면 링크 태스크가 NO-SOURCE로 건너뛰어 프레임워크가 생기지 않는다. 그래서 `iosMain`에 패키지 선언 파일 하나를 두었다. 2단계에서 진입점이 들어오면 지운다.
- Tuist: `KeyManageIOS` 타깃에 XCFramework와 `RIBsKMPSwiftUI`(로컬 패키지)를 연결했다. `RIBsKMPSwiftUI`가 iOS 17 이상이라 iOS 앱 타깃의 최소 버전을 15.0에서 17.0으로 올렸다. Core·Features 모듈은 15.0 그대로다.
- 확인: `KeyManageIOS` 시뮬레이터 빌드 통과. `import KeyManageShared`·`import RIBsKMPSwiftUI` 후 `LaunchRouting`·`RootInteractor`·`VaultSession` 참조가 타입 검사를 통과했다. 화면은 아직 Swift RIB이다.
- 남은 것: `Scripts/verify-apple.sh`에 XCFramework 생성 단계 추가는 1단계 전에 한다.

#### 1단계: iOS 플랫폼 구현 (2026-11-13)

공유 RIB·세션이 받는 포트 중 플랫폼이 채워야 하는 것을 Kotlin으로 구현했다. Swift 코드는 고치지 않았다.

| 포트 | iOS 구현 | 위치 |
| :--- | :--- | :--- |
| `DeviceStore` → `DeviceRecordStore` | `FileDeviceStore`(Application Support/KeyManage/device-record.json, 원자적 쓰기) + `DeviceRecordStoreAdapter`(실패를 도메인 `DeviceStorageException`으로 바꿈) | `device-store` `appleMain`, `ios-app` `iosMain` |
| `ClipboardWriter`(`ClipboardBurner`가 씀) | `PasteboardClipboardWriter`(`UIPasteboard`. 빈 값이면 항목을 모두 지움, Swift `SystemPasteboard`와 같다) | `ios-app` `iosMain` |
| `Scheduler` | `CoroutineScheduler`(진입점이 넘긴 메인 스레드 범위에서 `delay`) | `ios-app` `iosMain` |
| `AutoLockSettingStore` | `UserDefaultsAutoLockSettingStore`(키 `keymanage.autoLockTimeoutMs`, 밀리초 십진 문자열. 탭이 하나라 `subscribe`는 알리지 않음) | `ios-app` `iosMain` |
| `AppEnvironment` | `IosAppEnvironment` + `NetworkStatus`·`NetworkPathMonitor`(`NWPathMonitor`, 메인 큐) + `TimeZoneCalendar`(`NSCalendar`, 현지 시간대) | `ios-app` `iosMain` |
| `Tabs`·`TabMemory` | 도메인의 `SingleTab`, `TabMemory`는 넘기지 않음(null) | 새 코드 없음 |
| 생체 인증·키체인 | `secure-store` `appleMain`의 `LocalBiometricAuthenticator`(`LAContext`)·`BiometricKeychainStore`·`KeychainSecureStore`를 `ios-app` 의존성으로 넣었다 | 새 공통 포트 없음 |

- 생체 해제 포트(`BiometricUnlock`)는 정하지 않았다. 3단계에서 Unlock RIB Interactor 테스트와 함께 정하고, iOS 구현은 위 `secure-store` 두 클래스를 감싼다. Swift의 14일 비밀번호 재요구와 생체 정보 변경 무효화(SEC-003 8번)는 세션 규칙이라 그때 공통 코드로 옮긴다. 변경 무효화는 키체인 항목의 `kSecAccessControlBiometryCurrentSet`이 이미 맡는다.
- 백업 파일(`BackupFilesPort`)은 문서 선택기(`UIDocumentPickerViewController`)를 띄울 화면이 필요해 3단계 Settings 화면과 함께 만든다.
- 0단계의 `ios-app` `iosMain` 패키지 선언 파일(`Package.kt`)은 소스가 생겨 지웠다.
- `Scripts/verify-apple.sh` 4번 단계에서 `tuist generate` 전에 `./gradlew :ios-app:assembleKeyManageSharedDebugXCFramework`를 돌린다.
- 확인: `:device-store:allTests`(js 6, macosArm64 7, iosSimulatorArm64 7) 통과, `:ios-app:iosSimulatorArm64Test` 14건 통과, XCFramework 생성 통과. 파일·UserDefaults·UIPasteboard(이름 붙인 보드)는 호스트 앱 없이 시뮬레이터 테스트에서 돈다.
- 확인하지 못한 것: `NWPathMonitor` 실제 경로 알림(상태 갱신 로직만 테스트), 일반 `UIPasteboard`, 키체인·Face ID를 앱 안에서 쓰는 것(호스트 앱·실기기, 2단계와 5단계). 12.3 1단계 확인 기준의 "호스트 앱 안에서 키체인·기기 저장소 테스트"는 기기 저장소가 호스트 없이 통과해 키체인만 남았고, 키체인은 target-architecture 6.1의 결과 그대로다.
- 별개 문제: `:kotlinStoreYarnLock`이 이 단계 변경 전에도 실패한다(빌드 중 생기는 yarn.lock에 `typescript`·`ws`가 더해짐). `allTests`는 `-x kotlinStoreYarnLock`으로 돌렸다.

#### 2단계: iOS 진입점 (2026-11-13)

iOS 앱의 `@main`이 공유 RIB 트리를 띄운다. 옛 Swift RIB 경로는 macOS 앱(`KeyManageMac`)이 그대로 쓰고, iOS 쪽 Swift RIB·도메인은 4단계에서 지운다.

| 위치 | 내용 |
| :--- | :--- |
| `ios-app` `IosKeyManageApp` | `IosKeyManageApp.companion.create(serverOrigin:viewFactory:)`가 세션(`server-http` Darwin 엔진, 기기 저장소 파일, `SingleTab`)과 `KeyManageApp`을 조립한다. `start(host:)`는 기기 기록을 읽은 뒤(`restore`) 첫 화면을 붙이고, 복원 중 세션 만료·비밀번호 바뀜이면 로그인 화면 안내를 넘긴다(웹 `bootKotlinApp`과 같다). `stop()`은 트리와 수명주기 배선을 내린다. `didEnterBackground`·`willEnterForeground`·`userActivity`는 실행 중일 때만 배선에 넘긴다 |
| `ios-app` `AppLifecycleWiring` | 웹 `app/*Wiring.ts`와 같은 일. 잠금 해제부터 자동 잠금 타이머(도메인 `createAutoLock`), 백그라운드는 "즉시"일 때만 잠금, 앞으로 오면 경과 시간으로 판정(Swift `VaultLockManager.didEnterBackground`·`willEnterForeground`와 같다), 잠기면 클립보드 바로 소각, 30초마다·네트워크 복구 때 대기 재전송(VLT-002), `NWPathMonitor` 시작·중지. 설정 화면이 자동 잠금 시간을 바꾸면 그때부터 새 시간으로 잰다(SEC-003) |
| `ios-app` `DocumentsBackupFiles` | 백업 내보내기는 앱 문서 폴더에 쓴다. 파일 고르기는 문서 선택기가 필요해 3단계 Settings 화면과 함께 만들고, 지금은 고른 파일이 없다고 답한다 |
| `feature` 뷰 계약 | `render`·`handle`가 RIB마다 같은 이름이라 Objective-C 헤더에서 `render(state__:)`처럼 깨졌다. `@ObjCName`으로 `renderAuth`·`handleAuth` 등 RIB별 이름을 붙였다. Kotlin·웹 코드는 바뀌지 않는다 |
| iOS `Sources/iOS` | `SharedAppLauncher`(`ViewHost`, 창 슬롯, 장면 상태 전달), `SharedViewFactory`, 화면 7개(`Shared*Screen`). Auth·Unlock·Main은 상태를 다 그리고, 탭 화면(볼트 목록·생성기·설정)은 목록·생성·잠그기만 그린다. 화면 입력은 모두 자동 잠금 조작으로 넘긴다. 백그라운드·비활성 장면에서는 가림 화면을 씌운다(SEC-003 3번) |
| Tuist `KeyManageIOSUITests` | 로컬 백엔드로 가입 → 잠금 → 틀린 비밀번호 → 해제를 확인한다. 3단계에서 RIB별 흐름을 더한다 |

- 서버 주소: `server-http`의 `resolveBaseUrl`은 출처만 받으므로 Swift 앱이 쓰던 `http://localhost:8080/api/v1` 대신 출처 `http://localhost:8080`(`LOCAL_DEV_SERVER_ORIGIN`)을 기본으로 둔다. 실행 인자 `-serverOrigin`으로 바꿀 수 있다.
- 사용자 조작: 화면이 `listener`로 보내는 입력(누름·글자 입력)을 자동 잠금 조작으로 넘긴다. 루트 뷰에 탭 제스처를 겹쳐 받는 방법은 화면 버튼과 겹칠 수 있어 쓰지 않았다.
- 입력 칸(`SharedTextInput`): 칸이 글자를 먼저 받고 같은 입력 이벤트 안에서 공유 코드로 보낸다. `onChange`로 늦게 보내면 XCUITest처럼 치자마자 제출할 때 마지막 입력보다 제출이 먼저 처리돼 가입 비밀번호가 짧다는 오류가 났다. 공유 코드를 거쳐 돌아온 값은 칸을 덮지 않고, 공유 코드가 다른 값을 정할 때(지움)만 칸을 고친다.
- 확인: `:ios-app:iosSimulatorArm64Test` 33건 통과(새 `AppLifecycleWiringTest` 11건, `IosKeyManageAppTest` 8건), `:feature:jvmTest`·`:web-session:compileKotlinJs` 통과, `KeyManageIOS` 시뮬레이터 빌드(Swift 6 strict concurrency) 통과, `KeyManageIOSUITests`가 로컬 백엔드로 가입 → 잠금 → 해제 통과, `Scripts/verify-apple.sh` 통과(macOS 앱은 옛 Swift 경로 그대로 빌드).
- 확인하지 못한 것: 실제 백그라운드 전환·자동 잠금 시간 경과·클립보드 소각·네트워크 끊김을 시뮬레이터에서 직접 일으킨 확인(로직은 Kotlin 테스트로만 확인), 키체인·Face ID(5단계), 백업 파일 고르기(3단계).
- 리뷰 반영: `start(host, onFailure)`가 첫 화면을 붙이지 못하면 붙인 트리를 거두고 문구를 넘기며 다시 시작할 수 있게 했다(Swift는 "다시 시도" 버튼을 보인다). `create`의 조립 실패는 `AppSetupException`(`@Throws`)으로 바꿔 Swift가 잡는다. `NSLog`에 Kotlin 문자열을 `%@` 인자로 넘기면 시뮬레이터 테스트에서 프로세스가 죽어 문자열 하나로 넘긴다. `verify-apple.sh` 4번은 JDK 21을 찾지 못하거나 XCFramework 생성이 실패하면 멈춘다. 2단계 설정 화면에는 백업 복원 버튼이 없다.

#### 3단계: 화면 (2026-11-14)

iOS 공유 RIB 화면 7개를 옛 Swift 화면 배치로 채웠다. 옛 Swift RIB·도메인은 macOS 앱이 계속 쓰므로 4단계까지 둔다.

| RIB | iOS 뷰(`Sources/iOS`) | 내용 |
| :--- | :--- | :--- |
| Auth | `SharedAuthScreen` | 카드 배치, 안내·오류 띠, 가입 강도 게이지(`KMGaugeBar`)·취약 안내, 되돌림 수락 |
| Unlock | `SharedUnlockScreen` | 카드 배치, 생체 해제 버튼, "다음부터 생체 인증으로 잠금 해제" 선택, 되돌림 수락, 대기 변경 로그아웃 확인 |
| Main | `SharedMainScreen` | 탭 바(볼트·생성기·설정)와 탭마다 내비게이션 막대(계정, 로그아웃, 잠그기). 오프라인·읽기 전용·기기 저장 실패·동기화 오류 띠, 재연결 입력과 되돌림 수락, 로그아웃 확인 알림 |
| VaultList | `SharedVaultListScreen` | 검색(`searchable`), 항목 카드(아이디·비밀번호 복사, 비밀번호 보기), 상세는 내비게이션으로 밀고 추가·수정은 시트, 폼 안 생성기 슬롯, 강도·취약·이어지는 숫자 안내, 삭제 확인 |
| PasswordGenerator | `SharedPasswordGeneratorScreen` | 결과·강도·옵션 카드, 길이 입력 칸과 슬라이더, 문자 종류 토글, 이어지는 숫자 안내, 복사(탭)·사용·닫기(폼), 보조 기술 알림 |
| Settings | `SharedSettingsScreen` | 계정 정보·동기화 상태, 자동 잠금 메뉴, 지금 잠그기, 백업 내보내기(확인 알림)·복원(문서 선택기 → 비밀번호 시트), 비밀번호 변경·계정 삭제 시트, 안내·오류 띠 |
| 공통 | `SharedCommonViews`, `SharedBackupDocuments` | 상태 띠(`SharedBanner`), 입력 칸 디자인 토큰. 백업 문서 선택기(`UIDocumentPickerViewController` 열기·내보내기) |

- 생체 해제(12.3 끝): `:feature`에 포트 `BiometricUnlock`(`isAvailable`·`isEnrolled`·`passwordUnlockedAt`·`remember`·`recall`·`forget`)와 `UnlockMessages`를 더했다. 포트가 없으면(웹) 버튼과 선택 칸을 숨긴다. Interactor 규칙: 비밀번호로 열 때 선택했거나 이미 켰으면 비밀번호와 기준 시각을 저장한다. 생체 해제는 기준 시각에서 14일이 지나면 막고 비밀번호를 받는다(SEC-003 경계값, Swift `VaultLockManager`와 같이 14일 정각은 허용). 생체 해제로 연 경우 기준 시각을 새로 하지 않는다. 생체 정보가 바뀌었거나(Invalidated) 저장한 비밀번호가 틀리면 끄고 안내한다. 로그아웃하면 끈다. 테스트 `UnlockBiometricInteractorTest` 17건을 먼저 썼다. 기존 테스트는 고치지 않았다(의존성에 기본값 `null`·`Clock.System`).
- 생체 해제 키 처리 변경([ADR-0027](../../adr/ADR-0027-biometric-unlock-stores-derived-key.md), 사용자 결정): 처음 구현은 마스터 비밀번호를 키체인에 뒀으나, 옛 Swift(`VaultLockManager.unlockWithBiometrics(recoveredEncryptionKey:)`)처럼 암호화 키를 두게 바꿨다. 도메인 `VaultSession.unlock(…, exportUnlockKey = true)`가 비밀번호로 연 직후 키 사본을 `UnlockResult.unlockKey`로 내보내고, `unlockWithKey(key)`가 그 키로 연다(열지 못하면 `StaleUnlockKeyException`). 세션 토큰이 없으면 인증 해시가 없어 세션 없이 연다. 기본값이 `false`라 웹은 그대로다. `:feature`는 `UnlockKeySession`(구현 `SessionPortAdapter`)으로 키를 받아 `BiometricUnlock.remember(email, unlockKey, at)`에 넘기고 바로 0으로 지운다. `BiometricRecall.Key`로 꺼낸 키도 쓴 뒤 지운다(되돌림 확인 중에는 그때까지 두고 취소하면 지운다). `BiometricInvalidatingSession`이 비밀번호 변경·계정 삭제·로그아웃(또는 그 작업 중 로그인 전 상태로 바뀜) 뒤 키를 지운다. 14일 재요구 규칙은 그대로다. 테스트: 도메인 `VaultSessionUnlockKeyTest` 10건, `UnlockBiometricInteractorTest` 21건(키 저장·지움·되돌림 포함, 비밀번호 대신 키를 보도록 기대값을 바꿈), `BiometricInvalidatingSessionTest` 6건.
- iOS 구현 `KeychainBiometricUnlock`: 키(32바이트)를 `BiometricKeychainStore`(이 기기 전용, `BiometryCurrentSet`)에 두고, 켰는지와 기준 시각은 UserDefaults에 둔다.
- 생체 해제 켜기: SEC-003은 "가입 직후 켤지 묻는다"이지만 옛 Swift 앱에는 켜는 화면이 없었다. 이번에는 잠금 화면에서 비밀번호로 열 때 고르게 했다. 가입 직후 묻기는 하지 않았다.
- 검색·이어지는 숫자 안내는 이미 공유 RIB에 있어(`VaultListViewAction.Search`, `GeneratedPassword.hasSequentialDigits`) 뷰만 그렸다.
- 백업: `DocumentsBackupFiles`가 Swift `BackupDocumentPresenter`를 받는다. 내보내기는 문서 폴더에 쓴 뒤 내보내기 선택기를 띄우고(파일 앱에서도 보이도록 `UIFileSharingEnabled`), 복원은 문서 선택기로 고른 파일을 임시 폴더에 복사해 읽는다.
- 자동 잠금 선택지: Kotlin `Duration`은 Swift에 내부 표현(Int64)으로 넘어가 글자로 바꿀 수 없어 `ios-app`의 `autoLockChoices`가 글자·선택 여부·보낼 동작을 만든다(웹 `AutoLockSelect`와 같은 글자). 테스트 `AutoLockChoicesTest`.
- 확인: `:domain:jvmTest`·`:feature:jvmTest`·`:ios-app:iosSimulatorArm64Test`·`:ios-app:assembleKeyManageSharedDebugXCFramework` 통과, 웹 `jsBrowserProductionLibraryDistribution` 뒤 `npm run check` 통과(499건), Tuist 생성·`KeyManageIOS`(UI 테스트 빌드)·`KeyManageMac`(`verify-apple.sh`) 빌드 통과, `check-docs.sh`·`verify-apple.sh` 통과.
- XCUITest: 처음 `SharedFeatureFlowUITests` 실패는 제품 결함이 아니라 테스트 결함이었다(사용자가 시뮬레이터에서 복사해도 잠기지 않고 검색·생성기·자동 잠금·백업이 동작함을 직접 확인). 내비게이션 막대 첫 버튼(툴바의 잠금·로그아웃일 수 있음) 누르기, "취소" 같은 글자로 찾기를 없애고 접근성 식별자로 찾게 고쳤다(자동 잠금 선택지 `autoLockChoice-<초>`, 백업 확인 `exportConfirmButton`, 뒤로 가기는 시스템 `BackButton`). 검색은 검색 키로 키보드를 내린다. 탭 막대 항목만 SwiftUI가 식별자를 넘기지 않아 탭 이름으로 찾는다. 디버그 빌드에서 실행 인자 `-resetForUITests`면 `IosKeyManageApp.resetForUITests()`가 기기 기록·자동 잠금 설정을 지우고 시작한다. 두 UI 테스트 모두 통과(시뮬레이터 AF48707C, 로컬 백엔드).
- 확인 필요: 가입 직후 생체 해제 묻기를 넣을지. 실제 Face ID 기기에서 키 저장·해제(시뮬레이터 UI 테스트는 생체 경로를 지나지 않음).

#### macOS 전환 (2026-11-14)

macOS 앱(`KeyManageMac`)도 iOS와 같은 공유 RIB 트리를 띄운다. 옛 Swift RIB·도메인 코드는 iOS 타깃이 아직 소스에 넣고 있어 다음 단계에서 지운다.

| 위치 | 내용 |
| :--- | :--- |
| `ios-app` 모듈 | 대상에 macosArm64를 더해 `KeyManageShared.xcframework`에 `macos-arm64` 조각이 생긴다. 태스크 이름(`assembleKeyManageSharedDebugXCFramework`)이 그대로라 `verify-apple.sh`·CI는 고치지 않았다. 두 플랫폼이 같은 코드(진입점, 수명주기 배선, 네트워크 `NWPathMonitor`, 기기 저장소, 자동 잠금 설정, 생체 해제 `KeychainBiometricUnlock`, 백업 파일)는 `appleMain`, UIKit은 `iosMain`(`PasteboardClipboardWriter`), AppKit은 `macosMain`(`NSPasteboardClipboardWriter`)에 둔다. 플랫폼 차이는 `expect` 두 개(`systemClipboardWriter`, `backupStagingDirectory`)뿐이다 |
| 모듈 이름 | `ios-app`·`IosKeyManageApp`·패키지 `com.keymanage.ios`는 그대로 둔다. `apple-app`으로 바꾸면 Tuist 경로·스크립트·CI·Swift 참조를 함께 고쳐야 해, 옛 Swift 코드를 지우는 단계에서 한 번에 바꾼다 |
| 백업 임시 폴더 | iOS는 앱 문서 폴더(파일 앱에서 보임), macOS는 사용자 문서 폴더에 몰래 남기지 않도록 임시 폴더에 쓴 뒤 저장 패널(`NSSavePanel`)로 고른 곳에 복사하고 임시 파일을 지운다. 복원은 열기 패널(`NSOpenPanel`) |
| Swift `Sources/SharedUI` | iOS `Sources/iOS`에 있던 `SharedAppLauncher`와 화면 7개·공통 뷰·뷰 팩토리를 옮겨 두 타깃이 함께 컴파일한다. 배치 차이는 `#if os(macOS)`: 볼트 틀은 탭 바 대신 `NavigationSplitView` 사이드바(옛 Swift macOS 배치), 도구 막대 위치, 검색 칸 위치. iOS 전용 입력 수식어(키보드 종류·자동 대문자·인라인 제목)는 `SharedCommonViews`의 `shared*` 수식어로 감싸 macOS에서 건너뛴다. 추가·수정·비밀번호 변경 등은 iOS와 같이 시트로 뜬다 |
| 수명주기 | macOS는 장면이 비활성(다른 앱으로 전환)이 되면 iOS 백그라운드와 같이 `didEnterBackground`로 넘긴다(SEC-003: "즉시"면 잠금, 돌아오면 경과 시간 판정). iOS의 비활성은 잠깐이라 넘기지 않는다. 활성이 아니면 가림 화면을 씌운다 |
| Tuist | `KeyManageMac`은 `Sources/macOS`·`Sources/SharedUI`, `KeyManageDesignSystem`, XCFramework, `RIBsKMPSwiftUI`만 쓴다(옛 Swift 모듈·MicroRIBs 의존 제거). 최소 버전 14.0(`RIBsKMPSwiftUI`). Touch ID 문구 `NSFaceIDUsageDescription`. UI 테스트 타깃 `KeyManageMacUITests`(`MacSessionFlowUITests`, iOS `SharedSessionFlowUITests`와 같은 가입 → 잠금 → 틀린 비밀번호 → 해제) |

3단계 리뷰 반영:

- 잠금 해제 화면 내용을 옛 `UnlockView`처럼 세로 가운데에 둔다(스크롤 영역 최소 높이를 화면 높이로).
- `UnlockInteractor.unlockWithBiometrics`: 생체 인증 중 RIB이 내려가(취소) 꺼낸 키를 쓰지 못하면 바로 0으로 지운다. 테스트 `keyRecalledAfterDetachIsWiped`.
- `VaultSession.unlock`·`unlockWithKey`: 되돌림 확인·취소·오류로 열지 못하고 끝나면 세션이 만든 암호화 키 사본을 지운다. 연 뒤에는 동기화가 쓰므로 두고, 부른 쪽 키(되돌림 확인에 다시 씀)는 건드리지 않는다. 테스트 `VaultSessionUnlockKeyWipeTest` 3건(테스트 준비 `setup`에 동기화 생성 함수를 바꿔 끼울 인자를 더했다).
- 세션 만료(401) 등으로 장식(`BiometricInvalidatingSession`)을 거치지 않고 로그인 전 상태가 돼도 생체 해제 키를 지운다. `watchSignOut()`이 세션 알림(원격 변경·잠금·해제·동기화)마다 상태를 보고, 마지막으로 본 계정이 로그인 전 상태로 바뀌면 `forget`한다. `KeyManageApp`이 `start`에서 구독하고 `stop`에서 끊는다. [ADR-0027](../../adr/ADR-0027-biometric-unlock-stores-derived-key.md)의 "로그아웃·계정 삭제·비밀번호 변경 때 지운다"를 세션이 로그인 전 상태가 되는 모든 경우로 넓힌 것이다(ADR 본문은 고치지 않는다). 앱 시작 복원 중에 만료되면 계정 이메일을 알 수 없어 지우지 못하고, 그 키는 다음 같은 계정 로그인 뒤 비밀번호 해제 때 덮어쓰인다. 테스트 `BiometricInvalidatingSessionTest` 4건, `KeyManageAppTest` 1건.
- `IosKeyManageApp.resetForUITests`가 생체 해제 표시(`keymanage.biometric.*`)와 그 계정들의 키체인 항목도 지운다(`KeychainBiometricUnlock.forgetAll`). 테스트 `ApplePlatformTest`.

- 확인: `:domain:jvmTest`·`:feature:jvmTest`·`:ios-app:allTests`(iosSimulatorArm64·macosArm64)·XCFramework 생성 통과, 웹 `jsBrowserProductionLibraryDistribution` 뒤 `npm run check` 통과(499건), Tuist 생성, `KeyManageMac`·`KeyManageIOS` 빌드 통과. iOS `SharedSessionFlowUITests` 통과. `verify-apple.sh`는 1·2단계(문서·Apple 린트)와 3단계의 정책·API 계약 감사를 통과했고, TDD 무결성 검사는 `iosTest` → `appleTest` 파일 이동(git `R100`, 내용 그대로)과 `IosPlatformTest`의 달력 테스트를 `ApplePlatformTest`로 옮긴 것을 어설션 삭제로 보고 멈췄다(검사가 이름 바꾸기를 따라가지 않는다). 4~6단계(XCFramework·Tuist·`KeyManageMac` 빌드·옛 Swift 모듈 단위 테스트 9개)는 같은 명령을 따로 돌려 통과했다.
- 확인하지 못한 것: `KeyManageMacUITests`는 이 Mac에서 UI 자동화 모드를 켜지 못해("Timed out while enabling automation mode", 시스템 보안 설정 승인 필요) 돌지 않았다. macOS 앱 실행(프로세스가 뜨고 죽지 않음)만 확인했다. iOS `SharedFeatureFlowUITests`는 이번 1회 실행에서 첫 이메일 칸 입력 포커스 대기(10초)가 시간 초과로 실패했다. 같은 칸·같은 방식을 쓰는 `SharedSessionFlowUITests`는 같은 실행에서 통과해 다시 돌려 확인해야 한다. macOS 생체 해제: `BiometricKeychainStore`의 접근 제어 항목은 macOS에서 데이터 보호 키체인과 팀 서명·키체인 접근 그룹 권한이 필요할 수 있어, 서명하지 않은 개발 빌드에서는 켜기가 실패할 수 있다(실패하면 버튼이 안 보일 뿐 비밀번호 해제는 된다). Touch ID 실기 확인 필요.

#### 4단계: 옛 Swift 코드 삭제 (2026-11-14)

두 앱이 공유 Kotlin RIB 트리만 쓰게 되어 옛 Swift RIB·도메인을 지웠다. 남은 Swift는 SwiftUI 화면(`Sources/SharedUI`), 진입점·문서 선택기(`Sources/iOS`, `Sources/macOS`), `Core/DesignSystem`뿐이다.

| 지운 것 | 소스 | 테스트 |
| :--- | :--- | :--- |
| `Features/*` 7개(Interface·Implementation·Testing·Tests) | 7,097줄 | 3,171줄 |
| `Core/Domain` | 5,168줄 | 7,895줄 |
| `Core/Data`, `Core/UIAdapter` | 72줄 | 42줄(빈 테스트) |
| `App/Sources/Shared`(`AppDependency`, `SystemPasteboard`) | 97줄 | |
| Tuist `MicroRIBs` 패키지 의존, `Package.resolved`, `Project+Feature.swift`(`swift6Settings`는 `Settings+Swift6.swift`로 옮김), `Workspace.swift`의 `Projects/Features/**` | | |

- 지우기 전 대조: Swift 테스트 파일마다 같은 행동을 확인하는 Kotlin 테스트를 찾았다. Kotlin에 없던 것 중 행동이 Kotlin에 이미 있는 두 가지를 `commonTest`로 옮겼다. 계정 삭제 확인 문구의 다른 말·빈 값·두 칸 공백·줄바꿈(`SettingsInteractorTest`), 날짜와 시각을 공백으로 나눈 시각 거절(`VaultDataTest.rejectsSpaceSeparatedDateAndTime`).
- 옮기지 않은 것(공유 Kotlin에 그 동작이 없어 테스트가 아니라 기능 차이다): 복사 직후 COM-001 안내("클립보드 보안 소각")와 비우기 실패 문구를 Apple 화면이 띄우지 않는다. 로그인 중 볼트 복호화 실패·KDF 값 이상은 "알 수 없는 오류" 문구로 보인다. 계정 삭제 429·401 문구가 Swift(`AccountDeletionStrings`)와 다르다. 비밀번호 강도 3단계(취약·보통·안전, 80점)와 로그인 입력 비었을 때 제출 막기는 Kotlin 상태에 없다. "볼트가 잠겨 있습니다"·"항목을 찾을 수 없습니다" 오류 문구는 Kotlin에서 일반 오류 문구다. 가림 화면 상태는 SwiftUI 뷰에만 있다. 정책과 대조해 고칠지는 따로 정한다.
- 모듈 이름: `ios-app` → `apple-app`, 패키지 `com.keymanage.ios` → `com.keymanage.apple`, `IosKeyManageApp` → `AppleKeyManageApp`, `IosAppEnvironment` → `AppleAppEnvironment`. 태스크는 `:apple-app:assembleKeyManageSharedDebugXCFramework`, XCFramework 경로는 `Apps/Shared/apple-app/build/XCFrameworks/debug`. iOS 전용 파일(`IosPlatform.kt`, `PasteboardClipboardWriter`)은 이름을 두었다. 키체인 서비스 이름 `com.keymanage.ios.biometric-unlock`은 저장 데이터 식별자라 바꾸지 않았다.
- macOS 리뷰 반영:
  - 클립보드: `NSPasteboardClipboardWriter`가 `setString`이 거절하면 `ClipboardWriteException`을 던져 복사 실패 안내가 뜬다(옛 `SystemPasteboard`의 `copyFailed`와 같다). 테스트 `MacPlatformTest.failedSetStringThrowsSoTheCopyFailedNoticeShows`.
  - 창: `WindowGroup` 대신 `Window` 장면 하나. RIB 트리가 하나라 두 창이 같은 뷰를 나눠 그리지 않는다.
  - 백그라운드: macOS는 장면 비활성 대신 `NSApplication.didResignActive`·`didBecomeActive`로 SEC-003 백그라운드를 판정한다. 저장·열기 패널이나 경고창이 키 창을 가져가도 앱은 활성이라 "즉시" 설정에서도 내보내기 중에 잠기지 않는다. 가림 화면도 macOS는 이 상태를 따른다. SEC-003 적용 범위 표에 macOS 줄을 더했다(되돌리기 쉬운 범위 결정이라 추천값으로 정함).
  - 백업 임시 파일: 앱 시작 때 임시 폴더의 `keymanage-backup-YYYYMMDD.json`을 지운다(`removeStagedBackups`, 테스트 `staleStagedBackupsAreRemovedAndOtherFilesAreKept`). iOS는 문서 폴더(파일 앱에 보이는 사용자 파일)라 하지 않는다.
- 스크립트:
  - `check-swift-architecture.sh`: 지운 계층의 규칙 1(Domain의 UI import 금지)을 뺐다.
  - `check-apple-lint.sh`: 옛 규칙 4·5(Swift 잠금 모듈 `memset_s`, `unlockWithBiometrics` 키 인자)를 빼고, 새 규칙 4를 더했다. `Projects` 아래 모듈은 `App`·`Core/DesignSystem`만 허용하고 `MicroRIBs`·옛 Swift 모듈 import를 막는다.
  - `check-policy-acceptance.sh`: Apple 감사가 지운 `Core/Domain/Tests`를 읽어 조용히 건너뛰던 것을, `apple-app`·`feature` unlock Kotlin 테스트의 SEC-003·COM-001·VLT-002 규칙 감사로 바꿨다. 공통 규칙은 기존 Web 감사가 `Apps/Shared` 테스트로 본다. `.githooks/pre-commit`은 `Apps/Shared/*/src/*Test/` 변경에도 이 감사를 돌린다.
  - `verify-apple.sh`: 옛 모듈 단위 테스트 9개와 MicroRIBs 패치를 빼고, 두 앱 빌드(iOS 시뮬레이터는 arm64)와 DesignSystem 테스트를 둔다.
  - `verify-tdd-integrity.sh`: `git diff -M`으로 이름 바꾸기를 따라간다. 내용 그대로 이동(R100)은 수정이 아니고, 지운 어설션이 같은 diff의 테스트 파일에 글자 그대로 다시 나타나면 옮긴 것으로 본다(한 줄씩 짝지음). 테스트 파일 통째 삭제는 그 파일의 모듈(가장 가까운 상위 빌드 정의 파일)도 지웠을 때만 허용한다.
  - CI `apple` 작업: Tuist 의존성 캐시를 빼고, `:domain:jvmTest :feature:jvmTest :apple-app:allTests`를 돌린다.
- 확인: `:domain:jvmTest`(417)·`:feature:jvmTest`(340)·`:apple-app:allTests`(80, iosSimulatorArm64·macosArm64)·XCFramework 생성 통과, 웹 `jsBrowserProductionLibraryDistribution` 뒤 `npm run check` 통과(499건), `tuist install`·`tuist generate`, `KeyManageMac`·`KeyManageIOS` 빌드, iOS `SharedSessionFlowUITests`·`SharedFeatureFlowUITests` 통과(시뮬레이터 AF48707C, 로컬 백엔드), `check-docs.sh` 통과, `verify-apple.sh` 우회 없이 통과.
- 확인하지 못한 것: macOS UI 테스트(시스템 승인 필요), macOS에서 저장 패널을 연 채 "즉시" 설정으로 내보내기, 다른 앱 전환 시 잠금, 두 번째 창이 열리지 않는 것을 직접 조작해 본 확인. ADR-0014·0015(옛 Swift microRIBs 구조)를 대체하는 ADR은 아직 쓰지 않았다.

4단계 최종 리뷰 반영:

- `verify-tdd-integrity.sh`를 다시 썼다. 이동은 `git diff -M`의 이름 바꾸기 짝(옛 경로 → 새 경로) 안에서만 인정하고, 지운 어설션이 그 짝의 새 파일에 글자 그대로 있어야 한다(다른 테스트 파일에 같은 줄을 더해도 이동이 아니다). 테스트 파일 통째 삭제는 모듈 정의 파일이 이름 바꾸기가 아닌 삭제(D)이고 대상 트리에 그 모듈 폴더의 파일이 하나도 남지 않을 때만 허용한다. 워킹 트리 검사는 임시 인덱스(`GIT_INDEX_FILE` 사본)에 추적하지 않은 새 파일까지 올려 짝짓는다. 판정 예시(허용 2, 위반 4)를 스크립트 머리말에 적었고, 스크래치 저장소에서 리뷰가 낸 재현(다른 파일에 같은 줄 더하기, 모듈 폴더 이동 뒤 테스트 삭제, 정의 파일을 하위 폴더로 옮긴 뒤 테스트 삭제)과 정상 경우를 돌려 기대대로 나왔다. 이 저장소에서는 `ios-app` → `apple-app` 테스트 10개가 이름 바꾸기 짝으로, 옛 Swift 테스트 23개가 모듈과 함께 지운 것으로 판정된다. 옮긴 `MacPlatformTest`는 내용을 그대로 두고, 이번에 더한 macOS 테스트는 `MacClipboardAndBackupTest`로 나눴다.
- COM-001 복사 직후 안내: `:feature`의 `ClipboardMessages`·`ClipboardCopyNotice`를 두고, 생성기·볼트 목록 상태에 `copyNotice`(복사 성공 때 표준 제목·본문, 다음 복사 시도나 실패 때 지움)를 더했다. iOS·macOS는 `SharedCopyNoticeBanner`로 그린다. 웹은 기존 앱 전체 안내(`ClipboardNotice.tsx`)를 그대로 쓰고 새 필드는 그리지 않는다. 테스트: 생성기·볼트 목록 Interactor의 `copySuccessShowsTheCom001NoticeAndFailureShowsTheStandardText`, `VaultNoticesTest`(문구 그대로).
- 오류 문구: 로그인 중 서버 볼트가 이 비밀번호의 키로 열리지 않으면 `VaultDecryptException`이 나고 로그아웃 상태로 남는 것을 도메인 테스트(`VaultSessionLoginDecryptTest`)로 재현했다. `describeErrorMessage`가 `VaultDecryptException` → "마스터 비밀번호가 올바르지 않습니다."(SEC-003), `InvalidKdfParamsException` → SEC-002 로그인 실패, `UnsupportedPayloadException` → VLT-001 볼트 형식 오류로 바꾼다(테스트 먼저 실패 확인). 계정 삭제 401·429는 SEC-005에 문구가 없어 SEC-002 문구("로그인이 만료되었습니다…", "로그인 시도가 너무 많습니다…")를 그대로 쓴다. "볼트가 잠겨 있습니다"·"항목을 찾을 수 없습니다"는 정책에 문구가 없어 COM-002 공통 문구를 유지한다.
- 가림 화면(SEC-003 3번)을 Kotlin 상태로 옮겼다. `AppLifecycleWiring`이 백그라운드·iOS 비활성(`didResignActive`)이면 덮고 앞으로 오거나 활성이 되면(잠겨 있어도) 걷으며, `AppleKeyManageApp.isPrivacyCoverActive`·`onPrivacyCoverChanged`로 알린다. Swift는 이 값만 그린다. iOS 비활성은 가리기만 하고 잠그지 않는다. 테스트 `privacyCoverCoversInBackgroundAndWhileInactiveAndLiftsWhenActiveEvenIfLocked`, `forwardsInactiveEventsAndPrivacyCoverOnlyWhileRunning`.
- `check-policy-acceptance.sh` Apple 감사를 함수 이름이 아니라 값·어설션으로 본다: 자동 잠금 5종 목록과 `autoLockChoice-3600`, "즉시" 백그라운드 잠금 어설션, 가림 화면 켬·끔 어설션, 잠금 시 클립보드에 빈 값 쓰기, 30초 재전송, 대기 중 로그아웃 본문, 생체 14일 경계(14일+1시간 재요구, 14일 정각 허용), COM-001 제목·본문·복사 실패 문구 어설션, macOS 쓰기 실패 전파.
- 남은 정책 차이(정해야 함): SEC-003 "잠기면 키가 메모리에서 지워짐"을 Kotlin 세션은 참조를 버리는 것으로만 한다(`lockThisTab`이 `unlocked`를 비움). 키 바이트를 잠금 때 0으로 지우면 잠금과 겹친 저장 요청이 지운 키로 봉인할 수 있어, 진행 중 동기화를 끝낸 뒤 지우는 설계가 필요하다. 그래서 이번 감사에 "잠금 시 키 지움" 규칙은 넣지 않았다.
- 확인: `:domain:jvmTest`(418)·`:feature:jvmTest`(345)·`:apple-app:allTests`(84)·XCFramework·웹 JS 빌드 통과, 웹 `npm run check` 499건 통과, `verify-apple.sh` 우회 없이 통과(문서·린트·정책·API·TDD 무결성·Tuist·두 앱 빌드·DesignSystem 테스트). iOS UI 테스트: `SharedSessionFlowUITests` 통과, `SharedFeatureFlowUITests`는 첫 실행에서 가입 이메일 칸 포커스 대기(10초) 시간 초과로 실패하고 같은 테스트만 다시 돌려 통과했다(macOS 전환 기록의 같은 증상).

#### 완료 상태 (2026-11-14)

끝난 것: 0~4단계. iOS·macOS 앱이 공유 Kotlin RIB 트리를 띄우고, 옛 Swift RIB·도메인을 지웠다. 결정은 [ADR-APL-002](../../../Apps/Apple/docs/adr/ADR-APL-002-apple-apps-host-shared-kotlin-ribs.md)(ADR-0014·0015 대체)에 남겼다. `verify-tdd-integrity.sh`는 테스트 파일을 테스트가 아닌 이름으로 바꾸기와 테스트 비활성화 표시 추가도 위반으로 본다.

남은 것:

| 항목 | 상태 |
| :--- | :--- |
| 실제 기기 Face ID·Touch ID 키 저장·해제 | 확인하지 않음 |
| macOS UI 테스트 | 이 Mac의 UI 자동화 승인 필요 |
| macOS 직접 조작: 저장 패널을 연 채 "즉시" 잠금, 단일 창, 임시 백업 파일 정리 | 확인하지 않음 |
| 잠글 때 키 바이트 0으로 지우기 | 동기화 뒤 지우기 설계 필요, 사용자 결정 대기 |
| `SharedFeatureFlowUITests`의 `authEmailField` 포커스 대기 | 가끔 실패 |
| 정책 감사 LOW: VLT-002 30초 규칙은 호출만 봄, COM-001 본문 규칙에 `assertEquals` 없음, 14일 규칙은 결과 어설션 없음, `check-policy-acceptance.sh` 74번 줄은 이름 기준 | 보강 필요 |
| 다시 시도 중 백그라운드로 갈 때 가림 화면 | LOW, 확인 필요 |
