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
