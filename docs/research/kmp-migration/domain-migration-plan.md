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

- 빌드 순서: 웹을 빌드·테스트하기 전에 `npm run kotlin`(`:web-session:jsBrowserProductionLibraryDistribution`)으로 Kotlin 결과물을 만들어야 한다. Vite 별칭과 `tsconfig.app.json` `paths`가 그 결과물을 가리킨다. 웹 CI는 아직 이 순서를 모르므로 이 브랜치에서는 깨진다.
- 번들 크기: 메인 JS가 358KB(gzip 110KB)에서 1.21MB(gzip 344KB)로 늘었다. Kotlin 표준 라이브러리·코루틴·Ktor·cryptography-kotlin이 들어간 몫이다. 줄이는 일은 아직 하지 않았다.
- Ktor JS는 Node 웹소켓용으로 `ws`를 동적으로 가져온다. 이 앱은 웹소켓을 쓰지 않으므로 Vite 별칭으로 빈 모듈(`adapters/kotlin/wsStub.ts`)을 넣었다.
- Kotlin/JS는 nullable을 `T | null | undefined`로 내보낸다. 어댑터는 `== null`로 둘 다 받는다.
- 아직 지우지 않음: `Apps/Web/src/domain/session/`. 어댑터가 웹 오류 클래스와 `VaultSession` 타입을 여기서 가져오고, 화면 테스트 픽스처가 TS 세션을 쓴다. 오류 클래스·타입을 옮긴 뒤 지우는 일은 다음 작업이다. 웹 `adapters/http`도 남아 있다.
- 독립 코드 리뷰(실제 빌드 결과로 재현)에서 나온 문제를 고쳤다. 모두 수정 전 실패하는 테스트를 먼저 추가했다.
  - CRITICAL: Ktor JS는 연결 실패를 `kotlin.Error`(Exception 아님)로 던져 `server-http`가 `NetworkError`로 바꾸지 못했다. 오프라인 잠금 해제가 실패했다. `catch (Throwable)`로 바꿨다.
  - HIGH: 브라우저 포트가 reject한 JS `Error`도 Exception이 아니어서 도메인의 오류 처리를 건너뛰었다(기기 저장 실패 시 로그아웃이 서버 토큰을 폐기하지 않음 등). 기기 저장소 오류는 `DeviceStorageException`, 탭 잠금 오류는 `TabLockException`으로 감싼다.
  - HIGH: `scope.promise`가 바로 시작하지 않아 원래 TypeScript의 "부르는 즉시 실행되는 앞부분"이 늦었다(잠금 해제 직후 잠금이 무시됨, 잠금 화면 로그아웃 후 화면이 남음). `CoroutineStart.UNDISPATCHED`로 시작한다.
  - 그 밖에: 잠그면 상태 캐시의 평문 볼트를 버린다, 알 수 없는 오류는 `Error`로 감싼다, `Promise<Unit>`은 `undefined`로 끝낸다, 통합 테스트 설정도 같은 별칭(`vite.alias.ts`)을 쓴다.
- 실제 브라우저에서 확인했다(Vite 개발 서버 + 로컬 백엔드 jar, 임시 데이터 폴더): 가입 → 볼트 화면, 잠그기 → 틀린 비밀번호 문구("마스터 비밀번호가 올바르지 않습니다.") → 잠금 해제, 새로고침 → IndexedDB 기록으로 잠긴 화면. 요청 순서는 원래 웹과 같다(가입 직후 볼트 GET 404 `VAULT_NOT_FOUND` → 첫 저장).
